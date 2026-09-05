package com.hsin.sms.spi;

/** Delivery status of a send attempt as reported by the provider. */
public enum SmsSendStatus {
    /** Accepted by the carrier/vendor; final delivery not yet known. */
    ACCEPTED,
    /** Fully handed over / sent by the vendor. */
    SENT,
    /** Vendor reports a terminal delivery failure. */
    DELIVERED,
    /** Vendor reports terminal failure. */
    FAILED,
    /** Status could not be determined. */
    UNKNOWN
}
