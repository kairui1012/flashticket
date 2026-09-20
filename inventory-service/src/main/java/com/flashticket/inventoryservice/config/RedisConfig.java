package com.flashticket.inventoryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration

public class RedisConfig {
    @Bean
    public DefaultRedisScript<Long> reserveStockScript(){
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("resources/lua/reserve_stock.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
