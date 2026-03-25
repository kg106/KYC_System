package com.example.kyc_system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom handler for 403 Forbidden responses.
 * Called when an authenticated user tries to access a resource they don't have
 * permission for.
 * Returns a structured JSON error instead of Spring's default HTML page.
 */
@Component
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    /**
     * Handles an access denied failure by returning a structured JSON response.
     *
     * @param request the request that resulted in an AccessDeniedException
     * @param response the response so that a status code and error message can be sent
     * @param accessDeniedException the exception that caused the invocation
     * @throws IOException in case of I/O errors
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        log.warn("Forbidden access attempt: path={}, message={}", request.getServletPath(), accessDeniedException.getMessage());
        
        // Set response status and content type
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");

        // Construct error response body
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", "Forbidden");
        body.put("message", "You don't have permission to access this resource");
        body.put("path", request.getServletPath());

        // Write JSON response
        new ObjectMapper().writeValue(response.getOutputStream(), body);
    }
}
