package com.flashticket.orderservice.entity;

public enum OrderOutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    RETRY,
    DEAD
}
