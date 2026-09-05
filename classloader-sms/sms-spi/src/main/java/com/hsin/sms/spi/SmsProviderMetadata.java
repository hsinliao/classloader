package com.hsin.sms.spi;

import java.util.Objects;

/**
 * Immutable descriptive metadata supplied by an {@link SmsProvider}.
 */
public record SmsProviderMetadata(
        String providerId,
        String name,
        String version,
        String vendor,
        String description) {

    public SmsProviderMetadata {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(vendor, "vendor");
        description = description == null ? "" : description;
        if (providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
    }

    public static SmsProviderMetadata of(String providerId, String name, String version, String vendor) {
        return new SmsProviderMetadata(providerId, name, version, vendor, "");
    }
}
