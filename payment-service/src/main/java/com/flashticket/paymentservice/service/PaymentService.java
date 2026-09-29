package com.flashticket.paymentservice.service;

import com.flashticket.paymentservice.client.OrderClient;
import com.flashticket.paymentservice.dto.CreatePaymentRequest;
import com.flashticket.paymentservice.dto.OrderResponse;
import com.flashticket.paymentservice.dto.PaymentResponse;
import com.flashticket.paymentservice.dto.UpdatePaymentRequest;
import com.flashticket.paymentservice.entity.OrderStatus;
import com.flashticket.paymentservice.entity.Payment;
import com.flashticket.paymentservice.entity.PaymentStatus;
import com.flashticket.paymentservice.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor

public class PaymentService {

    private static final Duration PAYMENT_CACHE_TTL = Duration.ofMinutes(5);
    private final OrderClient orderClient;
    private final PaymentMapper paymentMapper;
    private final RedisTemplate<String,PaymentResponse> redisTemplate;


    public PaymentResponse createPayment(CreatePaymentRequest request) {
        PaymentResponse cachedOfOrderId = redisTemplate.opsForValue().get(
                paymentCacheKeyOfOrderId(request.getOrderId())
        );

        if (cachedOfOrderId != null) {
            return cachedOfOrderId;
        }

        Payment existingPayment = paymentMapper.findByOrderId(request.getOrderId());

        if (existingPayment != null) {
            PaymentResponse existingResponse = mapToResponse(existingPayment);
            cachePayment(existingResponse);
            return existingResponse;
        }

        OrderResponse orderResponse = orderClient.getOrderById(request.getOrderId());

        if (orderResponse == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Order not found: " + request.getOrderId()
            );
        }

        if (orderResponse.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a pending payment order can create a payment"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (!orderResponse.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The payment deadline has passed"
            );
        }

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setOrderId(orderResponse.getId());
        payment.setUserId(orderResponse.getUserId());
        payment.setAmount(orderResponse.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProviderTransactionId(null);
        payment.setFailureReason(null);
        payment.setCreatedAt(now);
        payment.setPaidAt(null);
        payment.setUpdatedAt(now);

        try {
            int insertedRows = paymentMapper.insert(payment);

            if (insertedRows != 1) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unable to create the payment"
                );
            }
        } catch (DuplicateKeyException exception) {
            Payment concurrentPayment = paymentMapper.findByOrderId(request.getOrderId());

            if (concurrentPayment == null) {
                throw exception;
            }

            PaymentResponse concurrentResponse = mapToResponse(concurrentPayment);
            cachePayment(concurrentResponse);
            return concurrentResponse;
        }

        PaymentResponse response = mapToResponse(payment);
        cachePayment(response);
        return response;
    }

    public PaymentResponse getPaymentById(String paymentId) {
        PaymentResponse cached = redisTemplate.opsForValue().get(
                paymentCacheKey(paymentId)
        );

        if (cached!=null){
            return cached;
        }

        Payment payment = paymentMapper.findById(paymentId);

        if (payment == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payment not found: " + paymentId
            );
        }

        PaymentResponse response = mapToResponse(payment);

        redisTemplate.opsForValue().set(
                paymentCacheKey(paymentId),
                response,
                PAYMENT_CACHE_TTL
        );

        return response;
    }

    public PaymentResponse getPaymentByOrderId(String orderId) {
        PaymentResponse cached = redisTemplate.opsForValue().get(
                paymentCacheKeyOfOrderId(orderId)
        );

        if (cached!=null){
            return cached;
        }

        Payment payment = paymentMapper.findByOrderId(orderId);

        if (payment == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payment not found: " + orderId
            );
        }

        PaymentResponse response = mapToResponse(payment);

        redisTemplate.opsForValue().set(
                paymentCacheKeyOfOrderId(orderId),
                response,
                PAYMENT_CACHE_TTL
        );

        return response;
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

    private PaymentResponse mapToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();

        response.setId(payment.getId());
        response.setOrderId(payment.getOrderId());
        response.setUserId(payment.getUserId());
        response.setAmount(payment.getAmount());
        response.setStatus(payment.getStatus());
        response.setProviderTransactionId(payment.getProviderTransactionId());
        response.setFailureReason(payment.getFailureReason());
        response.setCreatedAt(payment.getCreatedAt());
        response.setPaidAt(payment.getPaidAt());
        response.setUpdatedAt(payment.getUpdatedAt());

        return response;
    }

    private String paymentCacheKey(String paymentId) {
        return "payment:" + paymentId;
    }

    private String paymentCacheKeyOfOrderId(String orderId) {
        return "payment:order:" + orderId;
    }

    public PaymentResponse paymentCheckout(String paymentId) {
        
    }

    private void cachePayment(PaymentResponse response) {
        redisTemplate.opsForValue().set(
                paymentCacheKey(response.getId()),
                response,
                PAYMENT_CACHE_TTL
        );

        redisTemplate.opsForValue().set(
                paymentCacheKeyOfOrderId(response.getOrderId()),
                response,
                PAYMENT_CACHE_TTL
        );
    }


}
