package com.flashticket.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryReservedEvent {

    // eventId -> Ensures idempotency so duplicate Kafka delivery does not deduct MySQL stock twice.
    private String eventId;

    // ticketId -> Identifies which ticket inventory the consumer must update.
    private String ticketId;

    // userId -> Identifies who reserved the ticket for order processing and auditing.
    private String userId;

    // quantity -> Records how many tickets were reserved.
    private Integer quantity;

    // occurredAt -> Records when the reservation event occurred.
    private LocalDateTime occurredAt;
}
