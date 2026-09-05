package com.hsin.sms.spi;

/**
 * The stable service-provider contract between host and SMS vendors.
 *
 * <p>Implementations are discovered through the standard Java ServiceLoader mechanism
 * using the plugin class loader. A provider must have a public no-argument constructor.</p>
 *
 * <p>Threading contract: implementations must be thread-safe because the runtime can
 * invoke {@link #send(SmsRequest)} concurrently, bounded by the plugin bulkhead.</p>
 */
public interface SmsProvider {

    SmsProviderMetadata metadata();

    SmsProviderCapabilities capabilities();

    /**
     * Called once per runtime start, before the plugin is marked RUNNING.
     * Resource registration and configuration reads belong here.
     */
    default void init(SmsProviderContext context) {
        // optional
    }

    /**
     * Called when the plugin leaves RUNNING. Must release in-memory state; registered
     * resources are closed by the runtime independently of this hook.
     */
    default void destroy() {
        // optional
    }

    /**
     * Sends a message. Implementations may throw {@link SmsProviderException} for
     * protocol/vendor errors; the runtime translates them for the service layer.
     *
     * @return the provider response; never {@code null}
     */
    SmsResponse send(SmsRequest request);
}
