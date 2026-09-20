package com.flashticket.inventoryservice.client;

import com.flashticket.inventoryservice.dto.TicketScheduleResponse;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ticket-service", url = "${ticket-service.base-url}")
public interface TicketClient {

    @GetMapping("/api/v1/tickets/{ticketId}")
    TicketScheduleResponse getTicketById(@PathVariable("ticketId") String ticketId);

    default boolean existsById(String ticketId) {
        try {
            getTicketById(ticketId);
            return true;
        } catch (FeignException.NotFound exception) {
            return false;
        }
    }
}
