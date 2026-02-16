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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Authentication", description = "Endpoints for user registration and login")
public class AuthController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;

    // Build Login REST API
    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.Operation(summary = "User Login", description = "Authenticates user and returns a JWT access token")
    public ResponseEntity<JwtAuthResponse> login(@jakarta.validation.Valid @RequestBody LoginDTO loginDTO) {
        String token = userService.login(loginDTO);

        JwtAuthResponse jwtAuthResponse = new JwtAuthResponse();
        jwtAuthResponse.setAccessToken(token);

        return ResponseEntity.ok(jwtAuthResponse);
    }

    // Build Register REST API
    @PostMapping("/register")
    @io.swagger.v3.oas.annotations.Operation(summary = "User Registration", description = "Creates a new user account")
    public ResponseEntity<UserDTO> register(@jakarta.validation.Valid @RequestBody UserDTO userDTO) {
        UserDTO savedUser = userService.createUser(userDTO);
        return new ResponseEntity<>(savedUser, HttpStatus.CREATED);
    }

    @PostMapping("/forgot-password/generate-token")
    @io.swagger.v3.oas.annotations.Operation(summary = "Generate Reset Token", description = "Generates a password reset token for a given email address")
    public ResponseEntity<String> generateResetToken(
            @jakarta.validation.Valid @RequestBody com.example.kyc_system.dto.PasswordResetRequestDTO requestDTO) {
        String message = passwordResetService.generateToken(requestDTO.getEmail());
        return ResponseEntity.ok(message);
    }

    @PostMapping("/forgot-password/reset")
    @io.swagger.v3.oas.annotations.Operation(summary = "Reset Password", description = "Resets the user's password using a valid reset token")
    public ResponseEntity<String> resetPassword(
            @jakarta.validation.Valid @RequestBody com.example.kyc_system.dto.PasswordResetDTO resetDTO) {
        passwordResetService.resetPassword(resetDTO);
        return ResponseEntity.ok("Password successfully reset");
    }
}
