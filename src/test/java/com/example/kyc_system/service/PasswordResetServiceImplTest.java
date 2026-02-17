package com.example.kyc_system.service;

import com.example.kyc_system.dto.PasswordResetDTO;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.javamail.JavaMailSender;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PasswordResetServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void resetPassword_ShouldThrowException_WhenPasswordsDoNotMatch() {
        PasswordResetDTO resetDTO = new PasswordResetDTO();
        resetDTO.setEmail("test@example.com");
        resetDTO.setToken("123456");
        resetDTO.setNewPassword("Password@123");
        resetDTO.setConfirmPassword("Different@123");

        // Set up a valid token in the internal map using reflection since it's private
        setupMockToken("test@example.com", "123456");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            passwordResetService.resetPassword(resetDTO);
        });

        assertEquals("Passwords do not match", exception.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    void resetPassword_ShouldUpdatePassword_WhenPasswordsMatch() throws Exception {
        PasswordResetDTO resetDTO = new PasswordResetDTO();
        resetDTO.setEmail("test@example.com");
        resetDTO.setToken("123456");
        resetDTO.setNewPassword("Strong@123");
        resetDTO.setConfirmPassword("Strong@123");

        setupMockToken("test@example.com", "123456");

        User user = new User();
        user.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        passwordResetService.resetPassword(resetDTO);

        verify(userRepository).save(user);
    }

    @SuppressWarnings("unchecked")
    private void setupMockToken(String email, String token) {
        try {
            Field tokenStorageField = PasswordResetServiceImpl.class.getDeclaredField("tokenStorage");
            tokenStorageField.setAccessible(true);
            Map<String, Object> tokenStorage = (Map<String, Object>) tokenStorageField.get(passwordResetService);

            // Using reflection to create TokenInfo since it's private
            Class<?> tokenInfoClass = Class
                    .forName("com.example.kyc_system.service.PasswordResetServiceImpl$TokenInfo");
            java.lang.reflect.Constructor<?> constructor = tokenInfoClass.getDeclaredConstructor(String.class,
                    LocalDateTime.class);
            constructor.setAccessible(true);
            Object tokenInfo = constructor.newInstance(token, LocalDateTime.now().plusMinutes(15));

            tokenStorage.put(email, tokenInfo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
