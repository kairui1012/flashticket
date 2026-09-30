package com.flashticket.orderservice.mapper;

import com.flashticket.orderservice.event.InventoryReleaseEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProcessedEventMapper {
    int insertIfAbsent(InventoryReleaseEvent.ProcessedEvent processedEvent);
}
