package com.example.kyc_system.service;

import com.example.kyc_system.dto.PasswordResetDTO;

public interface PasswordResetService {
    String generateToken(String email);

    void resetPassword(PasswordResetDTO resetDTO);
}
