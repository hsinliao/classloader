package com.hsin.sms.spi;

/** Stable host-side error categories. Vendor details are preserved in {@code SmsError.code}. */
public enum SmsErrorCategory {
    INVALID_REQUEST,
    CONFIGURATION,
    AUTHENTICATION,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    PROVIDER_UNAVAILABLE,
    PROVIDER_REJECTED,
    TIMEOUT,
    BULKHEAD_REJECTED,
    INTERNAL,
    UNKNOWN
}
