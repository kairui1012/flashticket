package com.flashticket.orderservice.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TicketPriceResponse {
    private String id;
    private BigDecimal price;
}
