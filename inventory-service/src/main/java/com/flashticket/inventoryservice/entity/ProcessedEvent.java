package com.flashticket.inventoryservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {
    private String eventId;
    private EventType eventType;
    private LocalDateTime processedAt;
}
