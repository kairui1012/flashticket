package com.flashticket.tikcetservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateTicketRequest {
    private String eventId;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer totalStock;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
}
