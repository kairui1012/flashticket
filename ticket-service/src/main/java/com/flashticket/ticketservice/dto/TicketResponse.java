package com.flashticket.ticketservice.dto;

import com.flashticket.ticketservice.entity.TicketStatus;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TicketResponse implements Serializable {
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
