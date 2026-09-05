package com.hsin.sms.plugin;

import java.util.Optional;

/**
 * Resolves secrets for plugins without exposing a storage implementation to the SPI.
 * Production deployments can chain environment, file, KMS or Vault providers.
 */
public interface SecretProvider {

    Optional<String> resolve(String key);

    static SecretProvider empty() {
        return key -> Optional.empty();
    }
}
