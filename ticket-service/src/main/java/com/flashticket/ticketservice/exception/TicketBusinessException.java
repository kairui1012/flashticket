package com.flashticket.ticketservice.exception;

import org.springframework.http.HttpStatus;

public class TicketBusinessException extends RuntimeException {

    private final HttpStatus status;

    public TicketBusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
