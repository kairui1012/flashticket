package com.flashticket.paymentservice.service;

import com.flashticket.paymentservice.config.StripeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j

// Webhook processing flow:
// Check the webhook secret
// ↓
// Verify the Stripe signature
// ↓
// Invalid signature: return HTTP 400
// ↓
// Valid signature: obtain and log the event
// ↓
// Ignore unsupported event types
// ↓
// Deserialize the supported event data

public class StripeWebhookService {

    private final StripeProperties stripeProperties;
    private static final String CHECKOUT_SESSION_COMPLETED = "checkout.session.completed";
    private static final String STRIPE_PAYMENT_STATUS_PAID = "paid";

    private final PaymentService paymentService;

    public void handleWebhook(String payload, String stripeSignature) {
        String webhookSecret = stripeProperties.getWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stripe webhook secret is not configured"
            );
        }

        Event event;

        try {
            // Verify that the payload was sent by Stripe and was not modified.
            event = Webhook.constructEvent(
                    payload,stripeSignature,webhookSecret
            );
        } catch (SignatureVerificationException exception) {
            // Reject requests with an invalid Stripe signature.
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid Stripe webhook signature",
                    exception
            );
        }

        log.info(
                "Verified Stripe webhook: eventId={}, eventType={}",
                event.getId(),
                event.getType()
        );

        // Only checkout completion events are handled by this service.
        if (!CHECKOUT_SESSION_COMPLETED.equals(event.getType())){
            log.info(
                    "Ignoring unsupported Stripe event: eventId={}, eventType={}",
                    event.getId(),
                    event.getType()
            );
            return;
        }

        // Convert the generic event data into a Stripe object for further processing.
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject().orElseThrow(
                () -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unable to deserialize Stripe event data"
                )
        );

        if (!(stripeObject instanceof Session session)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Stripe event data is not a Checkout Session"
            );
        }

        if (!STRIPE_PAYMENT_STATUS_PAID.equals(session.getPaymentStatus())) {
            log.info(
                    "Ignoring unpaid Checkout Session: sessionId={}, paymentStatus={}",
                    session.getId(),
                    session.getPaymentStatus()
            );
            return;
        }

        Map<String, String> metadata = session.getMetadata();
        if (metadata == null){
            log.info(
                    "Ignoring Checkout Session without metadata: sessionId={}",
                    session.getId()
            );
            return;
        }

        String paymentId = metadata.get("paymentId");
        String orderId = metadata.get("orderId");
        if (paymentId == null || paymentId.isBlank()
                || orderId == null || orderId.isBlank()) {
            log.info(
                    "Ignoring Checkout Session with missing payment metadata: sessionId={}, paymentId={}, orderId={}",
                    session.getId(),
                    paymentId,
                    orderId
            );
            return;
        }
        
        String providerTransactionId = session.getPaymentIntent();

        LocalDateTime paidAt = Instant
                .ofEpochSecond(event.getCreated())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        paymentService.markPaymentSucceeded(
                paymentId,
                orderId,
                providerTransactionId,
                paidAt
        );
    }
}

