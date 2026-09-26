package com.flashticket.orderservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReleaseTask {

    // Identifies this inventory release task.
    private String id;

    // Identifies the order whose reserved inventory must be released.
    private String orderId;

    // Provides an idempotency key for the inventory release operation.
    private String releaseId;

    private String ticketId;
    private String userId;
    private Integer quantity;

    // Describes why the reserved inventory must be released.
    private String reason;

    // Tracks the current processing state of the release task.
    private InventoryReleaseTaskStatus status;

    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lockedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
