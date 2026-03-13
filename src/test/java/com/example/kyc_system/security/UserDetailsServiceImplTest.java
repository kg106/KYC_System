package com.example.kyc_system.security;

import com.example.kyc_system.entity.Role;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.entity.UserRole;
import com.example.kyc_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UserDetailsServiceImpl Unit Tests")
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should return UserDetails when user is found")
    void loadUserByUsername_UserFound_ReturnsUserDetails() {
        Role role = Role.builder().name("ROLE_USER").build();
        UserRole userRole = UserRole.builder().role(role).build();
        User user = User.builder()
                .email("user@test.com")
                .passwordHash("hashedPass")
                .userRoles(Set.of(userRole))
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("user@test.com");

        assertNotNull(result);
        assertEquals("user@test.com", result.getUsername());
        assertEquals("hashedPass", result.getPassword());
        assertTrue(result.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    @DisplayName("Should throw exception when user is not found")
    void loadUserByUsername_UserNotFound_ThrowsException() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("missing@test.com"));
    }
}
