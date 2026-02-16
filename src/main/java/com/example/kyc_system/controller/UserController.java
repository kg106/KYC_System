package com.example.kyc_system.controller;

import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@io.swagger.v3.oas.annotations.tags.Tag(name = "User Management", description = "Endpoints for managing user profiles (Admin and Self-service)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get All Users", description = "Retrieves a listing of all registered users. Restricted to ADMIN.")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @GetMapping("{id}")
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get User by ID", description = "Retrieves profile details for a specific user. Available to self or ADMIN.")
    public ResponseEntity<UserDTO> getUserById(@PathVariable("id") Long userId) {
        UserDTO userDto = userService.getUserById(userId);
        return new ResponseEntity<>(userDto, HttpStatus.OK);
    }

    @PatchMapping("{id}")
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @io.swagger.v3.oas.annotations.Operation(summary = "Update User Profile", description = "Performs a partial update of a user's profile. Available to self or ADMIN.")
    public ResponseEntity<UserDTO> updateUser(@PathVariable("id") Long userId,
            @RequestBody UserDTO updatedUser) {
        UserDTO userDto = userService.updateUser(userId, updatedUser);
        return new ResponseEntity<>(userDto, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Delete User", description = "Permanently deletes a user from the system. Restricted to ADMIN.")
    public ResponseEntity<String> deleteUser(@PathVariable("id") Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok("User deleted successfully");
    }

}
