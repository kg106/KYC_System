package com.example.kyc_system.exception;

import com.example.kyc_system.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/test-endpoint");
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException (400 Bad Request)")
    void handleValidationExceptions_ReturnsBadRequest() {
        // Arrange
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError error1 = new FieldError("objectName", "email", "must be a well-formed email address");
        FieldError error2 = new FieldError("objectName", "password", "must not be blank");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));

        // Act
        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleValidationExceptions(ex, request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        ErrorResponse response = responseEntity.getBody();
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
        assertEquals("Validation Error", response.getError());
        assertEquals("/api/test-endpoint", response.getPath());
        assertNotNull(response.getTimestamp());

        // Assert that both messages are included in the combined message
        org.junit.jupiter.api.Assertions
                .assertTrue(response.getMessage().contains("must be a well-formed email address"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getMessage().contains("must not be blank"));
    }

    @Test
    @DisplayName("Should handle AccessDeniedException (403 Forbidden)")
    void handleAccessDeniedException_ReturnsForbidden() {
        // Arrange
        AccessDeniedException ex = new AccessDeniedException("Access Denied");

        // Act
        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleAccessDeniedException(ex, request);

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, responseEntity.getStatusCode());
        ErrorResponse response = responseEntity.getBody();
        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        assertEquals("Forbidden", response.getError());
        assertEquals("You do not have permission to access this resource", response.getMessage());
        assertEquals("/api/test-endpoint", response.getPath());
    }

    @Test
    @DisplayName("Should handle BusinessException (409 Conflict)")
    void handleBusinessException_ReturnsConflict() {
        // Arrange
        BusinessException ex = new BusinessException("Daily attempt limit reached");

        // Act
        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleBusinessException(ex, request);

        // Assert
        assertEquals(HttpStatus.CONFLICT, responseEntity.getStatusCode());
        ErrorResponse response = responseEntity.getBody();
        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT.value(), response.getStatus());
        assertEquals("Conflict", response.getError());
        assertEquals("Daily attempt limit reached", response.getMessage());
        assertEquals("/api/test-endpoint", response.getPath());
    }

    @Test
    @DisplayName("Should handle generic RuntimeException (500 Internal Server Error)")
    void handleRuntimeException_ReturnsInternalServerError() {
        // Arrange
        RuntimeException ex = new RuntimeException("Unexpected null pointer");

        // Act
        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleRuntimeException(ex, request);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
        ErrorResponse response = responseEntity.getBody();
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatus());
        assertEquals("Internal Server Error", response.getError());
        assertEquals("Unexpected null pointer", response.getMessage());
        assertEquals("/api/test-endpoint", response.getPath());
    }

    @Test
    @DisplayName("Should handle generic Exception (500 Internal Server Error)")
    void handleGeneralException_ReturnsInternalServerError() {
        // Arrange
        Exception ex = new Exception("Total failure");

        // Act
        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleGeneralException(ex, request);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
        ErrorResponse response = responseEntity.getBody();
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatus());
        assertEquals("Internal Server Error", response.getError());
        assertEquals("An unexpected error occurred", response.getMessage());
        assertEquals("/api/test-endpoint", response.getPath());
    }
}
