package io.github.filipopov.gateway.api.common;

/** The request header that selects the tenant (bank). */
public final class TenantHeader {

    public static final String NAME = "X-Tenant-Id";

    private TenantHeader() {
    }
}
