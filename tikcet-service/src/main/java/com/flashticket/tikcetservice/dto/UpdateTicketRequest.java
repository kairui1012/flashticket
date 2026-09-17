package com.flashticket.tikcetservice.dto;

import com.flashticket.tikcetservice.entity.TicketStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data

public class UpdateTicketRequest {
    private String name;
    private String description;
    private BigDecimal price;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
}