package com.flashticket.inventoryservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReserveStockRequest {
    @NotBlank(message = "Ticket ID is required")
    private String ticketId;

    @NotNull(message = "Reserved stock is required")
    @Positive(message = "Reserved stock must be greater than zero")
    private Integer reservedStock;
    private LocalDateTime updatedAt;
}
