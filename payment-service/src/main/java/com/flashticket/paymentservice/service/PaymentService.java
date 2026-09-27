package com.flashticket.paymentservice.service;

import com.flashticket.paymentservice.dto.CreatePaymentRequest;
import com.flashticket.paymentservice.dto.PaymentResponse;
import com.flashticket.paymentservice.dto.UpdatePaymentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

    public PaymentResponse createPayment(CreatePaymentRequest request) {
        throw notImplemented();
    }

    public PaymentResponse getPaymentById(String paymentId) {
        throw notImplemented();
    }

    public PaymentResponse getPaymentByOrderId(String orderId) {
        throw notImplemented();
    }

    public PaymentResponse updatePayment(
            String paymentId,
            UpdatePaymentRequest request
    ) {
        throw notImplemented();
    }

    private ResponseStatusException notImplemented() {
        return new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Payment processing has not been implemented yet"
        );
    }
}
