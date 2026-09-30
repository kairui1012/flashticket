package com.flashticket.paymentservice.mapper;

import com.flashticket.paymentservice.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper

public interface PaymentMapper {

    int insert(Payment payment);

    Payment findById(@Param("paymentId") String paymentId);

    Payment findByOrderId(@Param("orderId") String orderId);

    int markSucceeded(
            @Param("paymentId") String paymentId,
            @Param("orderId") String orderId,
            @Param("providerTransactionId") String providerTransactionId,
            @Param("paidAt") LocalDateTime paidAt
    );

}
