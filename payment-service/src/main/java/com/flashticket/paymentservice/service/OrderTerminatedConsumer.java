package com.flashticket.paymentservice.service;

import com.flashticket.paymentservice.entity.OrderStatus;
import com.flashticket.paymentservice.event.OrderTerminatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderTerminatedConsumer {

    private static final String ORDER_TERMINATED_TOPIC = "order.terminated";

    private final PaymentService paymentService;

    @KafkaListener(
            topics = ORDER_TERMINATED_TOPIC,
            groupId = "payment-service",
            properties = "spring.json.value.default.type=com.flashticket.paymentservice.event.OrderTerminatedEvent"
    )
    public void handleOrderTerminated(OrderTerminatedEvent event) {
        validateEvent(event);

        paymentService.markOrderTerminated(
                event.getOrderId(),
                event.getStatus(),
                event.getOccurredAt()
        );

        log.info(
                "Processed order terminated event: eventId={}, orderId={}, status={}",
                event.getEventId(),
                event.getOrderId(),
                event.getStatus()
        );
    }

    private void validateEvent(OrderTerminatedEvent event) {
        if (event == null
                || event.getEventId() == null
                || event.getEventId().isBlank()
                || event.getOrderId() == null
                || event.getOrderId().isBlank()
                || event.getOccurredAt() == null
                || (event.getStatus() != OrderStatus.CANCELLED
                && event.getStatus() != OrderStatus.EXPIRED)) {
            throw new IllegalArgumentException("Invalid order terminated event");
        }
    }
}
