package com.flashticket.inventoryservice.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Inventory {

    private String id;
    private String ticketId;

    private Integer totalStock;
    private Integer availableStock;
    private Integer reservedStock;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}