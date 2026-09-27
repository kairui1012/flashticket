package com.flashticket.paymentservice.dto;

import com.flashticket.paymentservice.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePaymentRequest {

    @NotNull(message = "Payment status is required")
    private PaymentStatus status;

    private String providerTransactionId;
    private String failureReason;
}
