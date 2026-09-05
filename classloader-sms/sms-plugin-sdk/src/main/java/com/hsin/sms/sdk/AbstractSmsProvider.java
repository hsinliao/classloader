package com.hsin.sms.sdk;

import com.hsin.sms.spi.SmsProvider;
import com.hsin.sms.spi.SmsProviderCapabilities;
import com.hsin.sms.spi.SmsProviderContext;
import com.hsin.sms.spi.SmsProviderMetadata;

import java.util.Objects;

/**
 * Convenience base class for SMS plugins.
 *
 * <p>The base class stores the provider metadata/capabilities and the host context
 * handed to {@link #init(SmsProviderContext)}. It never depends on runtime classes.</p>
 */
public abstract class AbstractSmsProvider implements SmsProvider {

    private final SmsProviderMetadata metadata;
    private final SmsProviderCapabilities capabilities;
    private volatile SmsProviderContext context;

    protected AbstractSmsProvider(SmsProviderMetadata metadata, SmsProviderCapabilities capabilities) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    }

    /** Returns the fixed metadata supplied by the subclass constructor. */
    @Override
    public final SmsProviderMetadata metadata() {
        return metadata;
    }

    /** Returns the fixed capability set supplied by the subclass constructor. */
    @Override
    public final SmsProviderCapabilities capabilities() {
        return capabilities;
    }

    /** Stores the host context; subclasses may override to perform startup setup. */
    @Override
    public void init(SmsProviderContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /** Returns the context stored during {@link #init}. */
    protected final SmsProviderContext context() {
        SmsProviderContext c = context;
        if (c == null) {
            throw new IllegalStateException("provider not initialized");
        }
        return c;
    }
}
