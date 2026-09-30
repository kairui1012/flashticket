package com.flashticket.orderservice.mapper;

import com.flashticket.orderservice.event.ProcessedEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProcessedEventMapper {
    int insertIfAbsent(ProcessedEvent processedEvent);
}
