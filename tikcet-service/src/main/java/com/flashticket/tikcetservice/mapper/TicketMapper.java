package com.flashticket.tikcetservice.mapper;

import com.flashticket.tikcetservice.dto.UpdateTicketRequest;
import com.flashticket.tikcetservice.entity.Ticket;
import com.flashticket.tikcetservice.entity.TicketStatus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketMapper {

    void createTicket(Ticket ticket);

    Ticket findById(String id);

    Ticket findByEventId(String eventId);

    void updateTicketStatusById(
            @Param("id") String id,
            @Param("status") TicketStatus status
    );
    void updateTicketById(
            @Param("id") String id,
            @Param("request") UpdateTicketRequest request
    );

}
