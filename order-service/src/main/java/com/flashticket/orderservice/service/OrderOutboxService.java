package com.flashticket.orderservice.service;

import com.flashticket.orderservice.event.OrderOutboxEvent;
import com.flashticket.orderservice.entity.OrderOutboxStatus;
import com.flashticket.orderservice.event.OrderCreatedEvent;
import com.flashticket.orderservice.event.OrderTerminatedEvent;
import com.flashticket.orderservice.mapper.OrderOutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderOutboxService {

    private static final String ORDER_CREATED_TOPIC = "order.created";
    private static final String ORDER_CREATED_EVENT_TYPE = "ORDER_CREATED";
    private static final String ORDER_TERMINATED_TOPIC = "order.terminated";
    private static final String ORDER_TERMINATED_EVENT_TYPE = "ORDER_TERMINATED";

    private final OrderOutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    public void createOrderCreatedEvent(OrderCreatedEvent event) {
        createEvent(
                event.getEventId(),
                event.getOrderId(),
                ORDER_CREATED_TOPIC,
                ORDER_CREATED_EVENT_TYPE,
                event
        );
    }

    public void createOrderTerminatedEvent(OrderTerminatedEvent event) {
        createEvent(
                event.getEventId(),
                event.getOrderId(),
                ORDER_TERMINATED_TOPIC,
                ORDER_TERMINATED_EVENT_TYPE,
                event
        );
    }

    private void createEvent(
            String eventId,
            String orderId,
            String topic,
            String eventType,
            Object payload
    ) {
        LocalDateTime now = LocalDateTime.now();

        OrderOutboxEvent outboxEvent = new OrderOutboxEvent();
        outboxEvent.setId(eventId);
        outboxEvent.setAggregateId(orderId);
        outboxEvent.setTopic(topic);
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
        outboxEvent.setStatus(OrderOutboxStatus.PENDING);
        outboxEvent.setRetryCount(0);
        outboxEvent.setNextRetryAt(now);
        outboxEvent.setLockedAt(null);
        outboxEvent.setLastError(null);
        outboxEvent.setCreatedAt(now);
        outboxEvent.setUpdatedAt(now);

        int insertedRows = outboxEventMapper.insertIfAbsent(outboxEvent);

        if (insertedRows != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An order outbox event already exists for order and type: "
                            + orderId + ", " + eventType
            );
        }
    }
}
