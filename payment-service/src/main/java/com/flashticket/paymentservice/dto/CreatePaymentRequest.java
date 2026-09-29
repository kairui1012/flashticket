package com.flashticket.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePaymentRequest {

    @NotBlank(message = "Order ID is required")
    private String orderId;
}
