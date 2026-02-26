package com.example.kyc_system.controller;

import com.example.kyc_system.dto.JwtAuthResponse;
import com.example.kyc_system.dto.LoginDTO;
import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.service.UserService;
import com.example.kyc_system.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CookieValue;
import jakarta.servlet.http.HttpServletResponse;
import com.example.kyc_system.service.RefreshTokenService;
import com.example.kyc_system.util.CookieUtil;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Authentication", description = "Endpoints for user registration and login")
public class AuthController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;
    private final com.example.kyc_system.service.TokenBlacklistService tokenBlacklistService;

    // Build Login REST API
    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.Operation(summary = "User Login", description = "Authenticates user and returns a JWT access token")
    public ResponseEntity<JwtAuthResponse> login(@jakarta.validation.Valid @RequestBody LoginDTO loginDTO,
            HttpServletResponse response) {
        String token = userService.login(loginDTO);
        UserDTO user = userService.getUserByEmail(loginDTO.getEmail());

        String refreshToken = refreshTokenService.createRefreshToken(user.getId());
        response.addCookie(cookieUtil.createRefreshTokenCookie(refreshToken));

        JwtAuthResponse jwtAuthResponse = new JwtAuthResponse();
        jwtAuthResponse.setAccessToken(token);
        jwtAuthResponse.setRefreshToken(refreshToken);

        return ResponseEntity.ok(jwtAuthResponse);
    }

    // Build Register REST API
    @PostMapping("/register")
    @io.swagger.v3.oas.annotations.Operation(summary = "User Registration", description = "Creates a new user account")
    public ResponseEntity<UserDTO> register(@jakarta.validation.Valid @RequestBody UserDTO userDTO) {
        UserDTO savedUser = userService.createUser(userDTO);
        return new ResponseEntity<>(savedUser, HttpStatus.CREATED);
    }

    @PostMapping("/forgot-password")
    @io.swagger.v3.oas.annotations.Operation(summary = "Forgot Password (Step 1)", description = "Enter your email to receive a 6-character reset token. This token will be sent to your registered email address and is valid for 15 minutes.")
    public ResponseEntity<String> generateResetToken(
            @jakarta.validation.Valid @RequestBody com.example.kyc_system.dto.PasswordResetRequestDTO requestDTO) {
        String message = passwordResetService.generateToken(requestDTO.getEmail());
        return ResponseEntity.ok(message);
    }

    @PostMapping("/change-password")
    @io.swagger.v3.oas.annotations.Operation(summary = "Change Password (Step 2)", description = "Use the token received in your email to set a new password. Make sure the 'newPassword' and 'confirmPassword' fields match exactly.")
    public ResponseEntity<String> resetPassword(
            @jakarta.validation.Valid @RequestBody com.example.kyc_system.dto.PasswordResetDTO resetDTO) {
        passwordResetService.resetPassword(resetDTO);
        return ResponseEntity.ok("Password successfully reset");
    }

    @PostMapping("/refresh")
    @io.swagger.v3.oas.annotations.Operation(summary = "Refresh Token", description = "Uses the HttpOnly refresh token cookie to get a new access token")
    public ResponseEntity<JwtAuthResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken, HttpServletResponse response) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            JwtAuthResponse authResponse = refreshTokenService.processRefreshToken(refreshToken);
            response.addCookie(cookieUtil.createRefreshTokenCookie(authResponse.getRefreshToken()));
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    @io.swagger.v3.oas.annotations.Operation(summary = "User Logout", description = "Revokes the refresh token, blacklists the access token, and clears the cookie")
    public ResponseEntity<String> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken,
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response) {

        // Blacklist Access Token
        String bearerToken = request.getHeader("Authorization");
        if (org.springframework.util.StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String accessToken = bearerToken.substring(7);
            tokenBlacklistService.blacklistToken(accessToken);
        }

        if (refreshToken != null && !refreshToken.isEmpty()) {
            try {
                String familyId = refreshToken.split(":")[0];
                refreshTokenService.revokeFamily(familyId);
            } catch (Exception e) {
                // Ignore parsing errors on logout
            }
        }
        response.addCookie(cookieUtil.createEmptyCookie());
        return ResponseEntity.ok("Logged out successfully");
    }
}
