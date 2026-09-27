package com.flashticket.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TicketPriceResponse {

    @NotBlank(message = "Ticket ID is required")
    private String id;

    @NotNull(message = "Ticket price is required")
    @PositiveOrZero(message = "Ticket price must not be negative")
    private BigDecimal price;
}
