package com.flashticket.orderservice.service;

import com.flashticket.orderservice.entity.OrderOutboxStatus;
import com.flashticket.orderservice.event.InventoryReleaseEvent;
import com.flashticket.orderservice.event.OrderCreatedEvent;
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

    private final OrderOutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    public void createOrderCreatedEvent(OrderCreatedEvent event) {
        LocalDateTime now = LocalDateTime.now();

        InventoryReleaseEvent.OrderOutboxEvent outboxEvent = new InventoryReleaseEvent.OrderOutboxEvent();
        outboxEvent.setId(event.getEventId());
        outboxEvent.setAggregateId(event.getOrderId());
        outboxEvent.setTopic(ORDER_CREATED_TOPIC);
        outboxEvent.setEventType(ORDER_CREATED_EVENT_TYPE);
        outboxEvent.setPayload(objectMapper.writeValueAsString(event));
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
                    "An order-created outbox event already exists for order: "
                            + event.getOrderId()
            );
        }
    }
}
