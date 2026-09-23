package com.flashticket.inventoryservice.service;

import com.flashticket.inventoryservice.client.TicketClient;
import com.flashticket.inventoryservice.config.RedisConfig;
import com.flashticket.inventoryservice.dto.InventoryResponse;
import com.flashticket.inventoryservice.dto.InsertInventoryRequest;
import com.flashticket.inventoryservice.dto.ReleaseStockRequest;
import com.flashticket.inventoryservice.dto.ReserveStockRequest;
import com.flashticket.inventoryservice.dto.TicketScheduleResponse;
import com.flashticket.inventoryservice.entity.Inventory;
import com.flashticket.inventoryservice.event.InventoryReleaseEvent;
import com.flashticket.inventoryservice.event.InventoryReservedEvent;
import com.flashticket.inventoryservice.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor

public class InventoryService {

    // Complete inventory-detail cache entries expire after 10 minutes.
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    // Missing-inventory markers expire quickly so newly created records can become visible.
    private static final Duration NOT_FOUND_CACHE_TTL = Duration.ofSeconds(30);

    // Blocks manual stock changes starting 10 minutes before ticket sales begin.
    private static final Duration STOCK_CHANGE_LOCK_WINDOW = Duration.ofMinutes(10);

    private final InventoryMapper inventoryMapper;
    private final RedisTemplate<String,InventoryResponse> redisTemplate;

    // Manages flash-sale inventory keys and uses Lua to check and deduct stock atomically in Redis.
    private final StringRedisTemplate stringRedisTemplate;

    private final TicketClient ticketClient;
    private final RedisConfig redisConfig;

    private final KafkaTemplate<String,Object> kafkaTemplate;
    private static final String RESERVED_TOPIC = "inventory.reserved";
    private static final String RELEASE_TOPIC = "inventory.release";
    private static final String RESERVATION_TIMEOUTS_KEY = "inventory:reservation:timeouts";

    /**
     * Creates the initial inventory for a ticket.
     *
     * STEP 1 -> Confirm that the ticket exists in the ticket service.
     * STEP 2 -> Check that inventory has not already been created for the ticket.
     * STEP 3 -> Create the inventory with total and available stock set to the requested amount.
     * STEP 4 -> Save the new inventory record in MySQL.
     * STEP 5 -> Cache the inventory response and clear any previous not-found marker.
     * STEP 6 -> Initialize the Redis available-stock counter used by the reservation flow.
     *
     * @param request the ticket ID and initial total stock
     * @return the newly created inventory
     * @throws ResponseStatusException if the ticket does not exist or inventory already exists
     */
    public InventoryResponse insert(InsertInventoryRequest request) {

        if (!ticketClient.existsById(request.getTicketId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Ticket not found: " + request.getTicketId()
            );
        }

        Inventory existing =
                inventoryMapper.findByTicketId(request.getTicketId());

        if (existing != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inventory already exists for ticket: " + request.getTicketId()
            );
        }

        Inventory inventory = new Inventory();

        inventory.setId(UUID.randomUUID().toString());
        inventory.setTicketId(request.getTicketId());
        inventory.setTotalStock(request.getTotalStock());
        inventory.setAvailableStock(request.getTotalStock());
        inventory.setReservedStock(0);

        LocalDateTime now = LocalDateTime.now();
        inventory.setCreatedAt(now);
        inventory.setUpdatedAt(now);

        inventoryMapper.insert(inventory);

        InventoryResponse response = mapToResponse(inventory);

        // Cache the inventory response used by ordinary read requests.
        redisTemplate.opsForValue().set(
                inventoryDetailKey(inventory.getTicketId()),
                response,
                CACHE_TTL
        );
        stringRedisTemplate.delete(inventoryNotFoundCacheKey(inventory.getTicketId()));

        // Initialize the Redis counter used for fast available-stock checks.
        stringRedisTemplate.opsForValue().set(
                inventoryStockKey(inventory.getTicketId()),
                String.valueOf(inventory.getAvailableStock())
        );

        return response;
    }

    // Gets one inventory record, preferring the Redis cache before MySQL.
    public InventoryResponse findByTicketId(String ticketId) {

        String key = inventoryDetailKey(ticketId);

        InventoryResponse cached =
                redisTemplate.opsForValue().get(key);

        if (cached != null) {
            return cached;
        }

        String notFoundKey = inventoryNotFoundCacheKey(ticketId);
        String cachedNotFound = stringRedisTemplate.opsForValue().get(notFoundKey);

        // Negative cache prevents repeated MySQL reads for a missing inventory record.
        if ("NOT_FOUND".equals(cachedNotFound)) {
            throw inventoryNotFound(ticketId);
        }

        Inventory inventory =
                inventoryMapper.findByTicketId(ticketId);

        if (inventory == null) {
            stringRedisTemplate.opsForValue().set(
                    notFoundKey,
                    "NOT_FOUND",
                    NOT_FOUND_CACHE_TTL
            );
            throw inventoryNotFound(ticketId);
        }

        InventoryResponse response = mapToResponse(inventory);

        redisTemplate.opsForValue().set(
                key,
                response,
                CACHE_TTL
        );
        stringRedisTemplate.delete(notFoundKey);

        return response;
    }

    // Replaces the available stock amount while keeping it within total stock.
    public InventoryResponse updateAvailableStock(
            String ticketId,
            Integer availableStock
    ) {

        Inventory inventory =
                inventoryMapper.findByTicketId(ticketId);

        if (inventory == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Inventory not found for ticket: " + ticketId
            );
        }

        ensureStockCanBeModified(ticketId);

        if (availableStock == null || availableStock < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Available stock cannot be null or negative"
            );
        }

        if (availableStock > inventory.getTotalStock()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Available stock cannot exceed total stock"
            );
        }

        inventoryMapper.updateAvailableStock(
                ticketId,
                availableStock
        );

        inventory.setAvailableStock(availableStock);
        inventory.setUpdatedAt(LocalDateTime.now());

        // Synchronize the Redis available-stock counter after a manual update.
        stringRedisTemplate.opsForValue().set(
                inventoryStockKey(ticketId),
                String.valueOf(availableStock)
        );

        InventoryResponse response = mapToResponse(inventory);

        // Refresh the inventory-detail cache after a manual update.
        redisTemplate.opsForValue().set(
                inventoryDetailKey(ticketId),
                response,
                CACHE_TTL
        );
        stringRedisTemplate.delete(inventoryNotFoundCacheKey(ticketId));

        return response;
    }

    // Increases available stock and recalculates total stock in the returned response.
    public InventoryResponse increaseAvailableStock(
            String ticketId,
            Integer quantity) {

        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be greater than 0"
            );
        }

        Inventory inventory = inventoryMapper.findByTicketId(ticketId);

        if (inventory == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Inventory not found for ticket: " + ticketId
            );
        }

        ensureStockCanBeModified(ticketId);

        int newTotalStock = inventory.getTotalStock() + quantity;
        int newAvailableStock = inventory.getAvailableStock() + quantity;

        int updatedRows = inventoryMapper.increaseStock(ticketId, quantity);

        if (updatedRows != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unable to increase inventory stock"
            );
        }

        inventory.setTotalStock(newTotalStock);
        inventory.setAvailableStock(newAvailableStock);
        inventory.setUpdatedAt(LocalDateTime.now());

        InventoryResponse response = mapToResponse(inventory);

        redisTemplate.opsForValue().set(
                inventoryDetailKey(ticketId),
                response,
                CACHE_TTL
        );
        stringRedisTemplate.delete(inventoryNotFoundCacheKey(ticketId));

        stringRedisTemplate.opsForValue().set(
                inventoryStockKey(ticketId),
                String.valueOf(newAvailableStock)
        );

        return response;
    }

    // Decreases available stock and recalculates total stock in the returned response.
    public InventoryResponse decreaseAvailableStock(
            String ticketId,
            Integer quantity) {

        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be greater than 0"
            );
        }

        Inventory inventory = inventoryMapper.findByTicketId(ticketId);

        if (inventory == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Inventory not found for ticket: " + ticketId
            );
        }

        ensureStockCanBeModified(ticketId);

        if (inventory.getAvailableStock() < quantity) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Not enough available stock"
            );
        }

        int newTotalStock = inventory.getTotalStock() - quantity;
        int newAvailableStock = inventory.getAvailableStock() - quantity;

        int updatedRows = inventoryMapper.decreaseStock(ticketId, quantity);

        if (updatedRows != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unable to decrease inventory stock"
            );
        }

        inventory.setTotalStock(newTotalStock);
        inventory.setAvailableStock(newAvailableStock);
        inventory.setUpdatedAt(LocalDateTime.now());

        InventoryResponse response = mapToResponse(inventory);

        redisTemplate.opsForValue().set(
                inventoryDetailKey(ticketId),
                response,
                CACHE_TTL
        );
        stringRedisTemplate.delete(inventoryNotFoundCacheKey(ticketId));

        stringRedisTemplate.opsForValue().set(
                inventoryStockKey(ticketId),
                String.valueOf(newAvailableStock)
        );

        return response;
    }

    /*
     * Validates the ticket sale window and reserves stock by executing the Redis Lua script atomically.
     * Lua arguments:
     * KEYS[1] = available-stock key
     * KEYS[2] = stock reserved by this user for this ticket
     * ARGV[1] = quantity requested by the user
     */
    public InventoryResponse reserveStock(ReserveStockRequest request) {
        // STEP 1 -> Confirm that the ticket is currently eligible for reservation.
        // This check happens before Lua so an invalid sale never changes Redis stock.
        ensureTicketCanBeReserved(request.getTicketId());

        // STEP 2 -> Build the Redis keys used by the reservation and compensation scripts.
        String stockKey = inventoryStockKey(request.getTicketId());
        String reservationKey = inventoryReservedKey(
                request.getTicketId(),
                request.getUserId()
        );

        // STEP 3 -> Execute Lua with the stock key, user reservation key, and requested quantity.
        // Lua validates the request and deducts the stock atomically inside Redis.
        Long result = stringRedisTemplate.execute(
                redisConfig.reserveStockScript(),
                List.of(
                        stockKey,
                        reservationKey
                ),
                String.valueOf(request.getReservedStock())
        );

        // STEP 4 -> Continue only when Lua returns 1, which means the reservation succeeded.
        if (!Objects.equals(result, 1L)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unable to reserve the requested stock"
            );
        }

        // TODO: Enable reservation timeout scheduling after Order Service is completed.
//        long expireAt = System.currentTimeMillis() + Duration.ofMinutes(5).toMillis();
//
//        stringRedisTemplate.opsForZSet()
//                .add(
//                        RESERVATION_TIMEOUTS_KEY,
//                        reservationKey,
//                        expireAt
//                );


        // STEP 5 -> Create a unique event for idempotent MySQL synchronization.
        // eventId allows the consumer to implement idempotency when Kafka redelivers a message.
        InventoryReservedEvent event = new InventoryReservedEvent(
                UUID.randomUUID().toString(),
                request.getTicketId(),
                request.getUserId(),
                request.getReservedStock(),
                LocalDateTime.now()
        );




        // STEP 6 -> Send the event and wait for Kafka acknowledgement.
        // If publishing fails, compensate Redis atomically before returning an error.
        try {
            kafkaTemplate.send(
                    RESERVED_TOPIC,
                    request.getTicketId(),
                    event
            ).get();
        } catch (Exception exception) {
            Long compensationResult = compensateReservation(stockKey, reservationKey);

            if (!Objects.equals(compensationResult, 1L)) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Kafka publishing and Redis compensation both failed"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to publish the inventory reservation event"
            );
        }

        // STEP 7 -> Load the base inventory details, such as ID, ticket ID, total stock,
        // and timestamps. The available-stock value returned here may still be stale because
        // the Lua script has already changed the dedicated Redis stock counter.
        InventoryResponse response = findByTicketId(request.getTicketId());

        // STEP 8 -> Read the authoritative remaining stock from the Redis counter that
        // was atomically decremented by the reservation Lua script.
        String availableStock = stringRedisTemplate.opsForValue().get(stockKey);

        // STEP 9 -> Treat a missing stock counter as an inconsistent Redis state because
        // the reservation Lua script and Kafka publishing have already succeeded.
        if (availableStock == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stock cache is missing after reservation"
            );
        }

        // STEP 10 -> Replace the possibly stale response values with the latest Redis data.
        // In the current inventory model, reserved stock is derived as total minus available.
        int latestAvailableStock = Integer.parseInt(availableStock);
        response.setAvailableStock(latestAvailableStock);
        response.setReservedStock(response.getTotalStock() - latestAvailableStock);
        response.setUpdatedAt(LocalDateTime.now());

        // STEP 11 -> Refresh the inventory-detail cache so later read requests see the
        // latest reservation result without querying MySQL immediately.
        redisTemplate.opsForValue().set(
                inventoryDetailKey(request.getTicketId()),
                response,
                CACHE_TTL
        );

        // STEP 12 -> Return only after Redis reservation and Kafka publishing both succeed.
        return response;
    }

    public InventoryResponse releaseStock(ReleaseStockRequest request) {
        // STEP 1 -> Build the Redis keys for available stock and the user's reservation.
        String stockKey = inventoryStockKey(request.getTicketId());
        String reservationKey = inventoryReservedKey(
                request.getTicketId(),
                request.getUserId()
        );

        // STEP 2 -> Read the trusted reservation quantity stored by the reservation Lua script.
        String reservedQuantity = stringRedisTemplate.opsForValue().get(reservationKey);

        // STEP 3 -> Stop when this user no longer has an active reservation for the ticket.
        if (reservedQuantity == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No active reservation was found for this user and ticket"
            );
        }

        // STEP 4 -> Create a unique release event for idempotent MySQL synchronization.
        InventoryReleaseEvent event = new InventoryReleaseEvent(
                UUID.randomUUID().toString(),
                request.getTicketId(),
                request.getUserId(),
                Integer.valueOf(reservedQuantity),
                LocalDateTime.now()
        );

        // STEP 5 -> Atomically return the reserved quantity to Redis stock and delete
        // the user's reservation key. KEYS[1] is stock and KEYS[2] is the reservation.
        Long result = stringRedisTemplate.execute(
                redisConfig.releaseScript(),
                List.of(stockKey, reservationKey),
                reservedQuantity
        );

        // STEP 6 -> Continue only when Lua returns 1, which means the release succeeded.
        if (!Objects.equals(result, 1L)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unable to release the reserved stock"
            );
        }

        // STEP 7 -> Publish the release event and wait for Kafka acknowledgement.
        // If publishing fails, reserve the same quantity again to compensate Redis.
        try {
            kafkaTemplate.send(
                    RELEASE_TOPIC,
                    request.getTicketId(),
                    event
            ).get();
        } catch (Exception exception) {
            Long compensationResult = stringRedisTemplate.execute(
                    redisConfig.reserveStockScript(),
                    List.of(stockKey, reservationKey),
                    reservedQuantity
            );

            if (!Objects.equals(compensationResult, 1L)) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Kafka publishing and Redis release compensation both failed"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to publish the inventory release event"
            );
        }

        // STEP 8 -> Load the base inventory details and read the latest Redis stock value.
        InventoryResponse response = findByTicketId(request.getTicketId());
        String availableStock = stringRedisTemplate.opsForValue().get(stockKey);

        if (availableStock == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stock cache is missing after release"
            );
        }

        // STEP 9 -> Update the response with the latest available and reserved quantities.
        int latestAvailableStock = Integer.parseInt(availableStock);
        response.setAvailableStock(latestAvailableStock);
        response.setReservedStock(response.getTotalStock() - latestAvailableStock);
        response.setUpdatedAt(LocalDateTime.now());

        // STEP 10 -> Refresh the inventory-detail cache with the released stock result.
        redisTemplate.opsForValue().set(
                inventoryDetailKey(request.getTicketId()),
                response,
                CACHE_TTL
        );

        // STEP 11 -> Return the latest inventory response to the client.
        return response;
    }

    // Identifies the cached inventory response: inventory:{ticketId}
    private String inventoryDetailKey(String ticketId) {
        return "inventory:" + ticketId;
    }

    // Identifies the available-stock counter used by the reservation script.
    private String inventoryStockKey(String ticketId) {
        return "inventory:stock:" + ticketId;
    }

    // Identifies the temporary missing-inventory marker used to reduce database lookups.
    private String inventoryNotFoundCacheKey(String ticketId) {
        return "inventory:not-found:" + ticketId;
    }

    private ResponseStatusException inventoryNotFound(String ticketId) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Inventory not found for ticket: " + ticketId
        );
    }

    /*
     * Manual stock changes are allowed only before the lock window begins.
     * Once the current time reaches saleStartTime minus 10 minutes, update,
     * increase, and decrease operations remain blocked.
     */
    private void ensureStockCanBeModified(String ticketId) {
        TicketScheduleResponse ticket = ticketClient.getTicketById(ticketId);
        LocalDateTime saleStartTime = ticket.getSaleStartTime();

        if (saleStartTime == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket sale start time is not configured"
            );
        }

        LocalDateTime lockTime = saleStartTime.minus(STOCK_CHANGE_LOCK_WINDOW);

        if (!LocalDateTime.now().isBefore(lockTime)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stock cannot be changed from 10 minutes before the ticket sale start time onward"
            );
        }
    }

    /*
     * Reservations are accepted only while the configured sale window is active.
     * Terminal ticket statuses always block reservations. DRAFT is allowed during
     * the time window because Ticket Service does not yet persist an automatic
     * DRAFT-to-ON_SALE transition.
     */
    private void ensureTicketCanBeReserved(String ticketId) {
        TicketScheduleResponse ticket = ticketClient.getTicketById(ticketId);
        LocalDateTime saleStartTime = ticket.getSaleStartTime();
        LocalDateTime saleEndTime = ticket.getSaleEndTime();
        String status = ticket.getStatus();

        if (saleStartTime == null || saleEndTime == null || status == null || status.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket sale schedule or status is not configured"
            );
        }

        if ("CANCELLED".equals(status)
                || "ENDED".equals(status)
                || "SOLD_OUT".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket is not available for reservation: " + status
            );
        }

        if (!"DRAFT".equals(status) && !"ON_SALE".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unsupported ticket status: " + status
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(saleStartTime)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket sale has not started"
            );
        }

        if (!now.isBefore(saleEndTime)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket sale has ended"
            );
        }
    }

    // Identifies one user's reserved quantity for a ticket.
    private String inventoryReservedKey(String ticketId,String userId){
        return "inventory:reserved:" + ticketId + ":" + userId;
    }

    private String inventoryReleaseKey(String ticketId,String userId){
        return "inventory:release:" + ticketId + ":" + userId;
    }

    // Restores Redis stock when the reservation event cannot be published to Kafka.
    private Long compensateReservation(String stockKey, String reservationKey) {
        return stringRedisTemplate.execute(
                redisConfig.compensateScript(),
                List.of(
                        stockKey,
                        reservationKey
                )
        );
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        InventoryResponse response = new InventoryResponse();

        response.setId(inventory.getId());
        response.setTicketId(inventory.getTicketId());
        response.setTotalStock(inventory.getTotalStock());
        response.setAvailableStock(inventory.getAvailableStock());
        response.setReservedStock(inventory.getReservedStock());
        response.setCreatedAt(inventory.getCreatedAt());
        response.setUpdatedAt(inventory.getUpdatedAt());

        return response;
    }

}
