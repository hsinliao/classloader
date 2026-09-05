package com.hsin.sms.spi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable SMS send request with an explicit null policy.
 *
 * <ul>
 *   <li>{@code requestId} and {@code phoneNumbers} are required and validated.</li>
 *   <li>{@code content} or {@code templateId} must be present.</li>
 *   <li>{@code businessId}/{@code idempotencyKey} are optional; the idempotency key
 *       defaults to the request id.</li>
 *   <li>Only stable Java types are used: no vendor SDK types can cross this boundary.</li>
 * </ul>
 */
public final class SmsRequest {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final String requestId;
    private final String businessId;
    private final String idempotencyKey;
    private final List<String> phoneNumbers;
    private final String content;
    private final String templateId;
    private final Map<String, String> templateParams;
    private final Duration timeout;
    private final Map<String, String> extensions;

    private SmsRequest(Builder b) {
        this.requestId = requireNonBlank(b.requestId, "requestId");
        if (b.phoneNumbers == null || b.phoneNumbers.isEmpty()) {
            throw new IllegalArgumentException("phoneNumbers must not be empty");
        }
        List<String> phones = new ArrayList<>(b.phoneNumbers.size());
        for (String p : b.phoneNumbers) {
            String v = requireNonBlank(p, "phoneNumber");
            phones.add(v);
        }
        this.phoneNumbers = Collections.unmodifiableList(phones);
        this.content = trimToNull(b.content);
        this.templateId = trimToNull(b.templateId);
        if (content == null && templateId == null) {
            throw new IllegalArgumentException("content or templateId must be present");
        }
        this.businessId = trimToNull(b.businessId);
        String idem = trimToNull(b.idempotencyKey);
        this.idempotencyKey = idem == null ? requestId : idem;
        this.templateParams = b.templateParams == null || b.templateParams.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(b.templateParams));
        this.timeout = b.timeout == null ? DEFAULT_TIMEOUT : b.timeout;
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.extensions = b.extensions == null || b.extensions.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(b.extensions));
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SmsRequest of(String requestId, List<String> phoneNumbers, String content) {
        return builder().requestId(requestId).phoneNumbers(phoneNumbers).content(content).build();
    }

    public String requestId() {
        return requestId;
    }

    public String businessId() {
        return businessId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public List<String> phoneNumbers() {
        return phoneNumbers;
    }

    public String content() {
        return content;
    }

    public String templateId() {
        return templateId;
    }

    public Map<String, String> templateParams() {
        return templateParams;
    }

    public Duration timeout() {
        return timeout;
    }

    public Map<String, String> extensions() {
        return extensions;
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.requestId = requestId;
        b.businessId = businessId;
        b.idempotencyKey = idempotencyKey;
        b.phoneNumbers = new ArrayList<>(phoneNumbers);
        b.content = content;
        b.templateId = templateId;
        b.templateParams = new HashMap<>(templateParams);
        b.timeout = timeout;
        b.extensions = new HashMap<>(extensions);
        return b;
    }

    @Override
    public String toString() {
        return "SmsRequest{requestId='" + requestId + "', businessId='" + businessId + "', "
                + "templateId='" + templateId + "', phones=" + phoneNumbers.size() + "}";
    }

    public static final class Builder {

        private String requestId = UUID.randomUUID().toString();
        private String businessId;
        private String idempotencyKey;
        private List<String> phoneNumbers;
        private String content;
        private String templateId;
        private Map<String, String> templateParams;
        private Duration timeout;
        private Map<String, String> extensions;

        private Builder() {
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder businessId(String businessId) {
            this.businessId = businessId;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder phoneNumbers(List<String> phoneNumbers) {
            this.phoneNumbers = phoneNumbers;
            return this;
        }

        public Builder phoneNumbers(String... phoneNumbers) {
            this.phoneNumbers = List.of(phoneNumbers);
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder templateParams(Map<String, String> templateParams) {
            this.templateParams = templateParams;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder extensions(Map<String, String> extensions) {
            this.extensions = extensions;
            return this;
        }

        public SmsRequest build() {
            return new SmsRequest(this);
        }
    }
}
