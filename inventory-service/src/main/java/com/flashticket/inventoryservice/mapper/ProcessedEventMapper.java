package com.flashticket.inventoryservice.mapper;

import com.flashticket.inventoryservice.entity.ProcessedEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProcessedEventMapper {
    int insertIfAbsent(ProcessedEvent processedEvent);
}
