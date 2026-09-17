package com.flashticket.tikcetservice.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Ticket
{
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer totalStock;
    private Integer availableStock;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private String status;
    private LocalDateTime createdAt;
}
