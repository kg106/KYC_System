package com.example.kyc_system.service.impl;

import com.example.kyc_system.dto.PasswordResetDTO;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.repository.UserRepository;
import com.example.kyc_system.service.PasswordResetService;
import com.example.kyc_system.service.RefreshTokenService;
import com.example.kyc_system.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of PasswordResetService.
 * Handles the secure lifecycle of forgotten password recovery:
 * - Generation of short-lived (15m) numeric OTP tokens.
 * - Rate limiting (max 5 attempts per day) to prevent brute force.
 * - Async email dispatch.
 * - Post-reset session revocation (logging out all devices).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final RefreshTokenService refreshTokenService;

    /** In-memory storage for active reset tokens (Key: Email). */
    private final Map<String, TokenInfo> tokenStorage = new ConcurrentHashMap<>();
    /** In-memory storage for rate limiting tracking (Key: Email). */
    private final Map<String, AttemptInfo> rateLimitStorage = new ConcurrentHashMap<>();

    /** Model for token metadata. */
    private static class TokenInfo {
        String token;
        LocalDateTime expiry;

        TokenInfo(String token, LocalDateTime expiry) {
            this.token = token;
            this.expiry = expiry;
        }
    }

    /** Model for attempt tracking. */
    private static class AttemptInfo {
        int count;
        LocalDate date;

        AttemptInfo(int count, LocalDate date) {
            this.count = count;
            this.date = date;
        }
    }

    /**
     * Generates a 6-digit reset token if the email exists.
     * Enforces rate limiting before processing.
     * Always returns a generic success message to prevent user enumeration.
     *
     * @param email user email
     * @return generic feedback message
     */
    @Override
    public String generateToken(String email) {
        checkRateLimit(email);

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            String token = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            tokenStorage.put(email, new TokenInfo(token, LocalDateTime.now().plusMinutes(15)));

            // Send real email in the background
            sendEmail(email, token);

            log.info("PASSWORD RESET TOKEN FOR {}: {}", email, token);
            incrementAttempt(email);
        }

        return "If account exist, then email has been sent.";
    }

    /**
     * Asynchronously sends the reset token email.
     */
    @Async
    protected void sendEmail(String to, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Password Reset Token - KYC System");
            message.setText("Your password reset token is: " + token + "\n\n" +
                    "This token will expire in 15 minutes.\n" +
                    "If you did not request this, please ignore this email.");
            mailSender.send(message);
            log.info("Password reset email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", to, e.getMessage());
        }
    }

    /**
     * Validates the token and updates the user's password.
     * Revokes all active refresh tokens for the user upon successful reset.
     *
     * @param resetDTO contains email, new password, and token
     */
    @Override
    @Transactional
    public void resetPassword(PasswordResetDTO resetDTO) {
        String email = resetDTO.getEmail();
        TokenInfo info = tokenStorage.get(email);

        if (info == null || !info.token.equals(resetDTO.getToken())) {
            throw new RuntimeException("Invalid token");
        }

        if (!resetDTO.getNewPassword().equals(resetDTO.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        if (LocalDateTime.now().isAfter(info.expiry)) {
            tokenStorage.remove(email);
            throw new RuntimeException("Token has expired");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPasswordHash(PasswordUtil.hashPassword(resetDTO.getNewPassword()));
        userRepository.save(user);

        // Revoke ALL refresh tokens for this user (log out from every device)
        refreshTokenService.revokeAllForUser(user.getId());

        // Remove token after successful reset
        tokenStorage.remove(email);
        log.info("Password successfully reset for user: {}. All sessions revoked.", email);
    }

    /** Helper to check if the user has exceeded their daily reset limit. */
    private void checkRateLimit(String email) {
        AttemptInfo info = rateLimitStorage.get(email);
        LocalDate today = LocalDate.now();

        if (info != null && info.date.equals(today) && info.count >= 5) {
            throw new RuntimeException(
                    "Daily limit reached. You can only attempt to reset your password 5 times a day.");
        }
    }

    /** Increments the attempt counter for rate limiting. */
    private void incrementAttempt(String email) {
        AttemptInfo info = rateLimitStorage.get(email);
        LocalDate today = LocalDate.now();

        if (info == null || !info.date.equals(today)) {
            rateLimitStorage.put(email, new AttemptInfo(1, today));
        } else {
            info.count++;
        }
    }
}
