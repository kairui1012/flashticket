package com.flashticket.orderservice.service;

import com.flashticket.orderservice.event.OrderOutboxEvent;
import com.flashticket.orderservice.event.OrderCreatedEvent;
import com.flashticket.orderservice.mapper.OrderOutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderOutboxWorker {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_COUNT = 5;
    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(1);

    private final OrderOutboxEventMapper outboxEventMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(
            fixedDelayString = "${order.outbox.worker-delay-ms:5000}",
            initialDelayString = "${order.outbox.worker-initial-delay-ms:5000}"
    )
    public void publishEvents() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockExpiredBefore = now.minus(LOCK_TIMEOUT);

        List<OrderOutboxEvent> events = outboxEventMapper.findReadyEvents(
                now,
                lockExpiredBefore,
                BATCH_SIZE
        );

        for (OrderOutboxEvent event : events) {
            publishOne(event);
        }
    }

    private void publishOne(OrderOutboxEvent outboxEvent) {
        LocalDateTime now = LocalDateTime.now();

        int claimed = outboxEventMapper.markProcessing(
                outboxEvent.getId(),
                now,
                now.minus(LOCK_TIMEOUT)
        );

        if (claimed != 1) {
            return;
        }

        try {
            OrderCreatedEvent event = objectMapper.readValue(
                    outboxEvent.getPayload(),
                    OrderCreatedEvent.class
            );

            kafkaTemplate.send(
                    outboxEvent.getTopic(),
                    outboxEvent.getAggregateId(),
                    event
            ).get(10, TimeUnit.SECONDS);

            int updatedRows = outboxEventMapper.markPublished(
                    outboxEvent.getId(),
                    LocalDateTime.now()
            );

            if (updatedRows != 1) {
                throw new IllegalStateException(
                        "Unable to mark outbox event as published: " + outboxEvent.getId()
                );
            }

            log.info(
                    "Published order outbox event: eventId={}, orderId={}, topic={}",
                    outboxEvent.getId(),
                    outboxEvent.getAggregateId(),
                    outboxEvent.getTopic()
            );
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            handleFailure(outboxEvent, exception);
        }
    }

    private void handleFailure(
            OrderOutboxEvent outboxEvent,
            Exception exception
    ) {
        int retryCount = outboxEvent.getRetryCount() + 1;
        LocalDateTime now = LocalDateTime.now();
        String error = exception.getMessage();

        if (error != null && error.length() > 500) {
            error = error.substring(0, 500);
        }

        if (retryCount >= MAX_RETRY_COUNT) {
            outboxEventMapper.markDead(
                    outboxEvent.getId(),
                    retryCount,
                    error,
                    now
            );

            log.error(
                    "Order outbox event reached DEAD status: eventId={}, orderId={}",
                    outboxEvent.getId(),
                    outboxEvent.getAggregateId(),
                    exception
            );
            return;
        }

        LocalDateTime nextRetryAt = now.plusSeconds(5L * retryCount);

        outboxEventMapper.markRetry(
                outboxEvent.getId(),
                retryCount,
                nextRetryAt,
                error,
                now
        );

        log.warn(
                "Scheduled order outbox retry: eventId={}, retryCount={}, nextRetryAt={}",
                outboxEvent.getId(),
                retryCount,
                nextRetryAt
        );
    }
}
