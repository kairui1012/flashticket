package com.flashticket.paymentservice.service;

import com.flashticket.paymentservice.config.StripeProperties;
import com.flashticket.paymentservice.client.OrderClient;
import com.flashticket.paymentservice.dto.CheckoutSessionResponse;
import com.flashticket.paymentservice.dto.CreatePaymentRequest;
import com.flashticket.paymentservice.dto.OrderResponse;
import com.flashticket.paymentservice.dto.PaymentResponse;
import com.flashticket.paymentservice.dto.UpdatePaymentRequest;
import com.flashticket.paymentservice.entity.OrderStatus;
import com.flashticket.paymentservice.entity.Payment;
import com.flashticket.paymentservice.entity.PaymentStatus;
import com.flashticket.paymentservice.mapper.PaymentMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor

public class PaymentService {

    private static final Duration PAYMENT_CACHE_TTL = Duration.ofMinutes(5);
    private final OrderClient orderClient;
    private final PaymentMapper paymentMapper;
    private final RedisTemplate<String,PaymentResponse> redisTemplate;
    private final StripeProperties stripeProperties;


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

    public CheckoutSessionResponse createCheckoutSession(String paymentId) {
        Payment payment = paymentMapper.findById(paymentId);

        if (payment == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payment not found: " + paymentId
            );
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a pending payment can create a checkout session"
            );
        }

        OrderResponse orderResponse = orderClient.getOrderById(payment.getOrderId());

        if (orderResponse == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Order not found: " + payment.getOrderId()
            );
        }

        if (orderResponse.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a pending payment order can create a checkout session"
            );
        }

        if (orderResponse.getExpiresAt() == null
                || !orderResponse.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The payment deadline has passed"
            );
        }

        if (orderResponse.getTotalAmount() == null
                || orderResponse.getTotalAmount().compareTo(payment.getAmount()) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The payment amount no longer matches the order total"
            );
        }

        if (stripeProperties.getSecretKey() == null
                || stripeProperties.getSecretKey().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stripe is not configured"
            );
        }

        long amountInMinorUnits;

        try {
            amountInMinorUnits = payment.getAmount()
                    .movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Payment amount cannot be converted to Stripe minor units",
                    exception
            );
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(stripeProperties.getSuccessUrl())
                .setCancelUrl(stripeProperties.getCancelUrl())
                .setClientReferenceId(payment.getId())
                .putMetadata("paymentId", payment.getId())
                .putMetadata("orderId", payment.getOrderId())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(stripeProperties.getCurrency())
                                                .setUnitAmount(amountInMinorUnits)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("FlashTicket order " + payment.getOrderId())
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(stripeProperties.getSecretKey())
                .setIdempotencyKey("checkout-session:" + payment.getId())
                .build();

        try {
            Session session = Session.create(params, requestOptions);

            if (session.getUrl() == null || session.getUrl().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Stripe did not return a checkout URL"
                );
            }

            return new CheckoutSessionResponse(
                    payment.getId(),
                    payment.getOrderId(),
                    session.getId(),
                    session.getUrl()
            );
        } catch (StripeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to create the Stripe checkout session",
                    exception
            );
        }
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
