package com.flashticket.paymentservice.dto;

import com.flashticket.paymentservice.entity.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderResponse {

    @NotBlank(message = "Order ID is required")
    private String id;

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Ticket ID is required")
    private String ticketId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    private Integer quantity;

    @NotNull(message = "Unit price is required")
    @PositiveOrZero(message = "Unit price must not be negative")
    private BigDecimal unitPrice;

    @NotNull(message = "Total amount is required")
    @PositiveOrZero(message = "Total amount must not be negative")
    private BigDecimal totalAmount;

    @NotNull(message = "Order status is required")
    private OrderStatus status;

    @NotNull(message = "Creation time is required")
    private LocalDateTime createdAt;

    @NotNull(message = "Expiration time is required")
    private LocalDateTime expiresAt;

    private LocalDateTime paidAt;
}
