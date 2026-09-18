package com.flashticket.ticketservice.mapper;

import com.flashticket.ticketservice.dto.UpdateTicketRequest;
import com.flashticket.ticketservice.entity.Ticket;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TicketMapper {

    void createTicket(Ticket ticket);

    Ticket findById(String id);

    List<Ticket> findByEventId(String eventId);

    int updateTicketStatusToCancelById(
            @Param("id") String id
    );
    int updateTicketById(
            @Param("id") String id,
            @Param("request") UpdateTicketRequest request
    );

}
