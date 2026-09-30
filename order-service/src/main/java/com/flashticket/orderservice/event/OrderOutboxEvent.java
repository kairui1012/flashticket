package com.flashticket.orderservice.event;

import com.flashticket.orderservice.entity.OrderOutboxStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderOutboxEvent {
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
