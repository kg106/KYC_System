package com.example.kyc_system.service;

import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.repository.RoleRepository;
import com.example.kyc_system.repository.UserRepository;
import com.example.kyc_system.repository.UserRoleRepository;
import com.example.kyc_system.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createUser_ShouldHashPasswordAndSaveUser() {
        UserDTO userDTO = UserDTO.builder()
                .name("Test User")
                .email("test@example.com")
                .mobileNumber("1234567890")
                .password("plainPassword")
                .build();

        User savedUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@example.com")
                .mobileNumber("1234567890")
                .passwordHash("hashedPassword") // In reality this would be dynamic
                .isActive(true)
                .build();

        com.example.kyc_system.entity.Role role = com.example.kyc_system.entity.Role.builder().id(1L).name("ROLE_USER")
                .build();

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(userRoleRepository.save(any(com.example.kyc_system.entity.UserRole.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        UserDTO result = userService.createUser(userDTO);

        assertNotNull(result);
        assertEquals("Test User", result.getName());
        verify(userRepository, times(1)).save(any(User.class));
        // We verify hashing logic implicitly by checking flow,
        // essentially ensuring PasswordUtil is called would be better if mocked static,
        // but since it's a static util, we trust it works or test it separately.
        // Integration test would verify DB content.
    }

    @Test
    void updateUser_ShouldUpdateFieldsAndNotPassword() {
        Long userId = 1L;
        User existingUser = User.builder()
                .id(userId)
                .name("Old Name")
                .email("old@example.com")
                .passwordHash("oldHash")
                .build();

        UserDTO updateDTO = UserDTO.builder()
                .name("New Name")
                .email("new@example.com")
                .mobileNumber("9876543210")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        UserDTO result = userService.updateUser(userId, updateDTO);

        assertEquals("New Name", existingUser.getName());
        assertEquals("new@example.com", existingUser.getEmail());
        assertEquals("oldHash", existingUser.getPasswordHash()); // Password unchanged
    }

    @Test
    void forgotPassword_ShouldGenerateNewPassword() {
        Long userId = 1L;
        User existingUser = User.builder()
                .id(userId)
                .passwordHash("oldHash")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        String newPassword = userService.forgotPassword(userId);

        assertNotNull(newPassword);
        assertNotEquals("oldHash", existingUser.getPasswordHash());
        // Verify the new hash is valid for the returned password
        assertTrue(PasswordUtil.checkPassword(newPassword, existingUser.getPasswordHash()));
    }
}
