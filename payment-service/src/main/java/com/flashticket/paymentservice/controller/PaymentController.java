package com.flashticket.paymentservice.controller;

import com.flashticket.paymentservice.dto.CreatePaymentRequest;
import com.flashticket.paymentservice.dto.PaymentResponse;
import com.flashticket.paymentservice.dto.UpdatePaymentRequest;
import com.flashticket.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // Creates one payment record for an order.
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(paymentService.createPayment(request));
    }

    // Returns one payment by its payment ID.
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentById(
            @PathVariable String paymentId
    ) {
        return ResponseEntity.ok(
                paymentService.getPaymentById(paymentId)
        );
    }

    // Returns the payment associated with one order.
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(
            @PathVariable String orderId
    ) {
        return ResponseEntity.ok(
                paymentService.getPaymentByOrderId(orderId)
        );
    }

    // Updates the status and provider details of one payment.
    @PatchMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> updatePayment(
            @PathVariable String paymentId,
            @Valid @RequestBody UpdatePaymentRequest request
    ) {
        return ResponseEntity.ok(
                paymentService.updatePayment(paymentId, request)
        );
    }
}
