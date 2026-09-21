package com.flashticket.inventoryservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TicketScheduleResponse {
    private String id;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private String status;
}
