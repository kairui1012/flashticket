package com.flashticket.authservice.service;

import com.flashticket.authservice.entity.AuthAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;

    // AuthAccount → JWT claims → JwtEncoderParameters → Jwt → Token string
    public String generateToken(AuthAccount account){

        // STEP 1: Use the account ID as the token subject.
        String sub = account.getId();

        // STEP 2: Set the issue time and one-hour expiration time.
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(3600);

        // STEP 3: Build the JWT claims.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(sub)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt).build();

        // STEP 4: Convert the claims into encoder parameters.
        JwtEncoderParameters parameters = JwtEncoderParameters.from(claims);

        // STEP 5: Encode the JWT and return the token value.
        Jwt jwt = jwtEncoder.encode(parameters);
        return jwt.getTokenValue();
    }
}
