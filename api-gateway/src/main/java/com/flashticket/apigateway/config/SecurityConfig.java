package com.flashticket.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * STEP 1: Allow public access to login and registration endpoints.
 * STEP 2: Require authentication for all other gateway requests.
 * STEP 3: Disable CSRF for the stateless API.
 */

@Configuration

public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http)
    {

        // /login or /register? → YES: permitAll → NO: authenticated
        http.authorizeExchange(auth -> auth.pathMatchers("/api/v1/auth/login",
                "/api/v1/auth/register").permitAll().anyExchange().authenticated());

        http.oauth2ResourceServer(oAuth2ResourceServerSpec -> oAuth2ResourceServerSpec.jwt(Customizer.withDefaults()));

        // Disable CSRF because the gateway uses token-based authentication.
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);

        return http.build();
    }
}
