package com.hsin.sms.plugins.resources;

import com.hsin.sms.sdk.AbstractSmsProvider;
import com.hsin.sms.spi.SmsCapability;
import com.hsin.sms.spi.SmsProviderCapabilities;
import com.hsin.sms.spi.SmsProviderMetadata;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;
import com.hsin.sms.spi.SmsSendStatus;

import java.util.concurrent.atomic.AtomicInteger;

/** Registers configurable AutoCloseable resources during init. */
public final class ResourceProvider extends AbstractSmsProvider {

    static final AtomicInteger TOTAL_CLOSED = new AtomicInteger();

    public ResourceProvider() {
        super(SmsProviderMetadata.of("plugin-resources", "resources-provider", "1.0.0",
                        "example-vendor"),
                SmsProviderCapabilities.of(SmsCapability.SEND_SMS));
    }

    @Override
    public void init(com.hsin.sms.spi.SmsProviderContext context) {
        super.init(context);
        int count = context.config().getInt("resources.count", 5);
        for (int i = 0; i < count; i++) {
            final int index = i;
            context.registerResource("resource-" + index, () -> TOTAL_CLOSED.incrementAndGet());
        }
    }

    @Override
    public SmsResponse send(SmsRequest request) {
        return SmsResponse.builder()
                .status(SmsSendStatus.ACCEPTED)
                .requestId(request.requestId())
                .providerId("plugin-resources")
                .messageId("r-" + request.idempotencyKey())
                .extension("closedBeforeSend", String.valueOf(TOTAL_CLOSED.get()))
                .build();
    }
}
