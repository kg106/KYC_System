package com.example.kyc_system.service;

import com.example.kyc_system.dto.PasswordResetDTO;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.repository.UserRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    // In-memory storage for tokens and rate limiting
    private final Map<String, TokenInfo> tokenStorage = new ConcurrentHashMap<>();
    private final Map<String, AttemptInfo> rateLimitStorage = new ConcurrentHashMap<>();

    private static class TokenInfo {
        String token;
        LocalDateTime expiry;

        TokenInfo(String token, LocalDateTime expiry) {
            this.token = token;
            this.expiry = expiry;
        }
    }

    private static class AttemptInfo {
        int count;
        LocalDate date;

        AttemptInfo(int count, LocalDate date) {
            this.count = count;
            this.date = date;
        }
    }

    @Override
    public String generateToken(String email) {
        checkRateLimit(email);

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            String token = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            tokenStorage.put(email, new TokenInfo(token, LocalDateTime.now().plusMinutes(15)));

            // Send real email in the background
            sendEmail(email, token);

            // Keep logging for debugging purposes
            log.info("**************************************************");
            log.info("PASSWORD RESET TOKEN FOR {}: {}", email, token);
            log.info("**************************************************");

            incrementAttempt(email);
        }

        // Always return the same message for security reasons
        return "If account exist, then email has been sent.";
    }

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

        // Remove token after successful reset
        tokenStorage.remove(email);
        log.info("Password successfully reset for user: {}", email);
    }

    private void checkRateLimit(String email) {
        AttemptInfo info = rateLimitStorage.get(email);
        LocalDate today = LocalDate.now();

        if (info != null && info.date.equals(today) && info.count >= 5) {
            throw new RuntimeException(
                    "Daily limit reached. You can only attempt to reset your password 5 times a day.");
        }
    }

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
