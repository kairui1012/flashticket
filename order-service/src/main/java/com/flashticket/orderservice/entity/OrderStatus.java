package com.flashticket.orderservice.entity;

public enum OrderStatus {
    // The order has been created and is waiting for payment.
    PENDING_PAYMENT,

    // Payment has completed successfully.
    PAID,

    // The user or system cancelled the order before payment.
    CANCELLED,

    // The payment deadline passed and the reserved inventory must be released.
    EXPIRED,

    // A previously paid order has been refunded.
    REFUNDED
}
