package com.flashticket.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Principal;

@Configuration
public class RateLimiterConfig {

    // Previous approach: Use the client IP address as the rate-limit key.
    // Kept as a learning reference.
    // @Bean
    // public KeyResolver ipKeyResolver() {
    //     return exchange -> Mono.just(
    //             exchange.getRequest()
    //                     .getRemoteAddress()
    //                     .getAddress()
    //                     .getHostAddress()
    //     );
    // }

    // STEP 1: Now use the authenticated user ID as the rate-limit key.
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal().map(
                Principal::getName
        );
    }
}
