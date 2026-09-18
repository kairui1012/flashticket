package com.flashticket.ticketservice.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Ticket {

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