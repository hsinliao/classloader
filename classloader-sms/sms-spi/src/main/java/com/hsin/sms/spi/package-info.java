/**
 * Stable SMS service-provider contracts.
 *
 * <p>This package is loaded only by the host. It has zero dependencies on plugin
 * runtime internals, vendor SDKs, HTTP clients or JSON frameworks. A plugin's
 * {@code META-INF/services/com.hsin.sms.spi.SmsProvider} entry is discovered with
 * the plugin class loader, while the SPI types themselves always come from the
 * parent (host) class loader.</p>
 */
package com.hsin.sms.spi;
