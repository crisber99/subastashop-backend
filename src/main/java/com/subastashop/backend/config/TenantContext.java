package com.subastashop.backend.config;

public class TenantContext {
    // ThreadLocal aísla el dato por cada hilo de ejecución (cada usuario tiene el suyo)
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}