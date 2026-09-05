package com.hsin.sms.spi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable response returned by a provider after a send attempt. */
public final class SmsResponse {

    private final SmsSendStatus status;
    private final String requestId;
    private final String providerId;
    private final String messageId;
    private final SmsError error;
    private final long elapsedNanos;
    private final long sentAtEpochMillis;
    private final Map<String, String> extensions;

    private SmsResponse(Builder b) {
        this.status = Objects.requireNonNull(b.status, "status");
        this.requestId = Objects.requireNonNull(b.requestId, "requestId");
        this.providerId = Objects.requireNonNull(b.providerId, "providerId");
        this.messageId = b.messageId;
        this.error = b.error;
        this.elapsedNanos = b.elapsedNanos;
        this.sentAtEpochMillis = b.sentAtEpochMillis;
        this.extensions = b.extensions == null || b.extensions.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(b.extensions));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SmsResponse accepted(String requestId, String providerId, String messageId) {
        return builder().status(SmsSendStatus.ACCEPTED).requestId(requestId)
                .providerId(providerId).messageId(messageId).build();
    }

    public static SmsResponse failure(SmsSendStatus status, String requestId, String providerId,
                                      SmsError error) {
        return builder().status(status).requestId(requestId).providerId(providerId).error(error).build();
    }

    public SmsSendStatus status() {
        return status;
    }

    public String requestId() {
        return requestId;
    }

    public String providerId() {
        return providerId;
    }

    public String messageId() {
        return messageId;
    }

    public SmsError error() {
        return error;
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    public long sentAtEpochMillis() {
        return sentAtEpochMillis;
    }

    public Map<String, String> extensions() {
        return extensions;
    }

    public boolean isSuccessful() {
        return error == null && status != SmsSendStatus.FAILED;
    }

    public static final class Builder {
        private SmsSendStatus status;
        private String requestId;
        private String providerId;
        private String messageId;
        private SmsError error;
        private long elapsedNanos;
        private long sentAtEpochMillis = System.currentTimeMillis();
        private Map<String, String> extensions;

        private Builder() {
        }

        public Builder status(SmsSendStatus status) {
            this.status = status;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder error(SmsError error) {
            this.error = error;
            return this;
        }

        public Builder elapsedNanos(long elapsedNanos) {
            this.elapsedNanos = elapsedNanos;
            return this;
        }

        public Builder sentAtEpochMillis(long sentAtEpochMillis) {
            this.sentAtEpochMillis = sentAtEpochMillis;
            return this;
        }

        public Builder extension(String key, String value) {
            if (extensions == null) {
                extensions = new HashMap<>();
            }
            extensions.put(key, value);
            return this;
        }

        public SmsResponse build() {
            return new SmsResponse(this);
        }
    }
}
