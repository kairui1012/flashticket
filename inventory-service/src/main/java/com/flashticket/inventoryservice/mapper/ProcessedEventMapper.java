package com.flashticket.inventoryservice.mapper;

import com.flashticket.inventoryservice.event.ProcessedEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProcessedEventMapper {
    int insertIfAbsent(ProcessedEvent processedEvent);
}
