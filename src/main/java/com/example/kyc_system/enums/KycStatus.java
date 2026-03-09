package com.example.kyc_system.enums;

/**
 * Lifecycle statuses of a KYC request.
 * Normal flow: PENDING → SUBMITTED → PROCESSING → VERIFIED
 * On failure: PENDING → SUBMITTED → PROCESSING → FAILED (can be re-submitted)
 */
public enum KycStatus {
    PENDING,
    SUBMITTED,
    PROCESSING,
    VERIFIED,
    FAILED
}