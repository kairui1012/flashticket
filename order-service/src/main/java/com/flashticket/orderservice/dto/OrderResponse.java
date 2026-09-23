package com.flashticket.orderservice.dto;

import com.flashticket.orderservice.entity.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderResponse {

    private String id;
    private String userId;
    private String ticketId;
    private Integer quantity;

    private BigDecimal unitPrice;
    private BigDecimal totalAmount;

    private OrderStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
}