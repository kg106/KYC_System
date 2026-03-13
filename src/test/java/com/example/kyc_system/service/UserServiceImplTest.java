package com.example.kyc_system.service;

import com.example.kyc_system.context.TenantContext;
import com.example.kyc_system.dto.*;
import com.example.kyc_system.entity.*;
import com.example.kyc_system.repository.*;
import com.example.kyc_system.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("UserServiceImpl Unit Tests")
class UserServiceImplTest {

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
    @Mock
    private KycRequestRepository kycRequestRepository;
    @Mock
    private KycDocumentService kycDocumentService;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setTenant("test-tenant");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should get active user for regular user")
    void getActiveUser_RegularUser_ReturnsUser() {
        User user = User.builder().id(1L).tenantId("test-tenant").build();
        when(userRepository.findByIdAndTenantId(1L, "test-tenant")).thenReturn(Optional.of(user));

        User result = userService.getActiveUser(1L);

        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("Should get active user for super admin")
    void getActiveUser_SuperAdmin_ReturnsUser() {
        TenantContext.setTenant(TenantContext.SUPER_ADMIN_TENANT);
        User user = User.builder().id(1L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.getActiveUser(1L);

        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("Should return user by ID correctly")
    void getUserById_Found_ReturnsDTO() {
        User user = User.builder().id(1L).tenantId("test-tenant").email("test@test.com").build();
        when(userRepository.findByIdAndTenantId(1L, "test-tenant")).thenReturn(Optional.of(user));

        UserDTO result = userService.getUserById(1L);

        assertEquals("test@test.com", result.getEmail());
    }

    @Test
    @DisplayName("Should create user successfully")
    void createUser_Success_ReturnsDTO() {
        UserDTO dto = UserDTO.builder().email("new@test.com").password("password").build();
        when(userRepository.existsByEmailAndTenantId("new@test.com", "test-tenant")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserDTO result = userService.createUser(dto);

        assertNotNull(result.getId());
        assertEquals("new@test.com", result.getEmail());
        verify(userRepository).save(any(User.class));
        verify(roleRepository).findByName("ROLE_USER");
    }

    @Test
    @DisplayName("Should update user successfully")
    void updateUser_Success_ReturnsDTO() {
        User user = User.builder().id(1L).tenantId("test-tenant").name("Old Name").build();
        UserUpdateDTO updateDTO = UserUpdateDTO.builder().name("New Name").build();

        when(userRepository.findByIdAndTenantId(1L, "test-tenant")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserDTO result = userService.updateUser(1L, updateDTO);

        assertEquals("New Name", result.getName());
    }

    @Test
    @DisplayName("Should delete user and its documents")
    void deleteUser_Success() {
        User user = User.builder().id(1L).tenantId("test-tenant").build();
        KycRequest request = new KycRequest();
        KycDocument doc = new KycDocument();
        request.setKycDocuments(java.util.Set.of(doc));

        when(userRepository.findByIdAndTenantId(1L, "test-tenant")).thenReturn(Optional.of(user));
        when(kycRequestRepository.findByUserIdAndTenantId(1L, "test-tenant")).thenReturn(List.of(request));

        userService.deleteUser(1L);

        verify(kycDocumentService).deleteDocument(doc);
        verify(userRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Should return JWT token on successful login")
    void login_Success_ReturnsToken() {
        LoginDTO loginDto = new LoginDTO("user@test.com", "password");
        Authentication auth = mock(Authentication.class);
        User user = User.builder().email("user@test.com").tenantId("test-tenant").build();

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(auth, "test-tenant")).thenReturn("mock-jwt");

        String token = userService.login(loginDto);

        assertEquals("mock-jwt", token);
    }
}
