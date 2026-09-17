package com.flashticket.authservice.config;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration

public class JwtConfig {

    @Value("${jwt.private-key}")
    private Resource privateKeyResource;

    @Value("${jwt.public-key}")
    private Resource publicKeyResource;


    // private.pem → Resource
    //             → String → Remove PEM header/footer
    //             → Base64 decode → byte[]
    //             → PKCS8EncodedKeySpec → KeyFactory("RSA")
    //             → PrivateKey

    private PrivateKey loadPrivateKey() throws Exception {

        // STEP 1: Read the private key file content.
        String key = new String(
                privateKeyResource.getInputStream().readAllBytes()
        );

        // STEP 2: Remove the PEM header, footer, and whitespace.
        key = key
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        // STEP 3: Decode the Base64 key content.
        byte[] keyBytes = Base64.getDecoder().decode(key);

        // STEP 4: Create a PKCS#8 key specification from the decoded bytes.
        PKCS8EncodedKeySpec keySpec =
                new PKCS8EncodedKeySpec(keyBytes);

        // STEP 5: Create an RSA key factory.
        KeyFactory keyFactory =
                KeyFactory.getInstance("RSA");

        return keyFactory.generatePrivate(keySpec);
    }

    // public.pem → Resource
    //            → String → Remove PEM header/footer
    //            → Base64 decode → byte[]
    //            → X509EncodedKeySpec → KeyFactory("RSA")
    //            → PublicKey
    private PublicKey loadPublicKey() throws Exception {

        // STEP 1: Read the public key file content.
        String key = new String(
                publicKeyResource.getInputStream().readAllBytes()
        );

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
    public JwtEncoder jwtEncoder() throws Exception {
        // STEP 1: Load the RSA private key.
        RSAPrivateKey privateKey =
                (RSAPrivateKey) loadPrivateKey();

        // STEP 2: Load the RSA public key.
        RSAPublicKey publicKey =
                (RSAPublicKey) loadPublicKey();

        // STEP 3: Create a JWT encoder with the RSA key pair.
        return NimbusJwtEncoder
                .withKeyPair(publicKey, privateKey)
                .build();
    }
}
