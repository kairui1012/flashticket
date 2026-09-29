package com.flashticket.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckoutSessionResponse {

    private String paymentId;
    private String orderId;
    private String sessionId;
    private String checkoutUrl;
}
