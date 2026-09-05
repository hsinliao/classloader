package com.hsin.sms.spi;

import java.util.Objects;

/**
 * Immutable, transport-safe error description.
 *
 * <p>{@code cause} is deliberately optional and process-local; it is used for diagnostics
 * and is never serialized as part of a response.</p>
 */
public final class SmsError {

    private final String code;
    private final SmsErrorCategory category;
    private final String message;
    private final boolean retryable;
    private final transient Throwable cause;

    private SmsError(String code, SmsErrorCategory category, String message,
                     boolean retryable, Throwable cause) {
        this.code = Objects.requireNonNull(code, "code");
        this.category = Objects.requireNonNull(category, "category");
        this.message = Objects.requireNonNull(message, "message");
        this.retryable = retryable;
        this.cause = cause;
    }

    public static SmsError of(String code, SmsErrorCategory category, String message, boolean retryable) {
        return new SmsError(code, category, message, retryable, null);
    }

    /** Factory overload that also keeps a process-local cause for diagnostics. */
    public static SmsError of(String code, SmsErrorCategory category, String message,
                              boolean retryable, Throwable cause) {
        return new SmsError(code, category, message, retryable, cause);
    }

    public String code() {
        return code;
    }

    public SmsErrorCategory category() {
        return category;
    }

    public String message() {
        return message;
    }

    public boolean retryable() {
        return retryable;
    }

    public Throwable cause() {
        return cause;
    }

    @Override
    public String toString() {
        return "SmsError{code='" + code + "', category=" + category + ", retryable=" + retryable + "}";
    }
}
