package com.flashticket.paymentservice.service;

import com.flashticket.paymentservice.client.OrderClient;
import com.flashticket.paymentservice.dto.CheckoutSessionResponse;
import com.flashticket.paymentservice.dto.CreatePaymentRequest;
import com.flashticket.paymentservice.dto.OrderResponse;
import com.flashticket.paymentservice.dto.PaymentResponse;
import com.flashticket.paymentservice.entity.OrderStatus;
import com.flashticket.paymentservice.entity.Payment;
import com.flashticket.paymentservice.entity.PaymentStatus;
import com.flashticket.paymentservice.event.PaymentSucceededEvent;
import com.flashticket.paymentservice.mapper.PaymentMapper;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.flashticket.paymentservice.config.StripeProperties;
import com.stripe.param.checkout.SessionCreateParams;


import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor

public class PaymentService {

    private static final Duration PAYMENT_CACHE_TTL = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final KafkaTemplate<String, PaymentSucceededEvent> kafkaTemplate;
    private static final String PAYMENT_SUCCESS_TOPIC = "payment.succeeded";
    private final OrderClient orderClient;
    private final PaymentMapper paymentMapper;
    private final RedisTemplate<String,PaymentResponse> redisTemplate;
    private final StripeProperties stripeProperties;


    // Creates one payment per order and caches the result.
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

    // Reads by payment ID using Redis as a cache.
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

    // Reads by order ID using Redis as a cache.
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

    /**
     * Atomically marks a pending payment as succeeded and publishes the result.
     * Repeated Stripe webhooks are handled idempotently and are not republished.
     */
    public void markPaymentSucceeded(
            String paymentId,
            String orderId,
            String providerTransactionId,
            LocalDateTime paidAt
    ) {
        if (paymentId == null || paymentId.isBlank()
                || orderId == null || orderId.isBlank()
                || providerTransactionId == null || providerTransactionId.isBlank()
                || paidAt == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment success data is incomplete"
            );
        }

        int updatedRows;

        // MySQL is the source of truth for the PENDING -> SUCCEEDED transition.
        try {
            updatedRows = paymentMapper.markSucceeded(
                    paymentId,
                    orderId,
                    providerTransactionId,
                    paidAt
            );
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Provider transaction is already linked to another payment",
                    exception
            );
        }

        // Reload the persisted state instead of relying on a potentially stale cache.
        Payment payment = paymentMapper.findById(paymentId);

        if (payment == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payment not found: " + paymentId
            );
        }

        if (updatedRows == 1) {
            PaymentResponse response = mapToResponse(payment);
            cachePayment(response);

            // Publish only when this request performs the state transition.
            PaymentSucceededEvent paymentSucceededEvent = new PaymentSucceededEvent();
            paymentSucceededEvent.setEventId(UUID.randomUUID().toString());
            paymentSucceededEvent.setPaymentId(payment.getId());
            paymentSucceededEvent.setOrderId(payment.getOrderId());
            paymentSucceededEvent.setProviderTransactionId(payment.getProviderTransactionId());
            paymentSucceededEvent.setAmount(payment.getAmount());
            paymentSucceededEvent.setPaidAt(payment.getPaidAt());
            paymentSucceededEvent.setOccurredAt(LocalDateTime.now());

            // The order ID keeps events for the same order on the same Kafka partition.
            kafkaTemplate.send(
                    PAYMENT_SUCCESS_TOPIC,
                    payment.getOrderId(),
                    paymentSucceededEvent
            );

            log.info(
                    "Payment marked as succeeded: paymentId={}, orderId={}, providerTransactionId={}",
                    paymentId,
                    orderId,
                    providerTransactionId
            );
            return;
        }

        if (!orderId.equals(payment.getOrderId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment does not belong to order: " + orderId
            );
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            if (providerTransactionId.equals(payment.getProviderTransactionId())) {
                PaymentResponse response = mapToResponse(payment);
                cachePayment(response);

                log.info(
                        "Payment success was already processed: paymentId={}, providerTransactionId={}",
                        paymentId,
                        providerTransactionId
                );
                return;
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment already succeeded with a different provider transaction"
            );
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Payment cannot succeed from status: " + payment.getStatus()
        );
    }

    private String paymentCacheKey(String paymentId) {
        return "payment:" + paymentId;
    }

    private String paymentCacheKeyOfOrderId(String orderId) {
        return "payment:order:" + orderId;
    }

    // Validates the payment and creates a Stripe-hosted checkout session.
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

        if (payment.getAmount() == null
                || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment amount must be greater than zero"
            );
        }

        long amountInMinorUnits;

        try {
            amountInMinorUnits = payment.getAmount()
                    .movePointRight(2)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Payment amount cannot be converted to minor units",
                    exception
            );
        }

        SessionCreateParams.LineItem.PriceData.ProductData productData =
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName("FlashTicket order " + payment.getOrderId())
                        .setDescription("amount:" + payment.getAmount())
                        .build();

        SessionCreateParams.LineItem.PriceData priceData =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(stripeProperties.getCurrency())
                        .setUnitAmount(amountInMinorUnits)
                        .setProductData(productData)
                        .build();

        SessionCreateParams.LineItem lineItem =
                SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(priceData)
                        .build();

        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl(stripeProperties.getSuccessUrl())
                        .setCancelUrl(stripeProperties.getCancelUrl())
                        .setClientReferenceId(payment.getId())
                        .putMetadata("paymentId", payment.getId())
                        .putMetadata("orderId", payment.getOrderId())
                        .addLineItem(lineItem)
                        .build();

        RequestOptions requestOptions = RequestOptions.builder().setIdempotencyKey(
                "checkout-session:" + payment.getId()
        ).build();

        if (stripeProperties.getSecretKey() == null
                || stripeProperties.getSecretKey().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stripe secret key is not configured"
            );
        }

        StripeClient client =
                new StripeClient(stripeProperties.getSecretKey());

        try {
            Session session = client.v1()
                    .checkout()
                    .sessions()
                    .create(params, requestOptions);

            if (session.getId() == null
                    || session.getId().isBlank()
                    || session.getUrl() == null
                    || session.getUrl().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Stripe did not return a valid Checkout Session"
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
                    "Unable to create Stripe Checkout Session",
                    exception
            );
        }
    }

    // Refreshes both payment lookup keys with the same data.
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

    // Converts the persistence entity into the API response model.
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

}
