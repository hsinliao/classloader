package com.hsin.sms.plugins.slow;

import com.hsin.sms.sdk.AbstractSmsProvider;
import com.hsin.sms.spi.SmsCapability;
import com.hsin.sms.spi.SmsError;
import com.hsin.sms.spi.SmsErrorCategory;
import com.hsin.sms.spi.SmsProviderCapabilities;
import com.hsin.sms.spi.SmsProviderMetadata;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;
import com.hsin.sms.spi.SmsSendStatus;

/** Simulates a slow vendor; sleeps interruptibly for the configured duration. */
public final class SlowProvider extends AbstractSmsProvider {

    public SlowProvider() {
        super(SmsProviderMetadata.of("plugin-slow", "slow-provider", "1.0.0", "example-vendor"),
                SmsProviderCapabilities.of(SmsCapability.SEND_SMS));
    }

    @Override
    public SmsResponse send(SmsRequest request) {
        long millis = context().config().getLong("provider.slowMillis", 500);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SmsResponse.builder()
                    .status(SmsSendStatus.FAILED)
                    .requestId(request.requestId())
                    .providerId("plugin-slow")
                    .error(SmsError.of("INTERRUPTED", SmsErrorCategory.TIMEOUT,
                            "slow provider send was interrupted", true, e))
                    .build();
        }
        return SmsResponse.builder()
                .status(SmsSendStatus.ACCEPTED)
                .requestId(request.requestId())
                .providerId("plugin-slow")
                .messageId("slow-" + request.idempotencyKey())
                .build();
    }
}
