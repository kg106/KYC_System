package com.example.kyc_system.exception;

import com.example.kyc_system.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.*;

/**
 * Centralized exception handler for all REST controllers.
 * Catches specific exceptions and returns standardized ErrorResponse JSON.
 * Exception handling order (from most specific to most general):
 * 1. Validation errors (400)
 * 2. Access denied (403)
 * 3. Business rule violations (409)
 * 4. Runtime exceptions (500)
 * 5. General exceptions (500)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

        /**
         * Handles @Valid annotation failures — collects all field errors into a single
         * comma-separated message.
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidationExceptions(
                        MethodArgumentNotValidException ex, HttpServletRequest request) {

                Map<String, String> errors = new HashMap<>();
                ex.getBindingResult().getFieldErrors().forEach((error) -> {
                        String fieldName = error.getField();
                        String errorMessage = error.getDefaultMessage();
                        errors.put(fieldName, errorMessage);
                });

                // Combine all validation error messages
                String combinedMessage = errors.values().stream()
                                .filter(msg -> msg != null)
                                .collect(Collectors.joining(", "));

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error("Validation Error")
                                .message(combinedMessage)
                                .path(request.getRequestURI())
                                .build();

                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }

        /** Handles Spring Security access denied (missing required role). */
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDeniedException(
                        AccessDeniedException ex, HttpServletRequest request) {

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.FORBIDDEN.value())
                                .error("Forbidden")
                                .message("You do not have permission to access this resource")
                                .path(request.getRequestURI())
                                .build();

                return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
        }

        /**
         * Handles business rule violations (e.g., duplicate KYC, daily limit exceeded).
         */
        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ErrorResponse> handleBusinessException(
                        BusinessException ex, HttpServletRequest request) {

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.CONFLICT.value())
                                .error("Conflict")
                                .message(ex.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
        }

        /**
         * Catches unchecked exceptions — typically programming errors or unexpected
         * failures.
         */
        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .error("Internal Server Error")
                                .message(ex.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        /**
         * Last-resort handler for all other exceptions — returns a generic error
         * message.
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .error("Internal Server Error")
                                .message("An unexpected error occurred")
                                .path(request.getRequestURI())
                                .build();

                return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
}
