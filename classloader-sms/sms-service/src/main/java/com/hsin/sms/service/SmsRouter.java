package com.hsin.sms.service;

import com.hsin.sms.spi.SmsRequest;

import java.util.Optional;

/**
 * Business routing policy: maps a tenant (or any routing key) to a provider id.
 * A router never touches plugin runtimes or class loaders directly.
 */
@FunctionalInterface
public interface SmsRouter {

    Optional<String> resolve(String tenantId, SmsRequest request);
}
