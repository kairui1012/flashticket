package com.flashticket.orderservice.client;

import com.flashticket.orderservice.dto.TicketPriceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ticket-service", url = "${ticket-service.base-url}")
public interface TicketClient {

    @GetMapping("/api/v1/tickets/{ticketId}")
    TicketPriceResponse getTicketById(@PathVariable("ticketId") String ticketId);
}
