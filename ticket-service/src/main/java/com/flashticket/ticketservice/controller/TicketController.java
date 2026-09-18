package com.flashticket.ticketservice.controller;

import com.flashticket.ticketservice.dto.CreateTicketRequest;
import com.flashticket.ticketservice.dto.TicketResponse;
import com.flashticket.ticketservice.dto.UpdateTicketRequest;
import com.flashticket.ticketservice.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;


    // GET /api/v1/tickets/T001
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicketById(
            @PathVariable String id
    ) {
        return ResponseEntity.ok(
                ticketService.getTicketById(id)
        );
    }


    // GET /api/v1/tickets/event/E001
    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<TicketResponse>> getTicketsByEventId(
            @PathVariable String eventId
    ) {
        return ResponseEntity.ok(
                ticketService.getTicketsByEventId(eventId)
        );
    }


    // POST /api/v1/tickets
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ticketService.createTicket(request));
    }


    // PUT /api/v1/tickets/T001
    @PutMapping("/{id}")
    public ResponseEntity<TicketResponse> updateTicketById(
            @PathVariable String id,
            @Valid @RequestBody UpdateTicketRequest request
    ) {

        return ResponseEntity.ok(
                ticketService.updateTicketById(id, request)
        );
    }


    // PATCH /api/v1/tickets/T001/status/cancel
    @PatchMapping("/{id}/status/cancel")
    public ResponseEntity<TicketResponse> updateTicketStatusToCancelById(
            @PathVariable String id
    ) {
        return ResponseEntity.ok(
                ticketService.updateTicketStatusToCancelById(id)
        );
    }
}