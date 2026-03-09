package com.example.kyc_system.controller;

import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.kyc_system.dto.UserSearchDTO;
import java.util.List;

import com.example.kyc_system.service.*;
import io.swagger.v3.oas.annotations.*;
import jakarta.validation.*;
import com.example.kyc_system.dto.*;
import jakarta.servlet.http.*;
import org.springframework.util.*;
import io.swagger.v3.oas.annotations.tags.*;
import org.springdoc.core.annotations.*;

/**
 * REST controller for user profile management.
 * - Admin can list all users, search, and delete
 * - Users can view and update their own profiles
 * - Access control via @PreAuthorize and @securityService.canAccessUser
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "Endpoints for managing user profiles (Admin and Self-service)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Admin-only: lists all users within the current tenant. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get All Users", description = "Retrieves a listing of all registered users. Restricted to ADMIN.")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    /** Available to self or admin — retrieves a specific user's profile. */
    @GetMapping("{userId}")
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @Operation(summary = "Get User by ID", description = "Retrieves profile details for a specific user. Available to self or ADMIN.")
    public ResponseEntity<UserDTO> getUserById(@PathVariable("userId") Long userId) {
        UserDTO userDto = userService.getUserById(userId);
        return new ResponseEntity<>(userDto, HttpStatus.OK);
    }

    /** Partial update of user profile — only non-null fields are updated. */
    @PatchMapping("{userId}")
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @Operation(summary = "Update User Profile", description = "Performs a partial update of a user's profile. Available to self or ADMIN.")
    public ResponseEntity<UserDTO> updateUser(@PathVariable("userId") Long userId,
            @Valid @RequestBody UserUpdateDTO updatedUser) {
        UserDTO userDto = userService.updateUser(userId, updatedUser);
        return new ResponseEntity<>(userDto, HttpStatus.OK);
    }

    /**
     * Admin-only: permanently deletes a user and their associated KYC documents
     * from disk.
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete User", description = "Permanently deletes a user from the system. Restricted to ADMIN.")
    public ResponseEntity<String> deleteUser(@PathVariable("userId") Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok("User deleted successfully");
    }

    /**
     * Admin-only: search users with optional filters (name, email, mobile, active
     * status).
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search Users", description = "Search users with filters and pagination. Restricted to ADMIN.")
    public ResponseEntity<Page<UserDTO>> searchUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String mobileNumber,
            @RequestParam(required = false) Boolean isActive,
            @ParameterObject Pageable pageable) {

        UserSearchDTO searchDTO = UserSearchDTO.builder()
                .name(name)
                .email(email)
                .mobileNumber(mobileNumber)
                .isActive(isActive)
                .build();

        Page<UserDTO> users = userService.searchUsers(searchDTO, pageable);
        return ResponseEntity.ok(users);
    }

}
