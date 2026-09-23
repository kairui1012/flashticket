package com.flashticket.orderservice.service;

import com.flashticket.orderservice.client.TicketClient;
import com.flashticket.orderservice.dto.CreateOrderRequest;
import com.flashticket.orderservice.dto.OrderResponse;
import com.flashticket.orderservice.dto.TicketPriceResponse;
import com.flashticket.orderservice.entity.Order;
import com.flashticket.orderservice.entity.OrderStatus;
import com.flashticket.orderservice.mapper.OrderMapper;
import feign.FeignException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Duration ORDER_CACHE_TTL = Duration.ofMinutes(10);

    private final KafkaTemplate<String,Order> kafkaTemplate;
    private final RedisTemplate<String, OrderResponse> redisTemplate;
    private final OrderMapper orderMapper;
    private final TicketClient ticketClient;


    public OrderResponse createOrder(@Valid CreateOrderRequest request) {

        Order existing =
                orderMapper.findByUserIdAndTicketId(request.getUserId(),request.getTicketId());

        if (existing != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A pending order already exists for ticket "
                            + request.getTicketId()
                            + " and user "
                            + request.getUserId()
            );
        }

        TicketPriceResponse ticket;

        try {
            ticket = ticketClient.getTicketById(request.getTicketId());
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Ticket not found: " + request.getTicketId()
            );
        }

        if (ticket.getPrice() == null || ticket.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket price is not configured"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();

        order.setId(UUID.randomUUID().toString());
        order.setUserId(request.getUserId());
        order.setTicketId(request.getTicketId());
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(ticket.getPrice());
        order.setTotalAmount(calculateTotalAmount(ticket.getPrice(), request.getQuantity()));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCreatedAt(now);
        order.setExpiresAt(now.plusMinutes(5));
        order.setPaidAt(null);
        order.setUpdatedAt(now);

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

        return response;

    }

    public OrderResponse getOrderById(String orderId) {
    }

    public List<OrderResponse> getOrdersByUserId(String userId) {
    }

    public OrderResponse cancelOrder(String orderId) {
    }

    public OrderResponse markAsPaid(String orderId) {
    }

    private String orderCacheKey(String orderId) {
        return "order:" + orderId;
    }

    private BigDecimal calculateTotalAmount(BigDecimal unitPrice, Integer quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
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
