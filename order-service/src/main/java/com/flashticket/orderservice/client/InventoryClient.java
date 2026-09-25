package com.flashticket.orderservice.client;

import com.flashticket.orderservice.dto.ReleaseInventoryRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "inventory-service",
        url = "${inventory-service.base-url}"
)
public interface InventoryClient {

    @PostMapping("/api/v1/inventory/release")
    void releaseStock(@RequestBody ReleaseInventoryRequest request);
}
