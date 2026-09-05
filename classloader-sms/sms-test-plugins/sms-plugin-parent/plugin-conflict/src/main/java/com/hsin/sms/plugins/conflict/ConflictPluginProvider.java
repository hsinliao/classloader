package com.hsin.sms.plugins.conflict;

import com.hsin.sms.sdk.AbstractSmsProvider;
import com.hsin.sms.spi.SmsCapability;
import com.hsin.sms.spi.SmsProviderCapabilities;
import com.hsin.sms.spi.SmsProviderMetadata;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;
import com.hsin.sms.spi.SmsSendStatus;
import com.hsin.sms.testdep.LibraryInfo;

/**
 * Third plugin whose lib contains the same FQCN as plugin-a/plugin-b.
 * Its class loader instance is still independent.
 */
public final class ConflictPluginProvider extends AbstractSmsProvider {

    private final LibraryInfo library = new LibraryInfo();

    public ConflictPluginProvider() {
        super(SmsProviderMetadata.of("plugin-conflict", "plugin-conflict", "1.0.0", "example-vendor"),
                SmsProviderCapabilities.of(SmsCapability.SEND_SMS, SmsCapability.TEMPLATE_SMS,
                        SmsCapability.QUERY_STATUS));
    }

    @Override
    public SmsResponse send(SmsRequest request) {
        return SmsResponse.builder()
                .status(SmsSendStatus.SENT)
                .requestId(request.requestId())
                .providerId("plugin-conflict")
                .messageId("c-" + request.idempotencyKey())
                .extension("libraryVersion", library.version())
                .extension("libraryFeature", library.feature())
                .build();
    }
}
