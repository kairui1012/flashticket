package com.flashticket.orderservice.service;

import com.flashticket.orderservice.client.PaymentClient;
import com.flashticket.orderservice.event.InventoryReservedEvent;
import com.flashticket.orderservice.client.InventoryClient;
import com.flashticket.orderservice.client.TicketClient;
import com.flashticket.orderservice.dto.CreateOrderRequest;
import com.flashticket.orderservice.dto.OrderResponse;
import com.flashticket.orderservice.dto.ReleaseInventoryRequest;
import com.flashticket.orderservice.dto.TicketPriceResponse;
import com.flashticket.orderservice.entity.Order;
import com.flashticket.orderservice.entity.OrderStatus;
import com.flashticket.orderservice.mapper.OrderMapper;
import feign.FeignException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
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

    private static final Duration ORDER_CACHE_TTL = Duration.ofMinutes(5);

    private final KafkaTemplate<String,Order> kafkaTemplate;
    private final RedisTemplate<String, OrderResponse> redisTemplate;
    private final RedisTemplate<String, List<OrderResponse>> redisTemplateForOrderList;

    private static final String PAYMENT = "PAYMENT";

    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;

    private final OrderMapper orderMapper;
    private final TicketClient ticketClient;


    public void createOrderFromInventoryEvent(InventoryReservedEvent event) {
        TicketPriceResponse ticket;

        try {
            ticket = ticketClient.getTicketById(event.getTicketId());
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Ticket not found: " + event.getTicketId()
            );
        }

        if (ticket.getPrice() == null || ticket.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket price is not configured"
            );
        }

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

        int insertedRows = orderMapper.insert(order);

        if (insertedRows != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to create the order"
            );
        }

        OrderResponse response = mapToResponse(order);

        redisTemplate.opsForValue().set(
                orderCacheKey(order.getId()),
                response,
                ORDER_CACHE_TTL
        );
    }

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

        // 条件更新：只有 PENDING_PAYMENT 才能取消
        int updatedRows = orderMapper.cancelPendingOrder(orderId, now);

        if (updatedRows != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order status has already changed"
            );
        }

        ReleaseInventoryRequest releaseRequest =
                new ReleaseInventoryRequest(
                        order.getTicketId(),
                        order.getUserId(),
                        order.getQuantity()
                );

        inventoryClient.releaseStock(releaseRequest);

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(now);

        OrderResponse response = mapToResponse(order);

        redisTemplate.opsForValue().set(
                orderCacheKey(orderId),
                response,
                ORDER_CACHE_TTL
        );

        // 用户订单列表缓存也已经过期
        redisTemplateForOrderList.delete(
                userOrdersKey(order.getUserId())
        );

        return response;
    }


    public void markAsPaid(String orderId) {
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
                    "Only a pending payment order can be pay"
            );
        }
        kafkaTemplate.send(PAYMENT,order);
    }

    private String orderCacheKey(String orderId) {
        return "order:" + orderId;
    }

    private String userOrdersKey(String userId) {
        return "orders:user:" + userId;
    }

    private BigDecimal calculateTotalAmount(BigDecimal unitPrice, Integer quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    //    Current no use（just put here for future？）
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
