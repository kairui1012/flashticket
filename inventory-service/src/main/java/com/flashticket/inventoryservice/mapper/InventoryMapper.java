package com.flashticket.inventoryservice.mapper;

import com.flashticket.inventoryservice.entity.Inventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryMapper {

    Inventory findByTicketId(@Param("ticketId") String ticketId);

    void insert(Inventory inventory);

    void updateAvailableStock(
            @Param("ticketId") String ticketId,
            @Param("availableStock") Integer availableStock
    );

    int increaseStock(
            @Param("ticketId") String ticketId,
            @Param("quantity") Integer quantity
    );

    int decreaseStock(
            @Param("ticketId") String ticketId,
            @Param("quantity") Integer quantity
    );

    int reserveStock(
            @Param("ticketId") String ticketId,
            @Param("quantity") Integer quantity
    );

    int releaseStock(
            @Param("ticketId") String ticketId,
            @Param("quantity") Integer quantity
    );

}
