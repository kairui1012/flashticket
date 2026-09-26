package com.flashticket.orderservice.service;

import com.flashticket.orderservice.entity.ExpireResult;
import com.flashticket.orderservice.entity.Order;
import com.flashticket.orderservice.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Slf4j
@Transactional
@Service


public class OrderExpirationService {

    private final OrderMapper orderMapper;
    private final InventoryReleaseTaskService inventoryReleaseTaskService;

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
                "PAYMENT_TIMEOUT"
        );

        return ExpireResult.EXPIRED;
    }
}
