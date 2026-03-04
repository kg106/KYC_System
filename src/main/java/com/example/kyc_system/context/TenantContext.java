package com.example.kyc_system.context;

public class TenantContext {

    // InheritableThreadLocal ensures tenant propagates
    // to child threads (KycWorker async processing)
    private static final InheritableThreadLocal<String> CURRENT_TENANT = new InheritableThreadLocal<>();

    public static final String SUPER_ADMIN_TENANT = "SUPER_ADMIN";

    public static void setTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenant() {
        return CURRENT_TENANT.get();
    }

    public static boolean isSuperAdmin() {
        return SUPER_ADMIN_TENANT.equals(CURRENT_TENANT.get());
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}