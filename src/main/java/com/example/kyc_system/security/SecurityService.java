package com.example.kyc_system.security;

import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service("securityService")
@RequiredArgsConstructor
public class SecurityService {

    private final UserService userService;

    public boolean canAccessUser(Long userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Admin has full access
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }

        // Check if current user email matches target user email
        String currentEmail = authentication.getName();
        try {
            UserDTO targetUser = userService.getUserById(userId);
            return targetUser.getEmail().equals(currentEmail);
        } catch (Exception e) {
            return false;
        }
    }
}
