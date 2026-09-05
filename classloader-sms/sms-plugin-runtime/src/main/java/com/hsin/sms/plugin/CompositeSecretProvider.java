package com.hsin.sms.plugin;

import java.util.List;
import java.util.Optional;

/** Tries secret providers in order and returns the first hit. */
public final class CompositeSecretProvider implements SecretProvider {

    private final List<SecretProvider> providers;

    public CompositeSecretProvider(SecretProvider... providers) {
        this.providers = List.of(providers);
    }

    @Override
    public Optional<String> resolve(String key) {
        for (SecretProvider provider : providers) {
            Optional<String> value = provider.resolve(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
}
