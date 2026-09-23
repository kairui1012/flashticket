package com.flashticket.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderRequest {

    @NotBlank
    private String userId;

    @NotBlank
    private String ticketId;

    @NotNull
    @Min(1)
    private Integer quantity;
}
