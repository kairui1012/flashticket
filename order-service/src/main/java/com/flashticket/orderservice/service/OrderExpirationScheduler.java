package com.flashticket.orderservice.service;


import com.flashticket.orderservice.entity.Order;
import com.flashticket.orderservice.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Service

public class OrderExpirationScheduler {

    private final OrderMapper orderMapper;
    private final OrderExpirationService orderExpirationService;
    @Scheduled(
            fixedDelayString = "${order.expiration.scan-delay-ms:5000}",
            initialDelayString = "${order.expiration.initial-delay-ms:10000}"
            )

    public void scan() {
        List<Order> orders =
                orderMapper.findExpiredPendingOrders(LocalDateTime.now(), 100);

        for (Order order : orders) {
            try {
                orderExpirationService.expireOne(order.getId());
            } catch (Exception exception) {
                log.error(
                        "Failed to expire order: orderId={}",
                        order.getId(),
                        exception
                );
            }
        }

        if (!orders.isEmpty()) {
            log.info(
                    "Expired-order scan completed: scanned={}",
                    orders.size()
            );
        }
    }
}
