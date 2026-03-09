package com.example.kyc_system.exception;

/**
 * Custom exception for business-rule violations (e.g., duplicate KYC request).
 * Handled by GlobalExceptionHandler and returned as HTTP 409 Conflict.
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
