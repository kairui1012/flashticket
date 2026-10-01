package com.flashticket.orderservice.service;

import com.flashticket.orderservice.dto.OrderResponse;
import com.flashticket.orderservice.entity.ExpireResult;
import com.flashticket.orderservice.entity.Order;
import com.flashticket.orderservice.entity.OrderStatus;
import com.flashticket.orderservice.event.OrderTerminatedEvent;
import com.flashticket.orderservice.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
@Transactional
@Service


public class OrderExpirationService {

    private final OrderMapper orderMapper;
    private final InventoryReleaseTaskService inventoryReleaseTaskService;
    private final OrderOutboxService orderOutboxService;
    private final RedisTemplate<String, OrderResponse> redisTemplate;
    private final RedisTemplate<String, List<OrderResponse>> redisTemplateForOrderList;

    public ExpireResult expireOne(String orderId) {
        Order order = orderMapper.findById(orderId);

        if (order == null) {
            return ExpireResult.NOT_FOUND;
        }

        LocalDateTime now = LocalDateTime.now();

        int updatedRows = orderMapper.expirePendingOrder(
                orderId,
                now
        );

        if (updatedRows == 0) {
            return ExpireResult.SKIPPED;
        }

        inventoryReleaseTaskService.createTask(
                order.getId(),        // Reuse the order ID as the release ID at this stage.
                order.getId(),        // Order ID
                order.getTicketId(),
                order.getUserId(),
                order.getQuantity(),
                OrderStatus.EXPIRED
        );

        orderOutboxService.createOrderTerminatedEvent(
                new OrderTerminatedEvent(
                        UUID.randomUUID().toString(),
                        order.getId(),
                        OrderStatus.EXPIRED,
                        now
                )
        );

        // Remove the stale PENDING_PAYMENT entry for this order.
        redisTemplate.delete(orderCacheKey(order.getId()));

        // Invalidate the user order list so it is rebuilt with the EXPIRED status.
        redisTemplateForOrderList.delete(userOrdersKey(order.getUserId()));

        return ExpireResult.EXPIRED;
    }

    private String orderCacheKey(String orderId) {
        return "order:" + orderId;
    }

    private String userOrdersKey(String userId) {
        return "orders:user:" + userId;
    }
}
