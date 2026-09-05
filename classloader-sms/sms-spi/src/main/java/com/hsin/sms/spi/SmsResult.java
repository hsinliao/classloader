package com.hsin.sms.spi;

import java.util.Objects;

/**
 * Service-level outcome of a send attempt. A failed attempt carries either a
 * response with an error, or a transport-level {@link SmsError}.
 */
public final class SmsResult {

    private final boolean success;
    private final String providerId;
    private final SmsResponse response;
    private final SmsError error;

    private SmsResult(boolean success, String providerId, SmsResponse response, SmsError error) {
        this.success = success;
        this.providerId = providerId;
        this.response = response;
        this.error = error;
    }

    /** Successful outcome carrying the provider response. */
    public static SmsResult success(String providerId, SmsResponse response) {
        Objects.requireNonNull(response, "response");
        return new SmsResult(true, providerId, response, null);
    }

    /** Failed outcome carrying a stable host-side error. */
    public static SmsResult failure(String providerId, SmsError error) {
        Objects.requireNonNull(error, "error");
        return new SmsResult(false, providerId, null, error);
    }

    public boolean success() {
        return success;
    }

    /** Provider id that was attempted, when known. */
    public String providerId() {
        return providerId;
    }

    /** Provider response, present only on success. */
    public SmsResponse response() {
        return response;
    }

    /** Error description, present only on failure. */
    public SmsError error() {
        return error;
    }

    @Override
    public String toString() {
        return "SmsResult{success=" + success + ", providerId='" + providerId
                + "', error=" + error + "}";
    }
}
