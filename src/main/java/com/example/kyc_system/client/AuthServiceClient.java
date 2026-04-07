package com.example.kyc_system.client;

import com.example.kyc_system.dto.AuthUserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    private final RestTemplate restTemplate;
    
    @Value("${auth-service.base-url}")
    private String baseUrl;

    public AuthUserDTO getUserById(String userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.getDetails() instanceof Map) {
                Map<?, ?> details = (Map<?, ?>) authentication.getDetails();
                String token = (String) details.get("jwtToken");
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            return restTemplate.exchange(
                baseUrl + "/api/v1/users/" + userId,
                HttpMethod.GET,
                entity,
                AuthUserDTO.class
            ).getBody();
        } catch (Exception e) {
            log.error("Failed to fetch user {} from Auth Service: {}", userId, e.getMessage());
            return null;
        }
    }
}
