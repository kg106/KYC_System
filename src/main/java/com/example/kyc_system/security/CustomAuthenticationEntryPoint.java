package com.example.kyc_system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom handler for 401 Unauthorized responses.
 * Called when a request arrives without a valid JWT or with no credentials at
 * all.
 * Returns a structured JSON error instead of Spring's default HTML page.
 */
@Component
@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * Commences an authentication scheme by returning a structured JSON response.
     *
     * @param request the request that resulted in an AuthenticationException
     * @param response the response so that a status code and error message can be sent
     * @param authException the exception that caused the invocation
     * @throws IOException in case of I/O errors
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        log.warn("Unauthorized access attempt: path={}, message={}", request.getServletPath(), authException.getMessage());
        
        // Set response status and content type
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");

        // Construct error response body
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Unauthorized");
        body.put("message", "You need to login first to access this resource");
        body.put("path", request.getServletPath());

        // Write JSON response
        new ObjectMapper().writeValue(response.getOutputStream(), body);
    }
}
