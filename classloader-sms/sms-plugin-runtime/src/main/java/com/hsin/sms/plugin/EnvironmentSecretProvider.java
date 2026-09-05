package com.hsin.sms.plugin;

import java.util.Optional;

/** Reads secrets from environment variables. */
public final class EnvironmentSecretProvider implements SecretProvider {

    private final String prefix;

    public EnvironmentSecretProvider() {
        this("SMS_SECRET_");
    }

    public EnvironmentSecretProvider(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public Optional<String> resolve(String key) {
        String normalized = key.toUpperCase().replace('-', '_').replace('.', '_');
        return Optional.ofNullable(System.getenv(prefix + normalized));
    }
}
