package com.flashticket.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseInventoryRequest {

    private String ticketId;
    private String userId;
    private Integer reservedStock;
}
