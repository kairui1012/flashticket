package com.flashticket.inventoryservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InsertInventoryRequest {

    @NotBlank(message = "Ticket ID is required")
    private String ticketId;

    @NotNull(message = "Total stock is required")
    @PositiveOrZero(message = "Total stock must not be negative")
    private Integer totalStock;

    private LocalDateTime createdAt;
}
