package com.flashticket.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * STEP 1: Provide a BCrypt PasswordEncoder.
 * STEP 2: Allow unauthenticated access to /register and /login.
 */
@Configuration

public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    /**
     * Request
     *    ↓
     * Spring Security
     *    ↓
     * CSRF checks disabled
     *    ↓
     * /login or /register?
     *    ├── YES → permitAll
     *    └── NO  → denyAll
     *    ↓
     * AuthController
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)throws Exception{
        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/error")
                .permitAll()
                .anyRequest()
                .denyAll()
        );

        http.csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
