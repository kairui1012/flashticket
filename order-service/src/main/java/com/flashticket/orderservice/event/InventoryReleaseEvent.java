package com.flashticket.orderservice.event;

import com.flashticket.orderservice.entity.EventType;
import com.flashticket.orderservice.entity.OrderOutboxStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryReleaseEvent {

    // eventId -> Ensures idempotency so duplicate Kafka delivery does not release MySQL stock twice.
    private String eventId;

    // ticketId -> Identifies which ticket inventory the consumer must update.
    private String ticketId;

    // userId -> Identifies whose reservation is being released.
    private String userId;

    // quantity -> Records how many reserved tickets must be returned to available stock.
    private Integer quantity;

    // occurredAt -> Records when the stock release event occurred.
    private LocalDateTime occurredAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessedEvent {
        private String eventId;
        private EventType eventType;
        private LocalDateTime processedAt;
    }

    @Data
    public static class OrderOutboxEvent {
        private String id;
        private String aggregateId;
        private String topic;
        private String eventType;
        private String payload;
        private OrderOutboxStatus status;
        private Integer retryCount;
        private LocalDateTime nextRetryAt;
        private LocalDateTime lockedAt;
        private String lastError;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
