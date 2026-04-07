package com.example.kyc_system.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.math.BigInteger;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Base64;

@Component
public class JwksKeyProvider {

    @Value("${auth-service.jwks-url}")
    private String jwksUrl;

    private final RestTemplate restTemplate;
    private RSAPublicKey cachedKey;
    private Instant lastFetched;
    private static final Duration CACHE_DURATION = Duration.ofHours(1);

    public JwksKeyProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public RSAPublicKey getPublicKey() {
        if (cachedKey == null || Instant.now().isAfter(lastFetched.plus(CACHE_DURATION))) {
            refreshKey();
        }
        return cachedKey;
    }

    private synchronized void refreshKey() {
        try {
            Map<String, Object> jwks = restTemplate.getForObject(jwksUrl, Map.class);
            List<Map<String, String>> keys = (List) jwks.get("keys");
            Map<String, String> keyData = keys.get(0);

            byte[] nBytes = Base64.getUrlDecoder().decode(keyData.get("n"));
            byte[] eBytes = Base64.getUrlDecoder().decode(keyData.get("e"));

            RSAPublicKeySpec spec = new RSAPublicKeySpec(
                new BigInteger(1, nBytes),
                new BigInteger(1, eBytes)
            );
            KeyFactory factory = KeyFactory.getInstance("RSA");
            cachedKey = (RSAPublicKey) factory.generatePublic(spec);
            lastFetched = Instant.now();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch JWKS", e);
        }
    }
}
