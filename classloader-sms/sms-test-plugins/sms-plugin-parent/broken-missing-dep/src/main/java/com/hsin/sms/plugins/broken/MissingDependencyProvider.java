package com.hsin.sms.plugins.broken;

import com.hsin.sms.sdk.AbstractSmsProvider;
import com.hsin.sms.spi.SmsCapability;
import com.hsin.sms.spi.SmsProviderCapabilities;
import com.hsin.sms.spi.SmsProviderMetadata;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;
import com.hsin.sms.spi.SmsSendStatus;
import com.hsin.sms.testdep.LibraryInfo;

/**
 * The dependency is available at compile time (scope provided) but is never
 * copied into {@code lib/}; the class initializer forces resolution at start.
 */
public final class MissingDependencyProvider extends AbstractSmsProvider {

    private static final String VERSION = new LibraryInfo().version();

    public MissingDependencyProvider() {
        super(SmsProviderMetadata.of("broken-missing-dep", "broken-missing-dep",
                        "1.0.0", "example-vendor"),
                SmsProviderCapabilities.of(SmsCapability.SEND_SMS));
    }

    @Override
    public SmsResponse send(SmsRequest request) {
        return SmsResponse.builder().status(SmsSendStatus.ACCEPTED)
                .requestId(request.requestId()).providerId("broken-missing-dep")
                .extension("libraryVersion", VERSION).build();
    }
}
