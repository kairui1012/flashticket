package com.flashticket.ticketservice.exception;

public record ApiError(int status, String error, String message) {
}
