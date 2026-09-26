package com.flashticket.orderservice.service;

import com.flashticket.orderservice.dto.ReleaseInventoryRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Data
public class InventoryReleaseTaskService {
    public ReleaseInventoryRequest createTask(String id, String id1, String ticketId, String userId, Integer quantity, String userCancelled) {
        
    }
}
