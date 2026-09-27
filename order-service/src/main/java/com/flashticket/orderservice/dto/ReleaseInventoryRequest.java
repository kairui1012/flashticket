package com.flashticket.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseInventoryRequest {

    @NotBlank(message = "Ticket ID is required")
    private String ticketId;

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Reserved stock is required")
    @Positive(message = "Reserved stock must be greater than zero")
    private Integer reservedStock;

    @NotBlank(message = "Release ID is required")
    private String releaseId;

    @NotBlank(message = "Order ID is required")
    private String orderId;
}
