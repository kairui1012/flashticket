package com.flashticket.orderservice.controller;

import com.flashticket.orderservice.dto.CreateOrderRequest;
import com.flashticket.orderservice.dto.OrderResponse;
import com.flashticket.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(orderService.createOrder(request));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable String orderId
    ) {
        return ResponseEntity.ok(
                orderService.getOrderById(orderId)
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByUserId(
            @PathVariable String userId
    ) {
        return ResponseEntity.ok(
                orderService.getOrdersByUserId(userId)
        );
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable String orderId
    ) {
        return ResponseEntity.ok(
                orderService.cancelOrder(orderId)
        );
    }

    @PostMapping("/{orderId}/paid")
    public ResponseEntity<OrderResponse> markAsPaid(
            @PathVariable String orderId
    ) {
        return ResponseEntity.ok(
                orderService.markAsPaid(orderId)
        );
    }
}
