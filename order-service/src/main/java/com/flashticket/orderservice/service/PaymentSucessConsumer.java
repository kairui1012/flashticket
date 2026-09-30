package com.flashticket.orderservice.service;

import com.flashticket.orderservice.event.InventoryReservedEvent;
import com.flashticket.orderservice.event.PaymentSucceededEvent;
import com.flashticket.orderservice.mapper.OrderMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Data

public class PaymentSucessConsumer {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private static final String PAYMENT_SUCCESS_TOPIC = "payment.succeeded";

    @KafkaListener(
            topics = PAYMENT_SUCCESS_TOPIC,
            groupId = "order-service"
    )
    @Transactional
    public void handlePaymentSuccess(PaymentSucceededEvent event) {
        
    }
}