package com.flashticket.paymentservice.entity;

public enum PaymentStatus {
    // The payment record has been created and is waiting to be processed.
    PENDING,

    // The payment provider is currently processing the payment.
    PROCESSING,

    // The payment completed successfully.
    SUCCEEDED,

    // The payment attempt failed.
    FAILED,

    // The payment was cancelled before completion.
    CANCELLED,

    // The order payment deadline passed before completion.
    EXPIRED,

    // A previously successful payment was refunded.
    REFUNDED
}
