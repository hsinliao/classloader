package com.hsin.sms.plugins.demo;

import com.hsin.sms.sdk.AbstractSmsProvider;
import com.hsin.sms.spi.SmsCapability;
import com.hsin.sms.spi.SmsProviderCapabilities;
import com.hsin.sms.spi.SmsProviderMetadata;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;
import com.hsin.sms.spi.SmsSendStatus;
import com.hsin.sms.testdep.LibraryInfo;

/** Normal plugin using vendor dependency v2 (same FQCN as v1). */
public final class PluginBProvider extends AbstractSmsProvider {

    private final LibraryInfo library = new LibraryInfo();

    public PluginBProvider() {
        super(SmsProviderMetadata.of("plugin-b", "plugin-b", "2.0.0", "example-vendor"),
                SmsProviderCapabilities.of(SmsCapability.SEND_SMS, SmsCapability.TEMPLATE_SMS));
    }

    @Override
    public SmsResponse send(SmsRequest request) {
        String region = context().config().getString("region", "unknown");
        return SmsResponse.builder()
                .status(SmsSendStatus.ACCEPTED)
                .requestId(request.requestId())
                .providerId("plugin-b")
                .messageId("b-" + request.idempotencyKey())
                .extension("libraryVersion", library.version())
                .extension("libraryFeature", library.feature())
                .extension("region", region)
                .build();
    }
}
