package com.example.kyc_system.context;

/**
 * Thread-local holder for the current tenant ID in a multi-tenant system.
 * Used to scope all database queries and operations to a specific tenant.
 *
 * Uses InheritableThreadLocal so tenant context propagates to child threads
 * (e.g., async KYC processing workers).
 */
public class TenantContext {

    // InheritableThreadLocal ensures tenant propagates
    // to child threads (KycWorker async processing)
    private static final InheritableThreadLocal<String> CURRENT_TENANT = new InheritableThreadLocal<>();

    /**
     * Special constant used when the superadmin is operating (bypasses tenant
     * scoping).
     */
    public static final String SUPER_ADMIN_TENANT = "SUPER_ADMIN";

    public static void setTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * Returns true if the current user is a superadmin (not scoped to any tenant).
     */
    public static boolean isSuperAdmin() {
        return SUPER_ADMIN_TENANT.equals(CURRENT_TENANT.get());
    }

    /** Must be called in a finally block to prevent thread-pool tenant leaks. */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}