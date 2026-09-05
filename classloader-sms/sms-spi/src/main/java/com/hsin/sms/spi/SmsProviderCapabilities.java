package com.hsin.sms.spi;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable declaration of what a provider can do.
 */
public final class SmsProviderCapabilities {

    private final Set<SmsCapability> capabilities;

    private SmsProviderCapabilities(Set<SmsCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw new IllegalArgumentException("capabilities must not be empty");
        }
        this.capabilities = Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    public static SmsProviderCapabilities of(SmsCapability first, SmsCapability... rest) {
        EnumSet<SmsCapability> set = EnumSet.of(first);
        set.addAll(Arrays.asList(rest));
        return new SmsProviderCapabilities(set);
    }

    /** Builds capabilities from a non-empty set. */
    public static SmsProviderCapabilities of(Set<SmsCapability> capabilities) {
        return new SmsProviderCapabilities(Objects.requireNonNull(capabilities, "capabilities"));
    }

    /** Unmodifiable set of supported capabilities. */
    public Set<SmsCapability> capabilities() {
        return capabilities;
    }

    /** Whether the given capability is declared. */
    public boolean supports(SmsCapability capability) {
        return capability != null && capabilities.contains(capability);
    }

    @Override
    public String toString() {
        return capabilities.stream().map(Enum::name).sorted().collect(Collectors.joining(",", "[", "]"));
    }
}
