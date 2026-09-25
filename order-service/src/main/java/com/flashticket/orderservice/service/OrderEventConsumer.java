package com.flashticket.orderservice.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Data

public class OrderEventConsumer {
    private static final String RESERVED_TOPIC = "inventory.reserved";
    private static final String RELEASE_TOPIC = "inventory.release";

    @Transactional
    @KafkaListener(
            topics = RESERVED_TOPIC,
            groupId = "inventory-service"
    )
    @KafkaListener( topics = RESERVED_TOPIC,
            groupId = "inventory-service" )
}
