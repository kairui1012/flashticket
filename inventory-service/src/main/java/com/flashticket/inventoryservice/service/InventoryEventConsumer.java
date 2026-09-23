package com.flashticket.inventoryservice.service;

import com.flashticket.inventoryservice.entity.EventType;
import com.flashticket.inventoryservice.entity.ProcessedEvent;
import com.flashticket.inventoryservice.event.InventoryReleaseEvent;
import com.flashticket.inventoryservice.event.InventoryReservedEvent;
import com.flashticket.inventoryservice.mapper.InventoryMapper;
import com.flashticket.inventoryservice.mapper.ProcessedEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final InventoryMapper inventoryMapper;
    private final ProcessedEventMapper processedEventMapper;

    private static final String RESERVED_TOPIC = "inventory.reserved";
    private static final String RELEASE_TOPIC = "inventory.release";

    @Transactional
    @KafkaListener(
            topics = RESERVED_TOPIC,
            groupId = "inventory-service"
    )
    public void handleInventoryReserved(InventoryReservedEvent event) {

        // STEP 1 -> Validate the Kafka payload before using it in idempotency or stock updates.
        if (event == null
                || event.getEventId() == null
                || event.getEventId().isBlank()
                || event.getTicketId() == null
                || event.getTicketId().isBlank()
                || event.getQuantity() == null
                || event.getQuantity() <= 0) {
            throw new IllegalArgumentException("Invalid inventory reserved event");
        }

        // STEP 2 -> Build the processed-event record using Kafka's stable event ID.
        ProcessedEvent processedEvent = new ProcessedEvent(
                event.getEventId(),
                EventType.INVENTORY_RESERVED,
                LocalDateTime.now()
        );

        // STEP 3 -> Insert the event ID only if it has not been processed before.
        // The event_id primary key provides concurrency-safe idempotency in MySQL.
        int inserted = processedEventMapper.insertIfAbsent(
                processedEvent
        );

        // STEP 4 -> Ignore a redelivered event when its event ID already exists.
        if (inserted == 0) {
            log.info(
                    "Skipping duplicate inventory reserved event: eventId={}",
                    event.getEventId()
            );
            return;
        }

        // STEP 5 -> Log the new event before applying its business change.
        log.info(
                "Processing inventory reserved event: eventId={}, ticketId={}, quantity={}",
                event.getEventId(),
                event.getTicketId(),
                event.getQuantity()
        );

        // STEP 6 -> Conditionally move stock from available to reserved in MySQL.
        int updatedRows = inventoryMapper.reserveStock(
                event.getTicketId(),
                event.getQuantity()
        );

        // STEP 7 -> Throw on failure so the transaction rolls back both database changes.
        // The Kafka record can then be retried instead of being acknowledged as successful.
        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Failed to reserve inventory in MySQL for event: " + event.getEventId()
            );
        }

        // STEP 8 -> Finish the listener so Spring can commit MySQL before Kafka advances the offset.
        log.info(
                "Inventory reservation applied within the transaction: eventId={}",
                event.getEventId()
        );
    }

    @Transactional
    @KafkaListener(
            topics = RELEASE_TOPIC,
            groupId = "inventory-service"
    )
    public void handleInventoryReleased(InventoryReleaseEvent event) {

        // STEP 1 -> Validate the release event before using it for idempotency or stock updates.
        if (event == null
                || event.getEventId() == null
                || event.getEventId().isBlank()
                || event.getTicketId() == null
                || event.getTicketId().isBlank()
                || event.getQuantity() == null
                || event.getQuantity() <= 0) {
            throw new IllegalArgumentException("Invalid inventory release event");
        }

        // STEP 2 -> Build the processed-event record using the release event's stable ID.
        ProcessedEvent processedEvent = new ProcessedEvent(
                event.getEventId(),
                EventType.INVENTORY_RELEASED,
                LocalDateTime.now()
        );

        // STEP 3 -> Insert the event ID only if this release has not been processed before.
        // The event_id primary key prevents concurrent Kafka redelivery from releasing stock twice.
        int inserted = processedEventMapper.insertIfAbsent(processedEvent);

        // STEP 4 -> Ignore a redelivered release event when its event ID already exists.
        if (inserted == 0) {
            log.info(
                    "Skipping duplicate inventory released event: eventId={}",
                    event.getEventId()
            );
            return;
        }

        // STEP 5 -> Log the new release event before applying its business change.
        log.info(
                "Processing inventory released event: eventId={}, ticketId={}, quantity={}",
                event.getEventId(),
                event.getTicketId(),
                event.getQuantity()
        );

        // STEP 6 -> Conditionally move stock from reserved back to available in MySQL.
        int updatedRows = inventoryMapper.releaseStock(
                event.getTicketId(),
                event.getQuantity()
        );

        // STEP 7 -> Throw on failure so both the stock update and processed-event insert roll back.
        // Kafka can then retry the record instead of acknowledging an incomplete release.
        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Failed to release inventory in MySQL for event: " + event.getEventId()
            );
        }

        // STEP 8 -> Finish successfully so Spring can commit MySQL before Kafka advances the offset.
        log.info(
                "Inventory release applied within the transaction: eventId={}",
                event.getEventId()
        );
    }
}
