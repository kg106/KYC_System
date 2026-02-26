package com.example.kyc_system.controller;

import com.example.kyc_system.dto.LoginDTO;
import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.service.UserService;
import com.example.kyc_system.service.PasswordResetService;
import com.example.kyc_system.dto.PasswordResetRequestDTO;
import com.example.kyc_system.dto.PasswordResetDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for controller testing
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private com.example.kyc_system.security.JwtTokenProvider jwtTokenProvider;

    @MockBean
    private com.example.kyc_system.security.CustomAuthenticationEntryPoint authenticationEntryPoint;

    @MockBean
    private com.example.kyc_system.security.CustomAccessDeniedHandler accessDeniedHandler;

    @MockBean
    private com.example.kyc_system.service.RefreshTokenService refreshTokenService;

    @MockBean
    private com.example.kyc_system.util.CookieUtil cookieUtil;

    @MockBean
    private com.example.kyc_system.service.TokenBlacklistService tokenBlacklistService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void login_Success() throws Exception {
        // Given
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setEmail("test@example.com");
        loginDTO.setPassword("password");

        String token = "jwt-token";
        given(userService.login(any(LoginDTO.class))).willReturn(token);

        UserDTO mockUser = new UserDTO();
        mockUser.setId(1L);
        given(userService.getUserByEmail(anyString())).willReturn(mockUser);

        given(refreshTokenService.createRefreshToken(1L)).willReturn("mock-refresh-token");

        jakarta.servlet.http.Cookie mockCookie = new jakarta.servlet.http.Cookie("refreshToken", "mock-refresh-token");
        given(cookieUtil.createRefreshTokenCookie(anyString())).willReturn(mockCookie);

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(token));
    }

    @Test
    void register_Success() throws Exception {
        // Given
        UserDTO userDTO = new UserDTO();
        userDTO.setName("Test User");
        userDTO.setEmail("test@example.com");
        userDTO.setMobileNumber("1234567890");
        userDTO.setPassword("Strong@123");

        UserDTO savedUser = new UserDTO();
        savedUser.setId(1L);
        savedUser.setName("Test User");
        savedUser.setEmail("test@example.com");
        savedUser.setMobileNumber("1234567890");

        given(userService.createUser(any(UserDTO.class))).willReturn(savedUser);

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("Test User"));
    }

    @Test
    void generateResetToken_Success() throws Exception {
        PasswordResetRequestDTO requestDTO = new PasswordResetRequestDTO();
        requestDTO.setEmail("test@example.com");

        given(passwordResetService.generateToken(anyString()))
                .willReturn("If account exist, then email has been sent.");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_Success() throws Exception {
        PasswordResetDTO resetDTO = new PasswordResetDTO();
        resetDTO.setEmail("test@example.com");
        resetDTO.setToken("A1B2C3");
        resetDTO.setNewPassword("Strong@123");
        resetDTO.setConfirmPassword("Strong@123");

        mockMvc.perform(post("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetDTO)))
                .andExpect(status().isOk());
    }
}
