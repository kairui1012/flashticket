package com.flashticket.paymentservice.event;

import com.flashticket.paymentservice.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderTerminatedEvent {
    private String eventId;
    private String orderId;
    private OrderStatus status;
    private LocalDateTime occurredAt;
}
