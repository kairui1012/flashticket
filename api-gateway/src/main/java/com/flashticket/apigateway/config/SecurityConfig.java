package com.flashticket.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
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

        http.authorizeExchange(auth -> auth
                .pathMatchers("/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/tickets/**",
                        "/api/v1/inventory/**").hasAnyRole("USER", "ADMIN")
                .pathMatchers("/api/v1/tickets/**", "/api/v1/inventory/**")
                .hasRole("ADMIN")
                .anyExchange().authenticated());

        http.oauth2ResourceServer(oAuth2ResourceServerSpec -> oAuth2ResourceServerSpec.jwt(
                jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
        ));

        // Disable CSRF because the gateway uses token-based authentication.
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);

        return http.build();
    }

    private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return new ReactiveJwtAuthenticationConverterAdapter(authenticationConverter);
    }
}
