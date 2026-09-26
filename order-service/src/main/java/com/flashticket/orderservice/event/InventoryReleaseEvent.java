package com.flashticket.orderservice.event;

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
}
