package com.hsin.sms.plugins.threads;

import com.hsin.sms.sdk.AbstractSmsProvider;
import com.hsin.sms.spi.SmsCapability;
import com.hsin.sms.spi.SmsProviderCapabilities;
import com.hsin.sms.spi.SmsProviderMetadata;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;
import com.hsin.sms.spi.SmsSendStatus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Creates an executor, a scheduled executor and a raw thread during init. */
public final class ThreadsProvider extends AbstractSmsProvider {

    private final AtomicLong ticks = new AtomicLong();
    private volatile ExecutorService pool;
    private volatile ScheduledExecutorService scheduler;
    private volatile Thread worker;

    public ThreadsProvider() {
        super(SmsProviderMetadata.of("plugin-threads", "threads-provider", "1.0.0", "example-vendor"),
                SmsProviderCapabilities.of(SmsCapability.SEND_SMS));
    }

    @Override
    public void init(com.hsin.sms.spi.SmsProviderContext context) {
        super.init(context);
        this.pool = context.newExecutor("demo-pool", 2);
        this.scheduler = context.newScheduledExecutor("demo-scheduler", 1);
        scheduler.scheduleAtFixedRate(() -> ticks.incrementAndGet(), 0, 10,
                TimeUnit.MILLISECONDS);
        Thread raw = context.newThreadFactory("manual-loop").newThread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        this.worker = raw;
        raw.start();
        context.registerResource("manual-thread", () -> raw.interrupt());
    }

    @Override
    public void destroy() {
        ExecutorService p = pool;
        if (p != null) {
            p.shutdownNow();
        }
        ScheduledExecutorService s = scheduler;
        if (s != null) {
            s.shutdownNow();
        }
        Thread w = worker;
        if (w != null) {
            w.interrupt();
        }
    }

    @Override
    public SmsResponse send(SmsRequest request) {
        return SmsResponse.builder()
                .status(SmsSendStatus.ACCEPTED)
                .requestId(request.requestId())
                .providerId("plugin-threads")
                .messageId("t-" + request.idempotencyKey())
                .extension("ticks", String.valueOf(ticks.get()))
                .build();
    }
}
