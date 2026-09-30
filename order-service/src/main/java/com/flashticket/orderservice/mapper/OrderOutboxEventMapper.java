package com.flashticket.orderservice.mapper;

import com.flashticket.orderservice.event.InventoryReleaseEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderOutboxEventMapper {

    int insertIfAbsent(InventoryReleaseEvent.OrderOutboxEvent event);

    List<InventoryReleaseEvent.OrderOutboxEvent> findReadyEvents(
            @Param("now") LocalDateTime now,
            @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore,
            @Param("limit") int limit
    );

    int markProcessing(
            @Param("eventId") String eventId,
            @Param("now") LocalDateTime now,
            @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore
    );

    int markPublished(
            @Param("eventId") String eventId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markRetry(
            @Param("eventId") String eventId,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markDead(
            @Param("eventId") String eventId,
            @Param("retryCount") int retryCount,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
