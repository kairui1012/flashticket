package com.flashticket.tikcetservice.service;

import com.flashticket.tikcetservice.dto.CreateTicketRequest;
import com.flashticket.tikcetservice.dto.TicketResponse;
import com.flashticket.tikcetservice.dto.UpdateTicketRequest;
import com.flashticket.tikcetservice.dto.UpdateTicketStatusRequest;
import com.flashticket.tikcetservice.entity.Ticket;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Data
@RequiredArgsConstructor
@Slf4j

public class TicketService {

    public TicketResponse getTicketById(String id) {
        
    }

    public List<TicketResponse> getTicketsByEventId(String eventId) {
    }

    public TicketResponse createTicket(@Valid CreateTicketRequest request) {

    }

    public TicketResponse updateTicketById(String id, @Valid UpdateTicketRequest request) {
    }

    public TicketResponse updateTicketStatusById(String id, @Valid UpdateTicketStatusRequest request) {
    }

    private TicketResponse mapToResponse(Ticket ticket) {

        TicketResponse response = new TicketResponse();

        response.setId(ticket.getId());
        response.setEventId(ticket.getEventId());
        response.setName(ticket.getName());
        response.setDescription(ticket.getDescription());
        response.setPrice(ticket.getPrice());
        response.setTotalStock(ticket.getTotalStock());
        response.setSaleStartTime(ticket.getSaleStartTime());
        response.setSaleEndTime(ticket.getSaleEndTime());
        response.setStatus(ticket.getStatus());
        response.setCreatedAt(ticket.getCreatedAt());

        return response;
    }
}
