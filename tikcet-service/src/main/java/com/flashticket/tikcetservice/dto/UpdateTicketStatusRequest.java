package com.flashticket.tikcetservice.dto;

import com.flashticket.tikcetservice.entity.TicketStatus;
import lombok.Data;

@Data

public class UpdateTicketStatusRequest {
    private TicketStatus status;
}
