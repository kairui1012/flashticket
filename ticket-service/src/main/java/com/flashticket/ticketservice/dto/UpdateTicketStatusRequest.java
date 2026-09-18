package com.flashticket.ticketservice.dto;

import com.flashticket.ticketservice.entity.TicketStatus;
import lombok.Data;

@Data

public class UpdateTicketStatusRequest {
    private TicketStatus status;
}
