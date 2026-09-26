package com.flashticket.orderservice.mapper;

import com.flashticket.orderservice.entity.InventoryReleaseTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryReleaseTaskMapper {

    int insertIfAbsent(InventoryReleaseTask task);

    InventoryReleaseTask findById(@Param("taskId") String taskId);

    InventoryReleaseTask findByOrderId(@Param("orderId") String orderId);

    List<InventoryReleaseTask> findReadyTasks(
            @Param("now") LocalDateTime now,
            @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore,
            @Param("limit") int limit
    );

    int markProcessing(
            @Param("taskId") String taskId,
            @Param("now") LocalDateTime now,
            @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore
    );

    int markSuccess(
            @Param("taskId") String taskId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markRetry(
            @Param("taskId") String taskId,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markDead(
            @Param("taskId") String taskId,
            @Param("retryCount") int retryCount,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
