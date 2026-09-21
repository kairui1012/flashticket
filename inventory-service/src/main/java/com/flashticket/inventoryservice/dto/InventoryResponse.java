package com.flashticket.inventoryservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryResponse {

    private String id;
    private String ticketId;
    private Integer totalStock;
    private Integer availableStock;
    private Integer reservedStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
