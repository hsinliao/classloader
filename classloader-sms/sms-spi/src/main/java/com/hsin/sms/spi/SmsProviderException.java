package com.hsin.sms.spi;

/**
 * Unchecked exception thrown by a provider (or by the runtime around a provider).
 */
public class SmsProviderException extends RuntimeException {

    private final SmsErrorCategory category;
    private final boolean retryable;

    public SmsProviderException(String message) {
        this(message, SmsErrorCategory.UNKNOWN, false, null);
    }

    public SmsProviderException(String message, SmsErrorCategory category, boolean retryable) {
        this(message, category, retryable, null);
    }

    public SmsProviderException(String message, SmsErrorCategory category, boolean retryable, Throwable cause) {
        super(message, cause);
        this.category = category == null ? SmsErrorCategory.UNKNOWN : category;
        this.retryable = retryable;
    }

    public SmsProviderException(Throwable cause) {
        this(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage(),
                SmsErrorCategory.INTERNAL, false, cause);
    }

    public SmsErrorCategory category() {
        return category;
    }

    public boolean retryable() {
        return retryable;
    }
}
