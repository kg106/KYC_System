package com.example.kyc_system.security;

import com.example.kyc_system.client.AuthServiceClient;
import com.example.kyc_system.dto.AuthUserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service("securityService")
@RequiredArgsConstructor
@Slf4j
public class SecurityService {

    private final AuthServiceClient authServiceClient;

    /**
     * Checks if the currently authenticated user can access data for the specified userId.
     */
    public boolean canAccessUser(String userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // 1. Super Admin has full global access
        if (hasRole(authentication, "ROLE_SUPER_ADMIN")) {
            return true;
        }

        // 2. Regular User can only access their own data
        if (isSelf(userId)) {
            return true;
        }

        // 3. Tenant Admin and Admin have access to users within their own tenant
        if (hasRole(authentication, "ROLE_TENANT_ADMIN") || hasRole(authentication, "ROLE_ADMIN")) {
            String adminTenantId = getTenantId(authentication);
            try {
                AuthUserDTO targetUser = authServiceClient.getUserById(userId);
                if (targetUser != null && targetUser.getTenantId() != null && targetUser.getTenantId().equals(adminTenantId)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Access denied: Target user not found or auth service error for id={}", userId);
            }
            log.warn("Access denied: Admin from tenant {} tried to access user bucket", adminTenantId);
            return false;
        }

        return false;
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    @SuppressWarnings("unchecked")
    private String getTenantId(Authentication auth) {
        if (auth.getDetails() instanceof Map) {
            return ((Map<String, String>) auth.getDetails()).get("tenantId");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String getCurrentUserId(Authentication auth) {
        if (auth.getDetails() instanceof Map) {
            return ((Map<String, String>) auth.getDetails()).get("userId");
        }
        return null;
    }

    /**
     * Checks if the currently authenticated user is the user specified by userId.
     */
    public boolean isSelf(String userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String currentUserId = getCurrentUserId(authentication);
        return userId != null && userId.equals(currentUserId);
    }
}
