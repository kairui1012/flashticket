package com.flashticket.paymentservice.service;

import com.flashticket.paymentservice.dto.CreatePaymentRequest;
import com.flashticket.paymentservice.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private static final String ORDER_CREATED_TOPIC = "order.created";

    private final PaymentService paymentService;

    @KafkaListener(
            topics = ORDER_CREATED_TOPIC,
            groupId = "payment-service",
            properties = "spring.json.value.default.type=com.flashticket.paymentservice.event.OrderCreatedEvent"
    )
    public void handleOrderCreated(OrderCreatedEvent event) {
        validateEvent(event);

        log.info(
                "Processing order created event: eventId={}, orderId={}",
                event.getEventId(),
                event.getOrderId()
        );

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderId(event.getOrderId());

        paymentService.createPayment(request);
    }

    private void validateEvent(OrderCreatedEvent event) {
        if (event == null
                || event.getEventId() == null
                || event.getEventId().isBlank()
                || event.getOrderId() == null
                || event.getOrderId().isBlank()) {
            throw new IllegalArgumentException("Invalid order created event");
        }
    }
}
