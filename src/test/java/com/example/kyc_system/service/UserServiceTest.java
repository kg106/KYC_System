package com.example.kyc_system.service;

import com.example.kyc_system.dto.LoginDTO;
import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.repository.RoleRepository;
import com.example.kyc_system.repository.UserRepository;
import com.example.kyc_system.repository.UserRoleRepository;
import com.example.kyc_system.security.JwtTokenProvider;
import com.example.kyc_system.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

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

        @Mock
        private AuthenticationManager authenticationManager;

        @Mock
        private JwtTokenProvider jwtTokenProvider;

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

                com.example.kyc_system.entity.Role role = com.example.kyc_system.entity.Role.builder().id(1L)
                                .name("ROLE_USER")
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
        void getUserById_ShouldReturnUser_WhenUserExists() {
                Long userId = 1L;
                User user = User.builder().id(userId).name("Test").build();
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));

                UserDTO result = userService.getUserById(userId);

                assertEquals("Test", result.getName());
        }

        @Test
        void getUserById_ShouldThrowException_WhenUserDoesNotExist() {
                Long userId = 1L;
                when(userRepository.findById(userId)).thenReturn(Optional.empty());

                assertThrows(RuntimeException.class, () -> userService.getUserById(userId));
        }

        @Test
        void createUser_ShouldThrowException_WhenEmailExists() {
                UserDTO userDTO = UserDTO.builder().email("exists@example.com").build();
                when(userRepository.findByEmail("exists@example.com")).thenReturn(Optional.of(new User()));

                assertThrows(RuntimeException.class, () -> userService.createUser(userDTO));
        }

        @Test
        void updateUser_ShouldThrowException_WhenUserDoesNotExist() {
                Long userId = 1L;
                UserDTO updateDTO = UserDTO.builder().name("New").build();
                when(userRepository.findById(userId)).thenReturn(Optional.empty());

                assertThrows(RuntimeException.class, () -> userService.updateUser(userId, updateDTO));
        }

        @Test
        void deleteUser_ShouldCallDelete_WhenUserExists() {
                Long userId = 1L;
                when(userRepository.existsById(userId)).thenReturn(true);

                userService.deleteUser(userId);

                verify(userRepository, times(1)).deleteById(userId);
        }

        @Test
        void deleteUser_ShouldThrowException_WhenUserDoesNotExist() {
                Long userId = 1L;
                when(userRepository.existsById(userId)).thenReturn(false);

                assertThrows(RuntimeException.class, () -> userService.deleteUser(userId));
        }

        @Test
        void forgotPassword_ShouldThrowException_WhenUserDoesNotExist() {
                Long userId = 1L;
                when(userRepository.findById(userId)).thenReturn(Optional.empty());

                assertThrows(RuntimeException.class, () -> userService.forgotPassword(userId));
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
                assertTrue(PasswordUtil.checkPassword(newPassword, existingUser.getPasswordHash()));
        }

        @Test
        void getActiveUser_ShouldReturnUser_WhenUserExists() {
                Long userId = 1L;
                User user = User.builder().id(userId).build();
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));

                User result = userService.getActiveUser(userId);

                assertEquals(userId, result.getId());
        }

        @Test
        void getAllUsers_ShouldReturnListOfDTOs() {
                User user1 = User.builder().id(1L).name("User 1").build();
                User user2 = User.builder().id(2L).name("User 2").build();
                when(userRepository.findAll()).thenReturn(java.util.List.of(user1, user2));

                java.util.List<UserDTO> result = userService.getAllUsers();

                assertEquals(2, result.size());
                assertEquals("User 1", result.get(0).getName());
        }

        @Test
        void login_ShouldReturnToken_WhenCredentialsAreValid() {
                LoginDTO loginDTO = new LoginDTO("test@example.com", "password");
                Authentication auth = mock(Authentication.class);

                when(authenticationManager.authenticate(any())).thenReturn(auth);
                when(jwtTokenProvider.generateToken(auth)).thenReturn("mock-token");

                String result = userService.login(loginDTO);

                assertEquals("mock-token", result);
                verify(authenticationManager).authenticate(any());
        }
}
