package com.flashticket.orderservice.mapper;

import com.flashticket.orderservice.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {

    Order findByUserIdAndTicketId(
            @Param("userId") String userId,
            @Param("ticketId") String ticketId
    );

    int insert(Order order);

    Order findById(@Param("orderId") String orderId);

    List<Order> findByUserId(@Param("userId") String userId);

    int cancelPendingOrder(
            @Param("orderId") String orderId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markPendingOrderAsPaid(
            @Param("orderId") String orderId,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    List<Order> findExpiredPendingOrders(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    int expirePendingOrder(
            @Param("orderId") String orderId,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
