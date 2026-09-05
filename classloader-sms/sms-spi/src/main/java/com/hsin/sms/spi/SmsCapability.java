package com.hsin.sms.spi;

/**
 * A declarative capability that a provider may support.
 *
 * <p>The host must never assume that every provider supports every capability.
 * A provider declares its own set through {@link SmsProvider#capabilities()}.</p>
 */
public enum SmsCapability {

    /** Plain text / content message sending. */
    SEND_SMS,

    /** Template based message sending. */
    TEMPLATE_SMS,

    /** Sending one request to many destinations. */
    BATCH_SEND,

    /** Querying the delivery status of a previously submitted message. */
    QUERY_STATUS,

    /** Sending messages to non-domestic destinations. */
    INTERNATIONAL_SMS
}
