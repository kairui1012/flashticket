package com.flashticket.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    // STEP 1: Read the public key file path from application.yaml.
    @Value("${jwt.public-key}")
    private Resource publicKeyResource;

    // public.pem → Resource → String → Remove PEM header/footer
    //            → Base64 decode → byte[] → X509EncodedKeySpec
    //            → KeyFactory("RSA") → PublicKey
    private PublicKey loadPublicKey() throws Exception{

        // STEP 1: Read the public key file content.
        String key =  new String(publicKeyResource.getInputStream().readAllBytes());

        // STEP 2: Remove the PEM header, footer, and whitespace.
        key = key
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        // STEP 3: Decode the Base64 key content.
        byte[] keyBytes = Base64.getDecoder().decode(key);

        // STEP 4: Create an X.509 key specification from the decoded bytes.
        X509EncodedKeySpec keySpec =
                new X509EncodedKeySpec(keyBytes);

        // STEP 5: Create an RSA key factory.
        KeyFactory keyFactory =
                KeyFactory.getInstance("RSA");

        return keyFactory.generatePublic(keySpec);
    }

    @Bean
    public JwtDecoder jwtDecoder() throws Exception{
        // STEP 1: Load the RSA public key.
        RSAPublicKey publicKey = (RSAPublicKey) loadPublicKey();

        // STEP 2: Create a JWT decoder with the RSA public key.
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}
