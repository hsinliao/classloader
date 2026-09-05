package com.hsin.sms.service;

import com.hsin.sms.spi.SmsRequest;

import java.util.Map;
import java.util.Optional;

/**
 * Static tenant -> provider mapping with an optional fallback provider.
 */
public final class DefaultTenantRouter implements SmsRouter {

    private final Map<String, String> tenantToProvider;
    private final String fallbackProvider;

    public DefaultTenantRouter(Map<String, String> tenantToProvider, String fallbackProvider) {
        this.tenantToProvider = Map.copyOf(tenantToProvider);
        this.fallbackProvider = fallbackProvider;
    }

    public DefaultTenantRouter(Map<String, String> tenantToProvider) {
        this(tenantToProvider, null);
    }

    @Override
    public Optional<String> resolve(String tenantId, SmsRequest request) {
        if (tenantId != null) {
            String mapped = tenantToProvider.get(tenantId);
            if (mapped != null) {
                return Optional.of(mapped);
            }
        }
        return Optional.ofNullable(fallbackProvider);
    }
}
