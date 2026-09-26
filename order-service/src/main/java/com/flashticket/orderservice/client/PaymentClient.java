package com.flashticket.orderservice.client;

import com.flashticket.orderservice.dto.UpdatePaymentRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service" ,url =" ${payment-service.base-url}" )
public interface PaymentClient {
    @PutMapping("/api/v1/payment/update")
    void releaseStock(@RequestBody UpdatePaymentRequest request);
}
