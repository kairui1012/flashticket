package com.flashticket.orderservice.service;

import com.flashticket.orderservice.entity.InventoryReleaseTask;
import com.flashticket.orderservice.entity.InventoryReleaseTaskStatus;
import com.flashticket.orderservice.entity.OrderStatus;
import com.flashticket.orderservice.mapper.InventoryReleaseTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j

/**
 * Persists durable inventory-release tasks in the database.
 *
 * Overall inventory-release flow:
 * 1. An unpaid order reaches its payment deadline.
 * 2. Order Service conditionally changes its status from
 *    PENDING_PAYMENT to EXPIRED.
 * 3. Only a successful status update creates a release task.
 * 4. The new task starts with the PENDING status.
 * 5. InventoryReleaseWorker claims the task and changes it
 *    to PROCESSING.
 * 6. The worker builds a ReleaseInventoryRequest and calls
 *    Inventory Service.
 * 7. A successful release changes the task to SUCCESS.
 * 8. A failed release changes it to RETRY, or DEAD after
 *    the maximum number of attempts.
 */


public class InventoryReleaseTaskService {

    private final InventoryReleaseTaskMapper inventoryReleaseTaskMapper;

    public void createTask(String releaseId, String orderId, String ticketId, String userId, Integer quantity, OrderStatus status) {

        LocalDateTime now = LocalDateTime.now();

        validateTask(
                releaseId,
                orderId,
                ticketId,
                userId,
                quantity,
                status
        );

        InventoryReleaseTask inventoryReleaseTask = new InventoryReleaseTask();
        inventoryReleaseTask.setId(releaseId);
        inventoryReleaseTask.setOrderId(orderId);
        inventoryReleaseTask.setReleaseId(releaseId);
        inventoryReleaseTask.setTicketId(ticketId);
        inventoryReleaseTask.setUserId(userId);
        inventoryReleaseTask.setQuantity(quantity);
        inventoryReleaseTask.setReason(status.name());
        inventoryReleaseTask.setStatus(InventoryReleaseTaskStatus.PENDING);
        inventoryReleaseTask.setRetryCount(0);
        inventoryReleaseTask.setNextRetryAt(now);
        inventoryReleaseTask.setLockedAt(null);
        inventoryReleaseTask.setLastError(null);
        inventoryReleaseTask.setCreatedAt(now);
        inventoryReleaseTask.setUpdatedAt(now);

        int insertedRows =
                inventoryReleaseTaskMapper.insertIfAbsent(inventoryReleaseTask);

        if (insertedRows == 0) {
            log.info(
                    "Inventory release task already exists: orderId={}, releaseId={}",
                    orderId,
                    releaseId
            );
        }
    }

    private void validateTask(
            String releaseId,
            String orderId,
            String ticketId,
            String userId,
            Integer quantity,
            OrderStatus orderStatus
    ) {
        if (releaseId == null || releaseId.isBlank()
                || orderId == null || orderId.isBlank()
                || ticketId == null || ticketId.isBlank()
                || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException(
                    "Release, order, ticket, and user IDs are required"
            );
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }

        if (orderStatus != OrderStatus.CANCELLED
                && orderStatus != OrderStatus.EXPIRED) {
            throw new IllegalArgumentException(
                    "Inventory can only be released for a cancelled or expired order"
            );
        }
    }
}
