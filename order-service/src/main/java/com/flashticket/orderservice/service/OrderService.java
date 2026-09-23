package com.flashticket.orderservice.service;

import com.flashticket.orderservice.dto.CreateOrderRequest;
import com.flashticket.orderservice.dto.OrderResponse;
import com.flashticket.orderservice.entity.Order;
import com.flashticket.orderservice.mapper.OrderMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor

public class OrderService {

    private final KafkaTemplate<String,Order> kafkaTemplate;
    private final RedisTemplate<String, OrderResponse> redisTemplate;
    private final OrderMapper orderMapper;
//    public InventoryResponse insert(InsertInventoryRequest request) {
//
//        if (!ticketClient.existsById(request.getTicketId())) {
//            throw new ResponseStatusException(
//                    HttpStatus.NOT_FOUND,
//                    "Ticket not found: " + request.getTicketId()
//            );
//        }
//
//        Inventory existing =
//                inventoryMapper.findByTicketId(request.getTicketId());
//
//        if (existing != null) {
//            throw new ResponseStatusException(
//                    HttpStatus.CONFLICT,
//                    "Inventory already exists for ticket: " + request.getTicketId()
//            );
//        }
//
//        Inventory inventory = new Inventory();
//
//        inventory.setId(UUID.randomUUID().toString());
//        inventory.setTicketId(request.getTicketId());
//        inventory.setTotalStock(request.getTotalStock());
//        inventory.setAvailableStock(request.getTotalStock());
//        inventory.setReservedStock(0);
//
//        LocalDateTime now = LocalDateTime.now();
//        inventory.setCreatedAt(now);
//        inventory.setUpdatedAt(now);
//
//        inventoryMapper.insert(inventory);
//
//        InventoryResponse response = mapToResponse(inventory);
//
//        // Cache the inventory response used by ordinary read requests.
//        redisTemplate.opsForValue().set(
//                inventoryDetailKey(inventory.getTicketId()),
//                response,
//                CACHE_TTL
//        );
//        stringRedisTemplate.delete(inventoryNotFoundCacheKey(inventory.getTicketId()));
//
//        // Initialize the Redis counter used for fast available-stock checks.
//        stringRedisTemplate.opsForValue().set(
//                inventoryStockKey(inventory.getTicketId()),
//                String.valueOf(inventory.getAvailableStock())
//        );
//
//        return response;
//    }
//
//
    public OrderResponse createOrder(@Valid CreateOrderRequest request) {

    }

    public OrderResponse getOrderById(String orderId) {
    }

    public List<OrderResponse> getOrdersByUserId(String userId) {
    }

    public OrderResponse cancelOrder(String orderId) {
    }

    public OrderResponse markAsPaid(String orderId) {
    }

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
