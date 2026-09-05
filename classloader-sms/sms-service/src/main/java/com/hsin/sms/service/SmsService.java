package com.hsin.sms.service;

import com.hsin.sms.plugin.PluginBulkheadRejectedException;
import com.hsin.sms.plugin.PluginGateway;
import com.hsin.sms.plugin.PluginInvocationException;
import com.hsin.sms.plugin.PluginNotFoundException;
import com.hsin.sms.plugin.PluginStateException;
import com.hsin.sms.plugin.PluginTimeoutException;
import com.hsin.sms.spi.SmsError;
import com.hsin.sms.spi.SmsErrorCategory;
import com.hsin.sms.spi.SmsProviderException;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;
import com.hsin.sms.spi.SmsResult;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Stateless business facade. It knows routing and failure translation only;
 * all concurrency, bulkhead, timeout and lifecycle policy lives in the runtime.
 */
public final class SmsService implements AutoCloseable {

    private final PluginGateway gateway;
    private final SmsRouter router;
    private final ProviderRegistry providerRegistry;

    public SmsService(PluginGateway gateway, SmsRouter router, ProviderRegistry providerRegistry) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.router = Objects.requireNonNull(router, "router");
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
    }

    /** Routes by tenant then sends; unknown routes produce an {@code NO_ROUTE} result. */
    public SmsResult sendForTenant(String tenantId, SmsRequest request) {
        Optional<String> providerId = router.resolve(tenantId, request);
        if (providerId.isEmpty()) {
            return SmsResult.failure(null, SmsError.of("NO_ROUTE",
                    SmsErrorCategory.INVALID_REQUEST, "no provider route for tenant " + tenantId, false));
        }
        return send(providerId.get(), request);
    }

    /**
     * Sends directly to a provider id and translates runtime exceptions into a
     * stable {@link SmsResult}. This method never throws for plugin failures.
     */
    public SmsResult send(String providerId, SmsRequest request) {
        Objects.requireNonNull(providerId, "providerId");
        try {
            SmsResponse response = gateway.send(providerId, request);
            if (response == null) {
                return SmsResult.failure(providerId, SmsError.of("NULL_RESPONSE",
                        SmsErrorCategory.INTERNAL, "provider returned no response", false));
            }
            if (response.isSuccessful()) {
                return SmsResult.success(providerId, response);
            }
            SmsError error = response.error() == null
                    ? SmsError.of("FAILED", SmsErrorCategory.PROVIDER_REJECTED,
                    "provider reported failure with status " + response.status(), false)
                    : response.error();
            return SmsResult.failure(providerId, error);
        } catch (PluginTimeoutException e) {
            return SmsResult.failure(providerId, SmsError.of("TIMEOUT",
                    SmsErrorCategory.TIMEOUT, message(e), true));
        } catch (PluginBulkheadRejectedException e) {
            return SmsResult.failure(providerId, SmsError.of("BULKHEAD_REJECTED",
                    SmsErrorCategory.BULKHEAD_REJECTED, message(e), true));
        } catch (PluginStateException | PluginNotFoundException e) {
            return SmsResult.failure(providerId, SmsError.of("PROVIDER_UNAVAILABLE",
                    SmsErrorCategory.PROVIDER_UNAVAILABLE, message(e), true));
        } catch (PluginInvocationException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SmsProviderException spe) {
                SmsErrorCategory category = spe.category() == null
                        ? SmsErrorCategory.UNKNOWN : spe.category();
                return SmsResult.failure(providerId, SmsError.of(
                        "PROVIDER_" + category.name(), category, message(e), spe.retryable(), cause));
            }
            return SmsResult.failure(providerId, SmsError.of("INVOCATION",
                    SmsErrorCategory.INTERNAL, message(e), false, cause));
        } catch (RuntimeException e) {
            return SmsResult.failure(providerId, SmsError.of("INTERNAL",
                    SmsErrorCategory.INTERNAL, message(e), false, e));
        }
    }

    /** Async variant on the common pool; the plugin runtime still enforces bulkhead. */
    public CompletableFuture<SmsResult> sendAsync(String providerId, SmsRequest request) {
        return CompletableFuture.supplyAsync(() -> send(providerId, request));
    }

    /** Async variant on an explicit caller-owned executor. */
    public CompletableFuture<SmsResult> sendAsync(String providerId, SmsRequest request, Executor executor) {
        return CompletableFuture.supplyAsync(() -> send(providerId, request), executor);
    }

    /** Live running-provider directory. */
    public ProviderRegistry providerRegistry() {
        return providerRegistry;
    }

    @Override
    public void close() {
        // Stateless; the caller owns the gateway/manager lifecycle.
    }

    private static String message(Throwable t) {
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m;
    }
}
