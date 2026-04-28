package com.example.kyc_system.controller;

import com.example.kyc_system.dto.JwtAuthResponse;
import com.example.kyc_system.dto.LoginDTO;
import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.service.UserService;
import com.example.kyc_system.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import io.swagger.v3.oas.annotations.tags.*;

import com.example.kyc_system.dto.*;
import com.example.kyc_system.service.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.servlet.http.*;
import org.springframework.util.*;

/**
 * Authentication Controller.
 * Provides endpoints for user registration, login, logout, and password management.
 * Integrated with JWT and Refresh Token mechanisms.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Endpoints for user registration and login")
public class AuthController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Authenticates a user and returns combined JWT and Refresh tokens.
     * Sets the Refresh Token as a secure HttpOnly cookie.
     *
     * @param loginDTO credentials (email, password)
     * @param response the HTTP response to attach the cookie to
     * @return a response containing the JWT and refresh token
     */
    @PostMapping("/login")
    @Operation(summary = "User Login", description = "Authenticates user and returns a JWT access token")
    public ResponseEntity<JwtAuthResponse> login(@Valid @RequestBody LoginDTO loginDTO,
            HttpServletResponse response) {
        String token = userService.login(loginDTO);
        UserDTO user = userService.getUserByEmailDirect(loginDTO.getEmail());

        String refreshToken = refreshTokenService.createRefreshTokenDirect(user.getId());
        response.addCookie(cookieUtil.createRefreshTokenCookie(refreshToken));

        JwtAuthResponse jwtAuthResponse = new JwtAuthResponse();
        jwtAuthResponse.setAccessToken(token);
        jwtAuthResponse.setRefreshToken(refreshToken);

        log.info("User logged in: {}", loginDTO.getEmail());
        return ResponseEntity.ok(jwtAuthResponse);
    }

    /**
     * Registers a new user account in the system.
     *
     * @param userDTO basic user profile information
     * @return the created user profile
     */
    @PostMapping("/register")
    @Operation(summary = "User Registration", description = "Creates a new user account")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody UserDTO userDTO) {
        UserDTO savedUser = userService.createUser(userDTO);
        log.info("User registered: email={}", savedUser.getEmail());
        return new ResponseEntity<>(savedUser, HttpStatus.CREATED);
    }

    /**
     * Step 1: Initiates a password reset by generating a 6-character numeric token.
     * The token is sent to the user's registered email address.
     *
     * @param requestDTO containing the user's email
     * @return success message
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot Password (Step 1)", description = "Enter your email to receive a 6-character reset token. This token will be sent to your registered email address and is valid for 15 minutes.")
    public ResponseEntity<String> generateResetToken(
            @Valid @RequestBody PasswordResetRequestDTO requestDTO) {
        String message = passwordResetService.generateToken(requestDTO.getEmail());
        return ResponseEntity.ok(message);
    }

    /**
     * Step 2: Finalizes password reset using the email token and a new password.
     *
     * @param resetDTO containing the token and new password
     * @return success message
     */
    @PostMapping("/change-password")
    @Operation(summary = "Change Password (Step 2)", description = "Use the token received in your email to set a new password. Make sure the 'newPassword' and 'confirmPassword' fields match exactly.")
    public ResponseEntity<String> resetPassword(
            @Valid @RequestBody PasswordResetDTO resetDTO) {
        passwordResetService.resetPassword(resetDTO);
        return ResponseEntity.ok("Password successfully reset");
    }

    /**
     * Uses a valid Refresh Token (from cookie) to issue a new JWT and Refresh Token.
     * Implements token rotation for enhanced security.
     *
     * @param refreshToken the rotation token from the HTTP cookie
     * @param response the HTTP response to attach the new cookie to
     * @return new set of credentials
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh Token", description = "Uses the HttpOnly refresh token cookie to get a new access token")
    public ResponseEntity<JwtAuthResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken, HttpServletResponse response) {
        if (refreshToken == null) {
            log.warn("Token refresh attempt with no refresh token cookie");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            JwtAuthResponse authResponse = refreshTokenService.processRefreshToken(refreshToken);
            response.addCookie(cookieUtil.createRefreshTokenCookie(authResponse.getRefreshToken()));
            log.debug("Token refreshed successfully");
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Logs out a user by:
     * 1. Blacklisting the current Access Token.
     * 2. Revoking the entire Refresh Token family.
     * 3. Clearing the Refresh Token cookie.
     *
     * @param refreshToken the current rotation token
     * @param request the HTTP request (for Authorization header)
     * @param response the HTTP response (to clear cookie)
     * @return logout confirmation
     */
    @PostMapping("/logout")
    @Operation(summary = "User Logout", description = "Revokes the refresh token, blacklists the access token, and clears the cookie")
    public ResponseEntity<String> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response) {

        // Blacklist Access Token
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
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
        log.info("User logged out");
        return ResponseEntity.ok("Logged out successfully");
    }
}
