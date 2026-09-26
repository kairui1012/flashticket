package com.flashticket.orderservice.service;

import com.flashticket.orderservice.client.PaymentClient;
import com.flashticket.orderservice.event.InventoryReservedEvent;
import com.flashticket.orderservice.client.InventoryClient;
import com.flashticket.orderservice.client.TicketClient;
import com.flashticket.orderservice.dto.OrderResponse;
import com.flashticket.orderservice.dto.ReleaseInventoryRequest;
import com.flashticket.orderservice.dto.TicketPriceResponse;
import com.flashticket.orderservice.entity.Order;
import com.flashticket.orderservice.entity.OrderStatus;
import com.flashticket.orderservice.mapper.OrderMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.flashticket.orderservice.entity.OrderStatus.CANCELLED;

@Service
@RequiredArgsConstructor

public class OrderService {

    // Keeps order cache entries for five minutes.
    private static final Duration ORDER_CACHE_TTL = Duration.ofMinutes(5);

    private final KafkaTemplate<String,Order> kafkaTemplate;
    private final RedisTemplate<String, OrderResponse> redisTemplate;
    private final RedisTemplate<String, List<OrderResponse>> redisTemplateForOrderList;

    private final InventoryReleaseTaskService inventoryReleaseTaskService;

    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;

    private final OrderMapper orderMapper;
    private final TicketClient ticketClient;


    // Creates a pending-payment order after inventory has been reserved successfully.
    public void createOrderFromInventoryEvent(InventoryReservedEvent event) {
        TicketPriceResponse ticket;

        // Fetch the authoritative ticket price from Ticket Service.
        try {
            ticket = ticketClient.getTicketById(event.getTicketId());
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Ticket not found: " + event.getTicketId()
            );
        }

        // Reject an order when the ticket price is missing or invalid.
        if (ticket.getPrice() == null || ticket.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket price is not configured"
            );
        }

        // Build an order with a five-minute payment deadline.
        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setUserId(event.getUserId());
        order.setTicketId(event.getTicketId());
        order.setQuantity(event.getQuantity());
        order.setUnitPrice(ticket.getPrice());
        order.setTotalAmount(calculateTotalAmount(ticket.getPrice() , event.getQuantity()));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCreatedAt(event.getOccurredAt());
        order.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        order.setPaidAt(null);
        order.setUpdatedAt(LocalDateTime.now());

        // Persist the newly created order.
        int insertedRows = orderMapper.insert(order);

        if (insertedRows != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to create the order"
            );
        }

        OrderResponse response = mapToResponse(order);

        // Cache the order for subsequent read requests.
        redisTemplate.opsForValue().set(
                orderCacheKey(order.getId()),
                response,
                ORDER_CACHE_TTL
        );
    }

    // Returns an order from Redis when available, otherwise loads it from MySQL.
    public OrderResponse getOrderById(String orderId) {
        String key = orderCacheKey(orderId);

        OrderResponse cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            return cached;
        }

        Order order = orderMapper.findById(orderId);

        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Order not found: " + orderId
            );
        }

        OrderResponse response = mapToResponse(order);

        redisTemplate.opsForValue().set(
                key,
                response,
                ORDER_CACHE_TTL
        );

        return response;
    }

    // Returns and caches the complete order list for a user.
    public List<OrderResponse> getOrdersByUserId(String userId) {
        String key = userOrdersKey(userId);

        List<OrderResponse> cached =
                redisTemplateForOrderList.opsForValue().get(key);

        if (cached != null) {
            return cached;
        }

        List<OrderResponse> responses = orderMapper.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();

        redisTemplateForOrderList.opsForValue().set(
                key,
                responses,
                ORDER_CACHE_TTL
        );

        return responses;
    }


    // Cancels a pending-payment order and releases its reserved inventory.
    @Transactional
    public OrderResponse cancelOrder(String orderId) {
        Order order = orderMapper.findById(orderId);

        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Order not found: " + orderId
            );
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return mapToResponse(order);
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a pending payment order can be cancelled"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        // Use a conditional update so only a PENDING_PAYMENT order can be canceled.
        int updatedRows = orderMapper.cancelPendingOrder(orderId, now);

        if (updatedRows != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order status has already changed"
            );
        }

        ReleaseInventoryRequest releaseRequest = inventoryReleaseTaskService.createTask(
                order.getId(),       // releaseId
                order.getId(),       // orderId
                order.getTicketId(),
                order.getUserId(),
                order.getQuantity(),
                "USER_CANCELLED"
        );

        // Return the reserved quantity to Inventory Service.
        inventoryClient.releaseStock(releaseRequest);

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(now);

        OrderResponse response = mapToResponse(order);

        redisTemplate.opsForValue().set(
                orderCacheKey(orderId),
                response,
                ORDER_CACHE_TTL
        );

        // Invalidate the user-order-list cache because the order status has changed.
        redisTemplateForOrderList.delete(
                userOrdersKey(order.getUserId())
        );

        return response;
    }


    // Marks an eligible pending-payment order as paid.
    public OrderResponse markAsPaid(String orderId) {
        Order order = orderMapper.findById(orderId);

        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Order not found: " + orderId
            );
        }

        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == CANCELLED) {
            return mapToResponse(order);
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a pending payment order can be paid"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (!order.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The payment deadline has passed"
            );
        }

        // The conditional update prevents payment after expiration or another status change.
        int updatedRows = orderMapper.markPendingOrderAsPaid(
                orderId,
                now,
                now
        );

        if (updatedRows != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order status has changed or the payment deadline has passed"
            );
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(now);
        order.setUpdatedAt(now);

        OrderResponse response = mapToResponse(order);

        // Refresh the individual-order cache with the paid status.
        redisTemplate.opsForValue().set(
                orderCacheKey(orderId),
                response,
                ORDER_CACHE_TTL
        );

        // Invalidate the cached user order list so it is rebuilt with the new status.
        redisTemplateForOrderList.delete(
                userOrdersKey(order.getUserId())
        );

        return response;
    }

    // Builds the Redis key for an individual order.
    private String orderCacheKey(String orderId) {
        return "order:" + orderId;
    }

    // Builds the Redis key for a user's order list.
    private String userOrdersKey(String userId) {
        return "orders:user:" + userId;
    }

    // Calculates the total amount from the unit price and quantity.
    private BigDecimal calculateTotalAmount(BigDecimal unitPrice, Integer quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // Currently unused. Retained as a reference for a possible future synchronous order endpoint.
    //    public OrderResponse createOrder(@Valid CreateOrderRequest request) {
    //
    //        Order existing =
    //                orderMapper.findByUserIdAndTicketId(request.getUserId(),request.getTicketId());
    //
    //        if (existing != null) {
    //            throw new ResponseStatusException(
    //                    HttpStatus.CONFLICT,
    //                    "A pending order already exists for ticket "
    //                            + request.getTicketId()
    //                            + " and user "
    //                            + request.getUserId()
    //            );
    //        }
    //
    //        TicketPriceResponse ticket;
    //
    //        try {
    //            ticket = ticketClient.getTicketById(request.getTicketId());
    //        } catch (FeignException.NotFound exception) {
    //            throw new ResponseStatusException(
    //                    HttpStatus.NOT_FOUND,
    //                    "Ticket not found: " + request.getTicketId()
    //            );
    //        }
    //
    //        if (ticket.getPrice() == null || ticket.getPrice().compareTo(BigDecimal.ZERO) < 0) {
    //            throw new ResponseStatusException(
    //                    HttpStatus.CONFLICT,
    //                    "Ticket price is not configured"
    //            );
    //        }
    //
    //        LocalDateTime now = LocalDateTime.now();
    //        Order order = new Order();
    //
    //        order.setId(UUID.randomUUID().toString());
    //        order.setUserId(request.getUserId());
    //        order.setTicketId(request.getTicketId());
    //        order.setQuantity(request.getQuantity());
    //        order.setUnitPrice(ticket.getPrice());
    //        order.setTotalAmount(calculateTotalAmount(ticket.getPrice(), request.getQuantity()));
    //        order.setStatus(OrderStatus.PENDING_PAYMENT);
    //        order.setCreatedAt(now);
    //        order.setExpiresAt(now.plusMinutes(5));
    //        order.setPaidAt(null);
    //        order.setUpdatedAt(now);
    //
    //        int insertedRows = orderMapper.insert(order);
    //
    //        if (insertedRows != 1) {
    //            throw new ResponseStatusException(
    //                    HttpStatus.INTERNAL_SERVER_ERROR,
    //                    "Unable to create the order"
    //            );
    //        }
    //
    //        OrderResponse response = mapToResponse(order);
    //
    //        redisTemplate.opsForValue().set(
    //                orderCacheKey(order.getId()),
    //                response,
    //                ORDER_CACHE_TTL
    //        );
    //
    //        return response;
    //
    //    }

    // Converts the persistence entity into the API response model.
    private OrderResponse mapToResponse(Order order) {
        OrderResponse response = new OrderResponse();

        response.setId(order.getId());
        response.setUserId(order.getUserId());
        response.setTicketId(order.getTicketId());
        response.setQuantity(order.getQuantity());
        response.setUnitPrice(order.getUnitPrice());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setExpiresAt(order.getExpiresAt());
        response.setPaidAt(order.getPaidAt());

        return response;
    }


}
