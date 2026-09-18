package com.flashticket.inventoryservice.mapper;

import com.flashticket.inventoryservice.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryMapper {

    Inventory findByEmail(String email);

    void insert(Inventory inventory);
}
