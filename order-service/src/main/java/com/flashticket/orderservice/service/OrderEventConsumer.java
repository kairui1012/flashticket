package com.flashticket.orderservice.service;

import com.flashticket.orderservice.event.ProcessedEvent;
import com.flashticket.orderservice.event.EventType;
import com.flashticket.orderservice.event.InventoryReservedEvent;
import com.flashticket.orderservice.mapper.ProcessedEventMapper;
import com.flashticket.orderservice.mapper.OrderMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@Data

public class OrderEventConsumer {
    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final ProcessedEventMapper processedEventMapper;
    private static final String RELEASE_TOPIC = "inventory.release";
    private static final String RESERVED_TOPIC = "inventory.reserved";

    @KafkaListener(
            topics = RESERVED_TOPIC,
            groupId = "order-service",
            properties = "spring.json.value.default.type=com.flashticket.orderservice.event.InventoryReservedEvent"
    )
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {

        // STEP 1 -> Validate the Kafka payload before using it in idempotency or stock updates.
        validateEvent(event);

        // STEP 2 -> Build the processed-event record using Kafka's stable event ID.
        ProcessedEvent processedEvent = new ProcessedEvent(
                event.getEventId(),
                EventType.INVENTORY_RESERVED,
                LocalDateTime.now()
        );

        int inserted = processedEventMapper.insertIfAbsent(processedEvent);

        if (inserted == 0) {
            log.info(
                    "Skipping duplicate inventory reserved event: eventId={}",
                    event.getEventId()
            );
            return;
        }

        log.info(
                "Processing inventory reserved event: eventId={}, ticketId={}, userId={}, quantity={}",
                event.getEventId(),
                event.getTicketId(),
                event.getUserId(),
                event.getQuantity()
        );

        // Propagate failures so the transaction rolls back and Kafka can retry the event.
        orderService.createOrderFromInventoryEvent(event);

    }

    private void validateEvent(InventoryReservedEvent event) {
        if (event == null
                || event.getEventId() == null
                || event.getEventId().isBlank()
                || event.getTicketId() == null
                || event.getTicketId().isBlank()
                || event.getUserId() == null
                || event.getUserId().isBlank()
                || event.getQuantity() == null
                || event.getQuantity() <= 0
                || event.getOccurredAt() == null) {

            throw new IllegalArgumentException(
                    "Invalid inventory reserved event"
            );
        }
    }

}
