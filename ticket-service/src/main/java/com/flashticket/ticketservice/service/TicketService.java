package com.flashticket.ticketservice.service;

import com.flashticket.ticketservice.dto.CreateTicketRequest;
import com.flashticket.ticketservice.dto.TicketResponse;
import com.flashticket.ticketservice.dto.UpdateTicketRequest;
import com.flashticket.ticketservice.entity.Ticket;
import com.flashticket.ticketservice.entity.TicketStatus;
import com.flashticket.ticketservice.exception.TicketBusinessException;
import com.flashticket.ticketservice.mapper.TicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class TicketService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final TicketMapper ticketMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public TicketResponse getTicketById(String id) {

        String key = ticketCacheKey(id);
        Object cached = redisTemplate.opsForValue().get(key);

        // Cache hit: return the ticket directly without querying MySQL.
        if (cached instanceof TicketResponse ticketResponse) {
            return ticketResponse;
        }

        Ticket ticket = ticketMapper.findById(id);

        if (ticket == null) {
            throw ticketNotFound(id);
        }

        TicketResponse response = mapToResponse(ticket);


        // Cache miss: save the database result for later requests.
        redisTemplate.opsForValue().set(key, response, CACHE_TTL);

        return response;
    }

    public List<TicketResponse> getTicketsByEventId(String eventId) {

        String key = eventTicketsCacheKey(eventId);
        Object cached = redisTemplate.opsForValue().get(key);

        // This cache entry contains a List<TicketResponse>, not one ticket.
        if (cached instanceof List<?> cachedTickets) {
            return cachedTickets.stream()
                    .filter(TicketResponse.class::isInstance)
                    .map(TicketResponse.class::cast)
                    .toList();
        }

        List<TicketResponse> tickets = ticketMapper.findByEventId(eventId)
                .stream()
                .map(this::mapToResponse)
                .toList();

        redisTemplate.opsForValue().set(key, tickets, CACHE_TTL);
        return tickets;
    }

    public TicketResponse createTicket(CreateTicketRequest request) {

        validateSalePeriod(
                request.getSaleStartTime(),
                request.getSaleEndTime()
        );

        Ticket ticket = new Ticket();

        ticket.setId(UUID.randomUUID().toString());
        ticket.setEventId(request.getEventId());
        ticket.setName(request.getName());
        ticket.setDescription(request.getDescription());
        ticket.setPrice(request.getPrice());
        ticket.setTotalStock(request.getTotalStock());
        ticket.setSaleStartTime(request.getSaleStartTime());
        ticket.setSaleEndTime(request.getSaleEndTime());
        ticket.setStatus(TicketStatus.DRAFT);
        ticket.setCreatedAt(LocalDateTime.now());
        ticketMapper.createTicket(ticket);

        // A newly created ticket changes the cached list for its event.
        redisTemplate.delete(eventTicketsCacheKey(ticket.getEventId()));
        return mapToResponse(ticket);
    }


    public TicketResponse updateTicketById(String id, UpdateTicketRequest request) {

        Ticket existingTicket = ticketMapper.findById(id);
        if (existingTicket == null) {
            throw ticketNotFound(id);
        }

        if (request.getName() == null
                && request.getDescription() == null
                && request.getPrice() == null
                && request.getSaleStartTime() == null
                && request.getSaleEndTime() == null) {
            throw badRequest("At least one ticket field must be provided");
        }

        LocalDateTime saleStartTime = request.getSaleStartTime() != null
                ? request.getSaleStartTime()
                : existingTicket.getSaleStartTime();
        LocalDateTime saleEndTime = request.getSaleEndTime() != null
                ? request.getSaleEndTime()
                : existingTicket.getSaleEndTime();

        if (saleStartTime.isBefore(LocalDateTime.now().plusMinutes(30))) {
            throw badRequest(
                    "Ticket cannot be updated within 30 minutes before sale starts"
            );
        }

        validateSalePeriod(saleStartTime, saleEndTime);

        int updatedRows = ticketMapper.updateTicketById(id, request);

        if (updatedRows == 0) {
            throw ticketNotFound(id);
        }

        Ticket updatedTicket = ticketMapper.findById(id);

        TicketResponse response = mapToResponse(updatedTicket);

        // Keep the single-ticket cache fresh and invalidate the event list.
        redisTemplate.opsForValue().set(ticketCacheKey(id), response, CACHE_TTL);
        redisTemplate.delete(eventTicketsCacheKey(updatedTicket.getEventId()));

        return response;
    }

    public TicketResponse updateTicketStatusToCancelById(
            String id
    ) {

        Ticket existingTicket = ticketMapper.findById(id);
        if (existingTicket == null) {
            throw ticketNotFound(id);
        }
        if (existingTicket.getStatus() == TicketStatus.CANCELLED
                || existingTicket.getStatus() == TicketStatus.ENDED) {
            throw new TicketBusinessException(
                    HttpStatus.CONFLICT,
                    "Ticket cannot be cancelled in its current status"
            );
        }

        int updatedRows = ticketMapper.updateTicketStatusToCancelById(
                id
        );

        if (updatedRows == 0) {
            throw new TicketBusinessException(
                    HttpStatus.CONFLICT,
                    "Ticket status changed before it could be cancelled"
            );
        }

        Ticket updatedTicket = ticketMapper.findById(id);

        TicketResponse response = mapToResponse(updatedTicket);

        // Cancellation changes both the ticket details and its event list.
        redisTemplate.opsForValue().set(ticketCacheKey(id), response, CACHE_TTL);
        redisTemplate.delete(eventTicketsCacheKey(updatedTicket.getEventId()));

        return response;
    }

    private void validateSalePeriod(
            LocalDateTime saleStartTime,
            LocalDateTime saleEndTime
    ) {
        if (!saleEndTime.isAfter(saleStartTime)) {
            throw badRequest("Sale end time must be after sale start time");
        }
    }

    private TicketBusinessException ticketNotFound(String id) {
        return new TicketBusinessException(
                HttpStatus.NOT_FOUND,
                "Ticket not found: " + id
        );
    }

    private TicketBusinessException badRequest(String message) {
        return new TicketBusinessException(HttpStatus.BAD_REQUEST, message);
    }

    private String ticketCacheKey(String id) {
        return "ticket:" + id;
    }

    private String eventTicketsCacheKey(String eventId) {
        return "tickets:event:" + eventId;
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
