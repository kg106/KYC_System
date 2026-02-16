package com.example.kyc_system.security;

import com.example.kyc_system.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SecurityExceptionHandlingTest {

    private CustomAuthenticationEntryPoint authenticationEntryPoint;
    private CustomAccessDeniedHandler accessDeniedHandler;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() throws Exception {
        authenticationEntryPoint = new CustomAuthenticationEntryPoint();
        accessDeniedHandler = new CustomAccessDeniedHandler();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        outputStream = new ByteArrayOutputStream();

        when(request.getRequestURI()).thenReturn("/api/test");
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public void write(int b) {
                outputStream.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
            }
        });
    }

    @Test
    void authenticationEntryPoint_ShouldReturn401WithJson() throws Exception {
        AuthenticationException authException = mock(AuthenticationException.class);

        authenticationEntryPoint.commence(request, response, authException);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);

        String jsonResponse = outputStream.toString();
        // Verify JSON contains expected fields
        assert jsonResponse.contains("\"status\":401");
        assert jsonResponse.contains("\"error\":\"Unauthorized\"");
    }

    @Test
    void accessDeniedHandler_ShouldReturn403WithJson() throws Exception {
        AccessDeniedException accessDeniedException = new AccessDeniedException("Access denied");

        accessDeniedHandler.handle(request, response, accessDeniedException);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);

        String jsonResponse = outputStream.toString();
        // Verify JSON contains expected fields
        assert jsonResponse.contains("\"status\":403");
        assert jsonResponse.contains("\"error\":\"Forbidden\"");
    }
}
