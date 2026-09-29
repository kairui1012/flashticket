package com.flashticket.paymentservice.client;

import com.flashticket.paymentservice.dto.OrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", url = "${order-service.base-url}")
public interface OrderClient {

    @GetMapping("/api/v1/orders/{orderId}")
    OrderResponse getOrderById(
            @PathVariable("orderId") String orderId
    );
}
