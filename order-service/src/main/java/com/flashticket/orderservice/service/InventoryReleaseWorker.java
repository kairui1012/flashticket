package com.flashticket.orderservice.service;

import com.flashticket.orderservice.client.InventoryClient;
import com.flashticket.orderservice.dto.ReleaseInventoryRequest;
import com.flashticket.orderservice.entity.InventoryReleaseTask;
import com.flashticket.orderservice.mapper.InventoryReleaseTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor

public class InventoryReleaseWorker {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_COUNT = 3;
    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(1);

    private final InventoryReleaseTaskMapper taskMapper;
    private final InventoryClient inventoryClient;

    @Scheduled(fixedDelayString = "${inventory.release.worker-delay-ms:5000}",initialDelayString =
            "${inventory.release.worker-initial-delay-ms:15000}")
    public void processTasks(){
        // STEP 1: Calculate the current time and the stale-lock threshold.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockExpiredBefore = now.minusMinutes(1);

        // STEP 2: Load pending, retryable, or stale processing tasks in batches.
        List<InventoryReleaseTask> tasks =
                taskMapper.findReadyTasks(
                        now,
                        lockExpiredBefore,
                        BATCH_SIZE
                );

        // STEP 3: Process every eligible inventory release task independently.
        for (InventoryReleaseTask task : tasks) {
            processOne(task);
        }
    }

    private void processOne(InventoryReleaseTask task) {
        LocalDateTime now = LocalDateTime.now();

        // STEP 4: Atomically claim the task to prevent concurrent processing.
        int claimed = taskMapper.markProcessing(
                task.getId(),
                now,
                now.minusMinutes(1)
        );

        if (claimed != 1) {
            return;
        }

        try {
            // STEP 5: Build the request with the task's stable release ID.
            ReleaseInventoryRequest request = new ReleaseInventoryRequest(
                    task.getTicketId(),
                    task.getUserId(),
                    task.getQuantity(),
                    task.getReleaseId(),
                    task.getOrderId()
            );

            // STEP 6: Ask Inventory Service to release the reserved stock.
            inventoryClient.releaseStock(request);

            // STEP 7: Mark the task as successful after Inventory Service responds.
            taskMapper.markSuccess(
                    task.getId(),
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            // STEP 8: Schedule another attempt or mark the task as permanently failed.
            handleFailure(task, e);
        }
    }

    private void handleFailure(
            InventoryReleaseTask task,
            Exception exception
    ) {
        // STEP 9: Increment the retry count and normalize the stored error message.
        int retryCount = task.getRetryCount() + 1;
        LocalDateTime now = LocalDateTime.now();
        String error = exception.getMessage();

        if (error != null && error.length() > 500) {
            error = error.substring(0, 500);
        }

        // STEP 10: Stop retrying after the task reaches the maximum retry count.
        if (retryCount >= MAX_RETRY_COUNT) {
            taskMapper.markDead(
                    task.getId(),
                    retryCount,
                    error,
                    now
            );

            return;
        }

        // STEP 11: Calculate the next retry time with a simple incremental delay.
        LocalDateTime nextRetryAt =
                now.plusSeconds(5L * retryCount);

        // STEP 12: Return the task to the retry queue.
        taskMapper.markRetry(
                task.getId(),
                retryCount,
                nextRetryAt,
                error,
                now
        );
    }
}
