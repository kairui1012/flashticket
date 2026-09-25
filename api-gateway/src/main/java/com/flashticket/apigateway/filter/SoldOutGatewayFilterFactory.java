package com.flashticket.apigateway.filter;

import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SoldOutGatewayFilterFactory
        extends AbstractGatewayFilterFactory<SoldOutGatewayFilterFactory.Config> {

    // Matches POST /api/v1/inventory/{ticketId}/reserve and captures the ticket ID.
    private static final Pattern RESERVE_PATH = Pattern.compile(
            "^/api/v1/inventory/([^/]+)/reserve$"
    );

    private final ReactiveStringRedisTemplate redisTemplate;

    public SoldOutGatewayFilterFactory(ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    // This filter currently has no configurable YAML properties.
    public static class Config {
    }

    @Override
    public GatewayFilter apply(@NonNull Config config) {
        return (exchange, chain) -> {

            // Only inspect POST requests; all other HTTP methods continue normally.
            if (exchange.getRequest().getMethod() != HttpMethod.POST) {
                return chain.filter(exchange);
            }

            String path = exchange.getRequest()
                    .getURI()
                    .getPath();

            Matcher matcher = RESERVE_PATH.matcher(path);

            // Only apply the sold-out check to the inventory reservation endpoint.
            if (!matcher.matches()) {
                return chain.filter(exchange);
            }

            String ticketId = matcher.group(1);
            String soldOutKey = "inventory:sold-out:" + ticketId;

            // Check the shared Redis marker before forwarding traffic to Inventory Service.
            return redisTemplate.hasKey(soldOutKey)
                    .flatMap(soldOut -> {
                        // No marker means the request must continue to the authoritative Lua check.
                        if (!soldOut) {
                            return chain.filter(exchange);
                        }

                        // A sold-out marker allows the Gateway to reject the request immediately.
                        exchange.getResponse().setStatusCode(HttpStatus.CONFLICT);
                        exchange.getResponse()
                                .getHeaders()
                                .setContentType(MediaType.APPLICATION_JSON);

                        byte[] responseBody = (
                                "{\"code\":\"SOLD_OUT\","
                                        + "\"message\":\"Ticket is sold out\","
                                        + "\"ticketId\":\"" + ticketId + "\"}"
                        ).getBytes(StandardCharsets.UTF_8);

                        DataBuffer buffer = exchange.getResponse()
                                .bufferFactory()
                                .wrap(responseBody);

                        // Complete the response here without calling the downstream service.
                        return exchange.getResponse()
                                .writeWith(Mono.just(buffer));
                    });
        };
    }
}
