package com.flashticket.ticketservice.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data

public class UpdateTicketRequest {
    private String name;
    private String description;

    @Positive(message = "Price must be greater than zero")
    private BigDecimal price;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
}
