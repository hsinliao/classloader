package com.hsin.sms.plugins.broken;

import com.hsin.sms.sdk.AbstractSmsProvider;
import com.hsin.sms.spi.SmsCapability;
import com.hsin.sms.spi.SmsProviderCapabilities;
import com.hsin.sms.spi.SmsProviderMetadata;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;
import com.hsin.sms.spi.SmsSendStatus;

/** Provider compiled against a future, incompatible SPI version. */
public final class SpiVersionTooNewProvider extends AbstractSmsProvider {

    public SpiVersionTooNewProvider() {
        super(SmsProviderMetadata.of("broken-spi-version", "broken-spi-version",
                        "1.0.0", "example-vendor"),
                SmsProviderCapabilities.of(SmsCapability.SEND_SMS));
    }

    @Override
    public SmsResponse send(SmsRequest request) {
        return SmsResponse.builder().status(SmsSendStatus.ACCEPTED)
                .requestId(request.requestId()).providerId("broken-spi-version").build();
    }
}
