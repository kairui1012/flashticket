package com.flashticket.paymentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymentRequest {

    @NotBlank(message = "Order ID is required")
    private String orderId;

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "Payment amount must be greater than zero")
    private BigDecimal amount;
}
