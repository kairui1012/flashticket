package com.flashticket.inventoryservice.service;

import com.flashticket.inventoryservice.client.TicketClient;
import com.flashticket.inventoryservice.dto.InventoryResponse;
import com.flashticket.inventoryservice.dto.InsertInventoryRequest;
import com.flashticket.inventoryservice.dto.ReleaseStockRequest;
import com.flashticket.inventoryservice.dto.ReserveStockRequest;
import com.flashticket.inventoryservice.entity.Inventory;
import com.flashticket.inventoryservice.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor

public class InventoryService {

    // Both the inventory detail cache and the stock counter expire after 10 minutes.
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration NOT_FOUND_CACHE_TTL = Duration.ofSeconds(30);

    private final InventoryMapper inventoryMapper;
    private final RedisTemplate<String,InventoryResponse> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private final TicketClient ticketClient;

    // Creates the initial inventory record after confirming the ticket exists.
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

        // Cache the complete inventory record for ordinary inventory reads.
        redisTemplate.opsForValue().set(
                inventoryCacheKey("",inventory.getTicketId()),
                response,
                CACHE_TTL
        );
        stringRedisTemplate.delete(inventoryNotFoundCacheKey(inventory.getTicketId()));

        // Store only the available amount for fast stock checks.
        stringRedisTemplate.opsForValue().set(
                inventoryCacheKey("stock",inventory.getTicketId()),
                String.valueOf(inventory.getAvailableStock()),
                CACHE_TTL
        );

        return response;
    }

    // Gets one inventory record, preferring the Redis cache before MySQL.
    public InventoryResponse findByTicketId(String ticketId) {

        String key = inventoryCacheKey("", ticketId);

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

        if (availableStock < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Available stock cannot be negative"
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

        // Refresh the fast available-stock cache after a manual update.
        stringRedisTemplate.opsForValue().set(
                inventoryCacheKey("stock", ticketId),
                String.valueOf(availableStock),
                CACHE_TTL
        );

        InventoryResponse response = mapToResponse(inventory);

        // Refresh the complete inventory cache after a manual update.
        redisTemplate.opsForValue().set(
                inventoryCacheKey("", ticketId),
                response,
                CACHE_TTL
        );
        stringRedisTemplate.delete(inventoryNotFoundCacheKey(ticketId));

        return response;
    }

    // Adds new stock to both the total and available quantities.
    public InventoryResponse increaseAvailableStock(String ticketId, Integer quantity) {

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

        int newTotalStock = inventory.getTotalStock() + quantity;
        int newAvailableStock = inventory.getAvailableStock() + quantity;

        inventoryMapper.increaseStock(ticketId, quantity);

        inventory.setTotalStock(newTotalStock);
        inventory.setAvailableStock(newAvailableStock);
        inventory.setUpdatedAt(LocalDateTime.now());

        InventoryResponse response = mapToResponse(inventory);

        redisTemplate.opsForValue().set(
                inventoryCacheKey("", ticketId),
                response,
                CACHE_TTL
        );
        stringRedisTemplate.delete(inventoryNotFoundCacheKey(ticketId));

        stringRedisTemplate.opsForValue().set(
                inventoryCacheKey("stock", ticketId),
                String.valueOf(newAvailableStock),
                CACHE_TTL
        );

        return response;
    }

    // Permanently removes stock from both the total and available quantities.
    public InventoryResponse decreaseAvailableStock(String ticketId, Integer quantity) {

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

        if (inventory.getAvailableStock() < quantity) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Not enough available stock"
            );
        }

        int newTotalStock = inventory.getTotalStock() - quantity;
        int newAvailableStock = inventory.getAvailableStock() - quantity;

        inventoryMapper.decreaseStock(ticketId, quantity);

        inventory.setTotalStock(newTotalStock);
        inventory.setAvailableStock(newAvailableStock);
        inventory.setUpdatedAt(LocalDateTime.now());

        InventoryResponse response = mapToResponse(inventory);

        redisTemplate.opsForValue().set(
                inventoryCacheKey("", ticketId),
                response,
                CACHE_TTL
        );
        stringRedisTemplate.delete(inventoryNotFoundCacheKey(ticketId));

        stringRedisTemplate.opsForValue().set(
                inventoryCacheKey("stock", ticketId),
                String.valueOf(newAvailableStock),
                CACHE_TTL
        );

        return response;
    }

    public InventoryResponse reserveStock(ReserveStockRequest request) {
    }

    public InventoryResponse releaseStock(ReleaseStockRequest request) {
    }

    private String inventoryCacheKey(String text, String id) {
        if (text == null || text.isBlank()) {
            return "inventory:" + id;
        }

        return "inventory:" + text + ":" + id;
    }

    private String inventoryNotFoundCacheKey(String ticketId) {
        return "inventory:not-found:" + ticketId;
    }

    private ResponseStatusException inventoryNotFound(String ticketId) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Inventory not found for ticket: " + ticketId
        );
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        InventoryResponse response = new InventoryResponse();

        response.setId(inventory.getId());
        response.setTicketId(inventory.getTicketId());
        response.setTotalStock(inventory.getTotalStock());
        response.setAvailableStock(inventory.getAvailableStock());
        response.setCreatedAt(inventory.getCreatedAt());
        response.setUpdatedAt(inventory.getUpdatedAt());

        return response;
    }


}
