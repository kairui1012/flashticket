package com.flashticket.orderservice.service;

import com.flashticket.orderservice.event.EventType;
import com.flashticket.orderservice.entity.PaymentApplyResult;
import com.flashticket.orderservice.event.PaymentSucceededEvent;
import com.flashticket.orderservice.event.ProcessedEvent;
import com.flashticket.orderservice.mapper.ProcessedEventMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Slf4j
@Data

public class PaymentSuccessConsumer {

    private final OrderService orderService;
    private final ProcessedEventMapper processedEventMapper;
    private static final String PAYMENT_SUCCESS_TOPIC = "payment.succeeded";

    @KafkaListener(
            topics = PAYMENT_SUCCESS_TOPIC,
            groupId = "order-service",
            properties = "spring.json.value.default.type=com.flashticket.orderservice.event.PaymentSucceededEvent"
    )
    @Transactional
    public void handlePaymentSuccess(PaymentSucceededEvent event) {
        validateEvent(event);

        String orderId = event.getOrderId();

        ProcessedEvent processedEvent = new ProcessedEvent(
                event.getEventId(),
                EventType.PAYMENT_SUCCEEDED,
                LocalDateTime.now()
        );

        int inserted = processedEventMapper.insertIfAbsent(processedEvent);

        if (inserted == 0) {
            log.info(
                    "Skipping duplicate payment succeeded event: eventId={}, paymentId={}, orderId={}",
                    event.getEventId(),
                    event.getPaymentId(),
                    orderId
            );
            return;
        }

        PaymentApplyResult result = orderService.markAsPaid(
                orderId,
                event.getPaidAt(),
                event.getAmount()
        );

        if (result == PaymentApplyResult.LATE_PAYMENT_IGNORED) {
            log.warn(
                    "LATE_PAYMENT_IGNORED: eventId={}, paymentId={}, orderId={}, "
                            + "providerTransactionId={}, amount={}, paidAt={}",
                    event.getEventId(),
                    event.getPaymentId(),
                    orderId,
                    event.getProviderTransactionId(),
                    event.getAmount(),
                    event.getPaidAt()
            );
            return;
        }

        log.info(
                "Processed payment succeeded event: eventId={}, paymentId={}, orderId={}, result={}",
                event.getEventId(),
                event.getPaymentId(),
                orderId,
                result
        );
    }

    private void validateEvent(PaymentSucceededEvent event) {
        if (event == null
                || event.getEventId() == null
                || event.getEventId().isBlank()
                || event.getPaymentId() == null
                || event.getPaymentId().isBlank()
                || event.getOrderId() == null
                || event.getOrderId().isBlank()
                || event.getProviderTransactionId() == null
                || event.getProviderTransactionId().isBlank()
                || event.getAmount() == null
                || event.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || event.getPaidAt() == null
                || event.getOccurredAt() == null) {
            throw new IllegalArgumentException("Invalid payment succeeded event");
        }
    }
}
