package com.example.kyc_system.service;

import com.example.kyc_system.dto.PasswordResetDTO;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("PasswordResetServiceImpl Unit Tests")
class PasswordResetServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should generate token and send email if user exists")
    void generateToken_UserExists_SendsEmail() {
        String email = "user@test.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(User.builder().email(email).build()));

        String result = passwordResetService.generateToken(email);

        assertEquals("If account exist, then email has been sent.", result);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Should not send email but return same message if user missing")
    void generateToken_UserMissing_SilentSuccess() {
        String email = "missing@test.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        String result = passwordResetService.generateToken(email);

        assertEquals("If account exist, then email has been sent.", result);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Should reset password successfully with valid token")
    void resetPassword_ValidToken_Success() {
        String email = "user@test.com";
        String token = "ABC123";
        PasswordResetDTO dto = new PasswordResetDTO(email, token, "NewPass123", "NewPass123");
        User user = User.builder().id(1L).email(email).build();

        // Inject token into internal storage via Reflection for testing resetPassword
        // independently
        Map<String, Object> tokenStorage = (Map<String, Object>) ReflectionTestUtils.getField(passwordResetService,
                "tokenStorage");
        try {
            Class<?> tokenInfoClass = Class
                    .forName("com.example.kyc_system.service.PasswordResetServiceImpl$TokenInfo");
            Object tokenInfo = tokenInfoClass.getDeclaredConstructors()[0].newInstance(token,
                    LocalDateTime.now().plusMinutes(15));
            tokenStorage.put(email, tokenInfo);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        passwordResetService.resetPassword(dto);

        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllForUser(1L);
        assertNull(tokenStorage.get(email));
    }

    @Test
    @DisplayName("Should throw exception if token is invalid")
    void resetPassword_InvalidToken_ThrowsException() {
        PasswordResetDTO dto = new PasswordResetDTO("user@test.com", "WRONG", "pass", "pass");
        // tokenStorage is empty by default
        assertThrows(RuntimeException.class, () -> passwordResetService.resetPassword(dto));
    }

    @Test
    @DisplayName("Should throw exception if passwords don't match")
    void resetPassword_MismatchedPasswords_ThrowsException() {
        String email = "user@test.com";
        String token = "TOKEN";
        PasswordResetDTO dto = new PasswordResetDTO(email, token, "pass1", "pass2");

        // Inject valid token
        Map<String, Object> tokenStorage = (Map<String, Object>) ReflectionTestUtils.getField(passwordResetService,
                "tokenStorage");
        try {
            Class<?> tokenInfoClass = Class
                    .forName("com.example.kyc_system.service.PasswordResetServiceImpl$TokenInfo");
            Object tokenInfo = tokenInfoClass.getDeclaredConstructors()[0].newInstance(token,
                    LocalDateTime.now().plusMinutes(15));
            tokenStorage.put(email, tokenInfo);
        } catch (Exception e) {
        }

        assertThrows(RuntimeException.class, () -> passwordResetService.resetPassword(dto));
    }
}
