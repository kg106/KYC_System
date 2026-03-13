package com.example.kyc_system.security;

import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SecurityService Unit Tests")
class SecurityServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private Authentication authentication;
    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private SecurityService securityService;

    private MockedStatic<SecurityContextHolder> mockedSecurityContextHolder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockedSecurityContextHolder = mockStatic(SecurityContextHolder.class);
        mockedSecurityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityContextHolder.close();
    }

    @Test
    @DisplayName("Super Admin should have access to any user")
    void canAccessUser_SuperAdmin_ReturnsTrue() {
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
                .when(authentication).getAuthorities();

        assertTrue(securityService.canAccessUser(100L));
        verify(userService, never()).getUserById(anyLong());
    }

    @Test
    @DisplayName("Tenant Admin should have access to user in same tenant")
    void canAccessUser_SameTenant_ReturnsTrue() {
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")))
                .when(authentication).getAuthorities();
        when(authentication.getDetails()).thenReturn(Map.of("tenantId", "tenant-a"));

        UserDTO targetUser = UserDTO.builder().id(1L).tenantId("tenant-a").build();
        when(userService.getUserById(1L)).thenReturn(targetUser);

        assertTrue(securityService.canAccessUser(1L));
    }

    @Test
    @DisplayName("Tenant Admin should not have access to user in different tenant")
    void canAccessUser_DifferentTenant_ReturnsFalse() {
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")))
                .when(authentication).getAuthorities();
        when(authentication.getDetails()).thenReturn(Map.of("tenantId", "tenant-a"));

        UserDTO targetUser = UserDTO.builder().id(1L).tenantId("tenant-b").build();
        when(userService.getUserById(1L)).thenReturn(targetUser);

        assertFalse(securityService.canAccessUser(1L));
    }

    @Test
    @DisplayName("User should have access to their own data")
    void canAccessUser_Self_ReturnsTrue() {
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("me@test.com");

        UserDTO targetUser = UserDTO.builder().id(1L).email("me@test.com").build();
        when(userService.getUserById(1L)).thenReturn(targetUser);

        assertTrue(securityService.canAccessUser(1L));
    }

    @Test
    @DisplayName("isSelf should return true if emails match")
    void isSelf_Match_ReturnsTrue() {
        when(authentication.getName()).thenReturn("me@test.com");
        UserDTO targetUser = UserDTO.builder().id(1L).email("me@test.com").build();
        when(userService.getUserById(1L)).thenReturn(targetUser);

        assertTrue(securityService.isSelf(1L));
    }
}
