package com.flashticket.paymentservice.mapper;

import com.flashticket.paymentservice.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper

public interface PaymentMapper {

    int insert(Payment payment);

    Payment findById(@Param("paymentId") String paymentId);

    Payment findByOrderId(@Param("orderId") String orderId);

    int update(Payment payment);
}
