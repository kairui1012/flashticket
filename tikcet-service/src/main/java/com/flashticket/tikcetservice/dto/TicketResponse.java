package com.flashticket.tikcetservice.dto;

import com.flashticket.tikcetservice.entity.TicketStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TicketResponse {
    private String id;
    private String eventId;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer totalStock;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private TicketStatus status;
    private LocalDateTime createdAt;
}
