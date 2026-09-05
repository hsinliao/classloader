/**
 * Host-side plugin runtime.
 *
 * <p>This package is never part of the plugin class path. It owns plugin discovery,
 * descriptor validation, class loader isolation, lifecycle state, resource/thread
 * governance, bulkhead and metrics. Business routing lives in the separate
 * {@code com.hsin.sms.service} package.</p>
 */
package com.hsin.sms.plugin;
