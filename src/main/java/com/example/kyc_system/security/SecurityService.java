package com.example.kyc_system.security;

import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service("securityService")
@RequiredArgsConstructor
@Slf4j
public class SecurityService {

    private final UserService userService;

    /**
     * Checks if the currently authenticated user can access data for the specified userId.
     * Access is granted if:
     * 1. The user is a SUPER_ADMIN (global access).
     * 2. The user is a TENANT_ADMIN or ADMIN and the target user belongs to the same tenant.
     * 3. The user is a regular user and is accessing their own data.
     *
     * @param userId the ID of the user being accessed
     * @return true if access is granted, false otherwise
     */
    public boolean canAccessUser(Long userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.trace("Access denied: Not authenticated");
            return false;
        }

        // 1. Super Admin has full global access (unscoped)
        if (hasRole(authentication, "ROLE_SUPER_ADMIN")) {
            log.debug("Access granted: SUPER_ADMIN for userId={}", userId);
            return true;
        }

        // 2. Fetch target user to check tenant
        UserDTO targetUser;
        try {
            // Note: We use getUserByEmailDirect or a similar bypass if needed,
            // but here we check if the target user belongs to the same tenant as the admin.
            targetUser = userService.getUserById(userId);
        } catch (Exception e) {
            log.warn("Access denied: Target user with id={} not found", userId);
            return false;
        }

        // 3. Tenant Admin and Admin have access to users within their own tenant
        if (hasRole(authentication, "ROLE_TENANT_ADMIN") || hasRole(authentication, "ROLE_ADMIN")) {
            // Get current user's tenant (passed in JWT details by JwtAuthenticationFilter)
            String adminTenantId = getTenantId(authentication);
            boolean result = targetUser.getTenantId() != null && targetUser.getTenantId().equals(adminTenantId);
            if (!result) {
                log.warn("Access denied: Admin from tenant {} tried to access user from bucket {}", adminTenantId,
                        targetUser.getTenantId());
            }
            return result;
        }

        // 4. Regular User can only access their own data
        String currentEmail = authentication.getName();
        boolean result = targetUser.getEmail().equals(currentEmail);
        if (!result) {
            log.warn("Access denied: User {} tried to access user {}", currentEmail, targetUser.getEmail());
        }
        return result;
    }

    /**
     * Checks if the authentication object contains a specific role.
     *
     * @param auth the authentication object
     * @param role the role name to check (e.g., "ROLE_ADMIN")
     * @return true if the role is present, false otherwise
     */
    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    /**
     * Safely retrieves the tenantId from the authentication details map.
     *
     * @param auth the authentication object
     * @return the tenantId string or null if not found
     */
    @SuppressWarnings("unchecked")
    private String getTenantId(Authentication auth) {
        if (auth.getDetails() instanceof java.util.Map) {
            return ((java.util.Map<String, String>) auth.getDetails()).get("tenantId");
        }
        return null;
    }

    /**
     * Checks if the currently authenticated user is the user specified by userId.
     * This is stricter than canAccessUser as it doesn't automatically grant access to admins.
     *
     * @param userId the ID of the user to check against
     * @return true if the current user is the target user, false otherwise
     */
    public boolean isSelf(Long userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Check if current user email matches target user email
        // Unlike canAccessUser, we don't automatically grant access to ADMIN here
        String currentEmail = authentication.getName();
        try {
            UserDTO targetUser = userService.getUserById(userId);
            return targetUser.getEmail().equals(currentEmail);
        } catch (Exception e) {
            return false;
        }
    }
}
