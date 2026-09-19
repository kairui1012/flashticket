package com.flashticket.inventoryservice.controller;

import com.flashticket.inventoryservice.dto.InventoryResponse;
import com.flashticket.inventoryservice.dto.InsertInventoryRequest;
import com.flashticket.inventoryservice.dto.ReleaseStockRequest;
import com.flashticket.inventoryservice.dto.ReserveStockRequest;
import com.flashticket.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    // Creates the initial inventory record for one ticket.
    @PostMapping("/insert")
    public ResponseEntity<InventoryResponse> insert(
            @Valid @RequestBody InsertInventoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                inventoryService.insert(request)
        );
    }

    // Returns the inventory record associated with one ticket.
    @GetMapping("/{id}")
    public ResponseEntity<InventoryResponse> findByTicketId(@PathVariable String ticketId){
        return ResponseEntity.ok(inventoryService.findByTicketId(ticketId)
        );
    }

    // Updates the available stock amount for one ticket.
    @PutMapping("/update/{id}")
    public ResponseEntity<InventoryResponse> updateAvailableStock( String ticketId, Integer availableStock){
        return ResponseEntity.ok(inventoryService.updateAvailableStock(ticketId,availableStock)
        );
    }

    // Increases available stock, for example after stock is released.
    @PutMapping("/{id}/increase")
    public ResponseEntity<InventoryResponse> increaseAvailableStock( String ticketId, Integer quantity){
        return ResponseEntity.ok(inventoryService.increaseAvailableStock(ticketId,quantity)
        );
    }

    // Decreases available stock, for example after stock is reserved.
    @PutMapping("/{id}/decrease")
    public ResponseEntity<InventoryResponse> decreaseAvailableStock( String ticketId, Integer quantity){
        return ResponseEntity.ok(inventoryService.decreaseAvailableStock(ticketId,quantity)
        );
    }

    // Reserves stock by moving the requested quantity from available to reserved.
    @PostMapping("/reserve")
    public ResponseEntity<InventoryResponse> reserveStock(
            @Valid @RequestBody ReserveStockRequest request
    ) {
        return ResponseEntity.ok(inventoryService.reserveStock(request));
    }

    // Releases reserved stock by moving the requested quantity back to available.
    @PostMapping("/release")
    public ResponseEntity<InventoryResponse> releaseStock(
            @Valid @RequestBody ReleaseStockRequest request
    ) {
        return ResponseEntity.ok(inventoryService.releaseStock(request));
    }

}
