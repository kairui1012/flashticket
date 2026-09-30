package com.flashticket.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentSucceededEvent {
    private String eventId;
    private String paymentId;
    private String orderId;
    private String providerTransactionId;
    private BigDecimal amount;
    private LocalDateTime paidAt;
    private LocalDateTime occurredAt;
}
