package com.hsin.sms.plugin;

import com.hsin.sms.spi.PluginConfig;
import com.hsin.sms.spi.SmsCapability;
import com.hsin.sms.spi.SmsProvider;
import com.hsin.sms.spi.SmsProviderException;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns one plugin instance end-to-end: class loader, provider, lifecycle state,
 * bulkhead, in-flight tracking, worker executor, resource registry, metrics.
 *
 * <p>Threading model:
 * <ul>
 *   <li>{@link #lifecycleLock} serializes lifecycle transitions.</li>
 *   <li>{@link #lifecycleGate} (read/write) atomically stops new in-flight work when
 *       a drain begins.</li>
 *   <li>Provider calls run on a bounded per-plugin worker executor so callers can
 *       observe a real timeout. Stuck code cannot be force-killed by the JVM; the
 *       drained worker is interrupted and the runtime moves on after the timeout.</li>
 * </ul>
 */
public final class PluginRuntime {

    private static final Logger LOG = Logger.getLogger("com.hsin.sms.plugin.runtime");

    private final PluginDescriptor descriptor;
    private final PluginConfigSource configSource;
    private final SecretProvider secretProvider;
    private final PluginIntegrityPolicy integrityPolicy;
    private final PluginRuntimeSettings settings;
    private final SpiCompatibilityChecker compatibilityChecker;
    private final PluginMetrics metrics = new PluginMetrics();
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final ReentrantReadWriteLock lifecycleGate = new ReentrantReadWriteLock();
    private final PluginResourceRegistry resourceRegistry = new PluginResourceRegistry();
    private final Object inFlightMonitor = new Object();
    private final Semaphore bulkhead;

    private volatile PluginClassLoader classLoader;
    private volatile PluginConfigView config;
    private volatile SmsProvider provider;
    private volatile String providerClassName;
    private volatile ExecutorService workers;
    private volatile PluginThreadFactory workerThreadFactory;
    private volatile PluginState state = PluginState.DISCOVERED;
    private volatile PluginStateChange lastChange;
    private int inFlight;
    private boolean classLoaderClosed;

    PluginRuntime(PluginDescriptor descriptor, PluginConfigSource configSource,
                  SecretProvider secretProvider, PluginIntegrityPolicy integrityPolicy,
                  PluginRuntimeSettings settings) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.configSource = Objects.requireNonNull(configSource, "configSource");
        this.secretProvider = Objects.requireNonNull(secretProvider, "secretProvider");
        this.integrityPolicy = Objects.requireNonNull(integrityPolicy, "integrityPolicy");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.compatibilityChecker = new SpiCompatibilityChecker(settings.runtimeSpiVersion());
        int concurrency = effectiveConcurrency();
        this.bulkhead = new Semaphore(concurrency, true);
    }

    private int effectiveConcurrency() {
        return settings.maxConcurrency();
    }

    // ------------------------------------------------------------------ load

    /**
     * Load-only transition (called by the plugin manager): checks compatibility,
     * opens the class loader, parses configuration and verifies that the declared
     * provider is discoverable via ServiceLoader without instantiating it yet.
     */
    void load() {
        lifecycleLock.lock();
        try {
            transition(PluginState.DISCOVERED, PluginState.LOADING);
            long started = System.nanoTime();
            try {
                checkCompatibility();
                integrityPolicy.verify(descriptor);
                URL[] urls = toUrls(descriptor.allJars());
                PluginClassLoader created =
                        new PluginClassLoader(descriptor.id(), urls, PluginRuntime.class.getClassLoader());
                this.classLoader = created;
                this.config = new PluginConfigView(configSource.load(descriptor.pluginDir()));
                String discovered = discoverProviderClassName(created);
                this.providerClassName = discovered;
                transition(PluginState.LOADING, PluginState.LOADED);
                metrics.recordLoaded(System.nanoTime() - started);
            } catch (Throwable t) {
                metrics.recordLoadFailure(t);
                fail(PluginState.LOADING, t);
                if (t instanceof Error error && !(t instanceof LinkageError)) {
                    throw error;
                }
                if (t instanceof PluginCompatibilityException || t instanceof PluginLoadException) {
                    throw t;
                }
                throw new PluginLoadException(descriptor.id(), descriptor.version(),
                        "load failed: " + rootMessage(t), t);
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void checkCompatibility() {
        SpiCompatibilityChecker.CompatibilityResult result =
                compatibilityChecker.check(descriptor.spiVersion(), descriptor.requiresSpiRange());
        if (!result.compatible()) {
            throw new PluginCompatibilityException(descriptor.id(), descriptor.version(),
                    "plugin " + descriptor.id() + " v" + descriptor.version() + " is incompatible: "
                            + result.reason());
        }
        int requiredJava = Integer.parseInt(descriptor.requiresJava());
        int runningJava = Runtime.version().feature();
        if (runningJava < requiredJava) {
            throw new PluginCompatibilityException(descriptor.id(), descriptor.version(),
                    "plugin requires Java " + requiredJava + " but runtime is " + runningJava);
        }
    }

    private String discoverProviderClassName(PluginClassLoader loader) {
        List<String> types = new ArrayList<>();
        try {
            ServiceLoader<SmsProvider> serviceLoader =
                    ServiceLoader.load(SmsProvider.class, loader);
            for (ServiceLoader.Provider<SmsProvider> p : serviceLoader.stream().toList()) {
                String typeName = p.type().getName();
                types.add(typeName);
                if (typeName.equals(descriptor.mainClass())) {
                    return typeName;
                }
            }
        } catch (ServiceConfigurationError e) {
            throw new PluginLoadException(descriptor.id(), descriptor.version(),
                    "service configuration error: " + e.getMessage(), e);
        }
        if (types.isEmpty()) {
            throw new PluginLoadException(descriptor.id(), descriptor.version(),
                    "no META-INF/services/" + SmsProvider.class.getName()
                            + " provider was discovered in " + descriptor.pluginDir(), null);
        }
        throw new PluginLoadException(descriptor.id(), descriptor.version(),
                "provider main class " + descriptor.mainClass() + " was not listed in service file; "
                        + "found " + types, null);
    }

    private static URL[] toUrls(List<Path> jars) {
        try {
            List<URL> urls = new ArrayList<>();
            for (Path jar : jars) {
                urls.add(jar.toUri().toURL());
            }
            return urls.toArray(URL[]::new);
        } catch (Exception e) {
            throw new PluginLoadException(null, null, "cannot convert jars to urls", e);
        }
    }

    // ----------------------------------------------------------------- start

    /**
     * Starts the plugin: instantiates the provider, calls {@code init} under the
     * plugin class loader as TCCL and starts the bounded worker pool.
     * Idempotent when already RUNNING; a stopped-but-loaded plugin can restart.
     */
    public void start() {
        lifecycleLock.lock();
        try {
            PluginState current = state;
            if (current == PluginState.RUNNING) {
                return;
            }
            if (current != PluginState.LOADED && current != PluginState.STOPPED) {
                throw new PluginStateException(descriptor.id(), descriptor.version(),
                        "cannot start plugin in state " + current);
            }
            transition(current, PluginState.STARTING);
            long started = System.nanoTime();
            try {
                // Every provider method (constructor, metadata, capabilities, init)
                // runs with the plugin class loader as TCCL.
                withPluginClassLoader(() -> {
                    SmsProvider created = instantiateProvider();
                    SmsProviderContextImpl context =
                            new SmsProviderContextImpl(created.metadata().providerId());
                    this.provider = created;
                    created.init(context);
                    created.metadata();
                    created.capabilities();
                });
                startWorkers();
                transition(PluginState.STARTING, PluginState.RUNNING);
                metrics.recordStarted(System.nanoTime() - started);
            } catch (Throwable t) {
                metrics.recordStartFailure(t);
                cleanupAfterStartFailure();
                transition(PluginState.STARTING, PluginState.FAILED);
                if (t instanceof Error error && !(t instanceof LinkageError)) {
                    throw error;
                }
                throw new PluginStartException(descriptor.id(), descriptor.version(),
                        "start failed: " + rootMessage(t), t);
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private SmsProvider instantiateProvider() {
        PluginClassLoader loader = requireClassLoader();
        try {
            ServiceLoader<SmsProvider> serviceLoader = ServiceLoader.load(SmsProvider.class, loader);
            for (SmsProvider candidate : serviceLoader) {
                if (candidate.getClass().getName().equals(descriptor.mainClass())) {
                    if (candidate.getClass().getClassLoader() != loader) {
                        throw new PluginStartException(descriptor.id(), descriptor.version(),
                                "provider class " + candidate.getClass().getName()
                                        + " was not loaded by the plugin class loader", null);
                    }
                    return candidate;
                }
            }
        } catch (ServiceConfigurationError | PluginStartException e) {
            throw new PluginStartException(descriptor.id(), descriptor.version(),
                    "cannot instantiate provider: " + rootMessage(e), e);
        }
        throw new PluginStartException(descriptor.id(), descriptor.version(),
                "no provider instance for main class " + descriptor.mainClass(), null);
    }

    private void startWorkers() {
        if (workers != null) {
            return;
        }
        int concurrency = effectiveConcurrency();
        PluginThreadFactory factory = new PluginThreadFactory(
                descriptor.id(), "sms-plugin-" + descriptor.id() + "-worker",
                classLoader, metrics);
        this.workerThreadFactory = factory;
        // A direct-handoff pool: a caller can only hold a bulkhead permit while one
        // worker is actually executing its request, so no unbounded queue can form.
        this.workers = new ThreadPoolExecutor(concurrency, concurrency, 0L,
                TimeUnit.MILLISECONDS, new SynchronousQueue<>(), factory);
    }

    private void cleanupAfterStartFailure() {
        try {
            withPluginClassLoader(() -> {
                SmsProvider current = provider;
                provider = null;
                if (current != null) {
                    try {
                        current.destroy();
                    } catch (Throwable destroyFailure) {
                        LOG.log(Level.WARNING,
                                "destroy failed during start rollback for " + descriptor.id(),
                                destroyFailure);
                    }
                }
            });
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "start rollback failed for " + descriptor.id(), t);
        }
        shutdownWorkers(settings.shutdownTimeout());
        List<Throwable> failures = closeResourcesWithPluginClassLoader();
        failures.forEach(t -> metrics.recordStartFailure(t));
    }

    // ----------------------------------------------------------------- send

    /**
     * Runs one provider call under the plugin's bulkhead and timeout.
     *
     * <p>State/gate semantics: RUNNING is required; a drain cannot begin after the
     * call is counted. The provider code runs on a bounded worker, so a caller can
     * observe a real timeout. Java cannot force-kill truly stuck code, so the worker
     * is interrupted and ownership of the bulkhead slot is released only when the
     * underlying invocation actually returns.</p>
     */
    public SmsResponse send(SmsRequest request) {
        Objects.requireNonNull(request, "request");
        PluginState current = state;
        SmsProvider target = provider;
        if (current != PluginState.RUNNING || target == null) {
            throw new PluginStateException(descriptor.id(), descriptor.version(),
                    "provider is not running (state=" + current + ")");
        }
        if (!bulkhead.tryAcquire()) {
            metrics.recordBulkheadRejected();
            throw new PluginBulkheadRejectedException(descriptor.id(), descriptor.version(),
                    "plugin '" + descriptor.id() + "' bulkhead is full (max="
                            + settings.maxConcurrency() + ")");
        }
        AtomicBoolean cleaned = new AtomicBoolean();
        Runnable releaseOnce = () -> {
            if (cleaned.compareAndSet(false, true)) {
                leaveInFlight();
                bulkhead.release();
            }
        };
        AtomicBoolean started = new AtomicBoolean();

        FutureTask<SmsResponse> task;
        try {
            lifecycleGate.readLock().lock();
            try {
                if (state != PluginState.RUNNING) {
                    throw new PluginStateException(descriptor.id(), descriptor.version(),
                            "plugin left RUNNING before dispatch (state=" + state + ")");
                }
                enterInFlight();
            } finally {
                lifecycleGate.readLock().unlock();
            }

            metrics.recordRequest();
            ExecutorService executor = workers;
            if (executor == null) {
                throw new PluginStateException(descriptor.id(), descriptor.version(),
                        "worker executor is not available");
            }

            // The worker (not the waiting caller) owns the bulkhead permit and the
            // in-flight slot. A caller that times out abandons the slot; cleanup
            // happens only when the underlying provider invocation really finishes.
            // This keeps maxConcurrency a true bound on concurrent provider work.
            task = new FutureTask<>(() -> {
                started.set(true);
                try {
                    return invokeProvider(target, request);
                } finally {
                    releaseOnce.run();
                }
            }) {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    boolean canceled = super.cancel(mayInterruptIfRunning);
                    // If cancellation wins the race before the task begins, the task
                    // body (and therefore its finally) never runs; clean up here.
                    if (canceled && !started.get()) {
                        releaseOnce.run();
                    }
                    return canceled;
                }
            };
            executor.execute(task);
        } catch (RuntimeException dispatchFailure) {
            releaseOnce.run();
            if (dispatchFailure instanceof PluginStateException pse) {
                throw pse;
            }
            throw new PluginStateException(descriptor.id(), descriptor.version(),
                    "cannot dispatch provider call: " + rootMessage(dispatchFailure));
        } catch (Error dispatchFailure) {
            releaseOnce.run();
            throw dispatchFailure;
        }
        return awaitResponse(task, request);
    }

    private SmsResponse awaitResponse(Future<SmsResponse> future, SmsRequest request) {
        long timeoutMillis = request.timeout().toMillis();
        try {
            SmsResponse response = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            if (response == null) {
                throw new PluginInvocationException(descriptor.id(), descriptor.version(), false,
                        "provider returned null response", null);
            }
            return response;
        } catch (TimeoutException e) {
            metrics.recordTimeout();
            future.cancel(true);
            throw new PluginTimeoutException(descriptor.id(), descriptor.version(),
                    "send timed out after " + timeoutMillis + " ms");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new PluginInvocationException(descriptor.id(), descriptor.version(), false,
                    "send was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SmsProviderException spe) {
                if (spe.category() == com.hsin.sms.spi.SmsErrorCategory.TIMEOUT) {
                    metrics.recordTimeout();
                }
                throw new PluginInvocationException(descriptor.id(), descriptor.version(),
                        spe.retryable(), "provider failed: " + rootMessage(spe), spe);
            }
            if (cause instanceof LinkageError) {
                metrics.recordFailure(cause);
                throw new PluginInvocationException(descriptor.id(), descriptor.version(), false,
                        "plugin linkage failure: " + rootMessage(cause), cause);
            }
            if (cause instanceof Error error) {
                // Do not swallow JVM-level errors such as OutOfMemoryError.
                throw error;
            }
            throw new PluginInvocationException(descriptor.id(), descriptor.version(), false,
                    "provider invocation failed: " + rootMessage(cause), cause);
        }
    }

    private SmsResponse invokeProvider(SmsProvider target, SmsRequest request) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            checkCapability(target, request);
            SmsResponse response = target.send(request);
            if (response == null) {
                throw new SmsProviderException("provider returned null",
                        com.hsin.sms.spi.SmsErrorCategory.INTERNAL, false);
            }
            metrics.recordSuccess();
            return response;
        } catch (SmsProviderException e) {
            metrics.recordFailure(e);
            throw e;
        } catch (LinkageError | ServiceConfigurationError e) {
            metrics.recordFailure(e);
            throw new SmsProviderException(
                    "plugin linkage/service error: " + rootMessage(e), 
                    com.hsin.sms.spi.SmsErrorCategory.PROVIDER_UNAVAILABLE, false, e);
        } catch (RuntimeException e) {
            metrics.recordFailure(e);
            throw new SmsProviderException("provider runtime failure: " + rootMessage(e),
                    com.hsin.sms.spi.SmsErrorCategory.INTERNAL, false, e);
        } catch (Error e) {
            metrics.recordFailure(e);
            throw e;
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private void checkCapability(SmsProvider target, SmsRequest request) {
        if (request.content() != null
                && !target.capabilities().supports(SmsCapability.SEND_SMS)) {
            throw new SmsProviderException("provider does not support SEND_SMS",
                    com.hsin.sms.spi.SmsErrorCategory.INVALID_REQUEST, false);
        }
        if (request.templateId() != null
                && !target.capabilities().supports(SmsCapability.TEMPLATE_SMS)) {
            throw new SmsProviderException("provider does not support TEMPLATE_SMS",
                    com.hsin.sms.spi.SmsErrorCategory.INVALID_REQUEST, false);
        }
    }

    // ---------------------------------------------------------------- stop

    /** Graceful stop with the configured drain/shutdown timeouts. */
    public void stop() {
        stop(settings.drainTimeout(), settings.shutdownTimeout());
    }

    /**
     * Graceful stop: RUNNING -> DRAINING -> STOPPING -> STOPPED. If in-flight calls
     * do not finish inside {@code drainTimeout}, the runtime proceeds with force
     * shutdown (interrupting workers) because the JVM cannot kill arbitrary code.
     */
    public void stop(Duration drainTimeout, Duration shutdownTimeout) {
        lifecycleLock.lock();
        try {
            PluginState current = state;
            if (current == PluginState.STOPPED || current == PluginState.UNLOADED) {
                return;
            }
            if (current == PluginState.LOADED) {
                transition(current, PluginState.STOPPED);
                metrics.recordStateChange();
                return;
            }
            if (current != PluginState.RUNNING && current != PluginState.DRAINING
                    && current != PluginState.STOPPING) {
                throw new PluginStateException(descriptor.id(), descriptor.version(),
                        "cannot stop plugin in state " + current);
            }
            long started = System.nanoTime();
            boolean drained = false;
            lifecycleGate.writeLock().lock();
            try {
                if (state == PluginState.RUNNING) {
                    transition(PluginState.RUNNING, PluginState.DRAINING);
                    waitForInFlight(drainTimeout);
                }
                if (state == PluginState.DRAINING) {
                    transition(PluginState.DRAINING, PluginState.STOPPING);
                }
                drained = true;
            } finally {
                lifecycleGate.writeLock().unlock();
            }
            if (!drained) {
                throw new PluginStateException(descriptor.id(), descriptor.version(),
                        "stop could not enter draining state");
            }
            List<Throwable> failures = stopProviderAndWorkers(shutdownTimeout);
            if (failures.isEmpty()) {
                transition(PluginState.STOPPING, PluginState.STOPPED);
                metrics.recordStopped(System.nanoTime() - started);
            } else {
                metrics.recordStopFailure(failures.get(0));
                transition(PluginState.STOPPING, PluginState.STOPPED);
                metrics.recordStopped(System.nanoTime() - started);
                throw new PluginStopException(descriptor.id(), descriptor.version(),
                        "stop finished with " + failures.size() + " failure(s); first: "
                                + rootMessage(failures.get(0)), failures.get(0));
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void waitForInFlight(Duration timeout) {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        synchronized (inFlightMonitor) {
            while (inFlight > 0) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) {
                    LOG.warning("plugin '" + descriptor.id() + "' drain timed out with "
                            + inFlight + " in-flight request(s) still executing");
                    return;
                }
                long millis = Math.max(1, remaining / 1_000_000);
                try {
                    inFlightMonitor.wait(millis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new PluginStopException(descriptor.id(), descriptor.version(),
                            "interrupted while draining plugin", e);
                }
            }
        }
    }

    private List<Throwable> stopProviderAndWorkers(Duration shutdownTimeout) {
        List<Throwable> failures = new ArrayList<>();
        withPluginClassLoader(() -> {
            SmsProvider current = provider;
            provider = null;
            if (current != null) {
                try {
                    current.destroy();
                } catch (Throwable t) {
                    failures.add(t);
                    LOG.log(Level.SEVERE, "provider.destroy failed for " + descriptor.id(), t);
                }
            }
        });
        shutdownWorkers(shutdownTimeout);
        failures.addAll(closeResourcesWithPluginClassLoader());
        return failures;
    }

    private void shutdownWorkers(Duration timeout) {
        ExecutorService executor = workers;
        workers = null;
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warning("plugin '" + descriptor.id() + "' worker executor did not terminate in "
                        + timeout.toMillis() + " ms; worker threads are daemon and were interrupted");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "interrupted while awaiting worker termination", e);
        }
    }

    // --------------------------------------------------------------- unload

    /**
     * Final release: stops if needed, closes provider resources, worker threads and
     * the class loader, and moves to UNLOADED. Must be reached from STOPPED/FAILED;
     * RUNNING is routed through the drain state machine.
     */
    public void unload() {
        lifecycleLock.lock();
        try {
            if (state == PluginState.UNLOADED) {
                return;
            }
            if (state == PluginState.RUNNING || state == PluginState.DRAINING
                    || state == PluginState.STOPPING) {
                stop();
            }
            if (state == PluginState.LOADED) {
                // A plugin that was loaded but never started has no runtime work to
                // drain; move it through the allowed transition to STOPPED.
                transition(state, PluginState.STOPPED);
            }
            List<Throwable> failures = new ArrayList<>(closeResourcesWithPluginClassLoader());
            shutdownWorkers(settings.shutdownTimeout());
            closeClassLoader();
            PluginState from = state;
            if (state != PluginState.UNLOADED) {
                transition(state, PluginState.UNLOADED);
            }
            metrics.recordStateChange();
            if (!failures.isEmpty()) {
                throw new PluginUnloadException(descriptor.id(), descriptor.version(),
                        "unload finished with " + failures.size() + " failure(s)", failures.get(0));
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void closeClassLoader() {
        if (classLoaderClosed) {
            return;
        }
        classLoaderClosed = true;
        PluginClassLoader loader = classLoader;
        classLoader = null;
        provider = null;
        if (loader != null) {
            try {
                loader.close();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "failed to close plugin class loader for " + descriptor.id(), e);
            }
        }
    }

    // -------------------------------------------------------------- helpers

    private void enterInFlight() {
        synchronized (inFlightMonitor) {
            inFlight++;
        }
    }

    private void leaveInFlight() {
        synchronized (inFlightMonitor) {
            inFlight--;
            inFlightMonitor.notifyAll();
        }
    }

    private void transition(PluginState expected, PluginState target) {
        if (state != expected) {
            throw new PluginStateException(descriptor.id(), descriptor.version(),
                    "illegal lifecycle transition: expected " + expected + " but state is " + state);
        }
        if (!expected.canTransitionTo(target)) {
            throw new PluginStateException(descriptor.id(), descriptor.version(),
                    "illegal lifecycle transition " + expected + " -> " + target);
        }
        state = target;
        metrics.recordStateChange();
        lastChange = new PluginStateChange(descriptor.id(), descriptor.version(),
                expected, target, System.currentTimeMillis());
    }

    private void fail(PluginState from, Throwable t) {
        // Mark FAILED first so concurrent callers observe a terminal state, then
        // release everything in dependency-safe order: resources -> workers -> loader.
        try {
            transition(from, PluginState.FAILED);
        } catch (PluginStateException ignore) {
            // If already failed, retain the first failure
        }
        closeResourcesWithPluginClassLoader();
        shutdownWorkers(Duration.ofSeconds(2));
        closeClassLoader();
        // Plugin-level failures are operational events, not host defects; metrics
        // and the FAILED state carry the diagnostics.
        LOG.log(Level.WARNING, "plugin '" + descriptor.id() + "' v" + descriptor.version()
                + " failed from " + from, t);
    }

    private void withPluginClassLoader(Runnable action) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        PluginClassLoader loader = classLoader;
        if (loader != null) {
            thread.setContextClassLoader(loader);
        }
        try {
            action.run();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private List<Throwable> closeResourcesWithPluginClassLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        PluginClassLoader loader = classLoader;
        if (loader != null) {
            thread.setContextClassLoader(loader);
        }
        try {
            return resourceRegistry.closeAll();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private PluginClassLoader requireClassLoader() {
        PluginClassLoader loader = classLoader;
        if (loader == null) {
            throw new PluginStateException(descriptor.id(), descriptor.version(),
                    "plugin class loader was closed");
        }
        return loader;
    }

    static String rootMessage(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String msg = current.getMessage();
        return (msg == null || msg.isBlank()) ? current.getClass().getSimpleName() : msg;
    }

    /** The immutable descriptor this runtime was built from. */
    public PluginDescriptor descriptor() {
        return descriptor;
    }

    /** Current lifecycle state. */
    public PluginState state() {
        return state;
    }

    /** Live plugin metrics snapshot. */
    public PluginMetricsSnapshot metricsSnapshot() {
        return metrics.snapshot();
    }

    /** Most recent successful state transition, if any. */
    public PluginStateChange lastChange() {
        return lastChange;
    }

    /** Number of provider calls still executing (worker-owned, including abandoned ones). */
    public int inFlight() {
        synchronized (inFlightMonitor) {
            return inFlight;
        }
    }

    /** Number of worker threads actually created by this runtime so far. */
    public long createdThreadCount() {
        PluginThreadFactory f = workerThreadFactory;
        return f == null ? 0 : f.createdThreadCount();
    }

    /** Builds the immutable diagnostics view used by the manager and the service layer. */
    public PluginSnapshot snapshot() {
        PluginClassLoader loader = classLoader;
        Class<?> providerClass = provider == null ? null : provider.getClass();
        PluginMetricsSnapshot metricsSnapshot = metrics.snapshot();
        return new PluginSnapshot(
                descriptor.id(),
                descriptor.name(),
                descriptor.version(),
                descriptor.vendor(),
                state,
                descriptor.spiVersion().toString(),
                descriptor.mainClass(),
                providerClass == null ? providerClassName : providerClass.getName(),
                loader == null ? null : loader.toString() + " [alive="
                        + (loader == null ? false : !classLoaderClosed) + "]",
                providerClass == null ? null
                        : providerClass.getClassLoader() + " [identity="
                        + Integer.toHexString(System.identityHashCode(providerClass.getClassLoader())) + "]",
                descriptor.allJars().stream().map(Path::toString).toList(),
                descriptor.pluginDir(),
                resourceRegistry.size(),
                resourceRegistry.names(),
                inFlight(),
                createdThreadCount(),
                metricsSnapshot.lastError(),
                metricsSnapshot);
    }

    /**
     * Host-side accessor used by diagnostics and integration tests to inspect
     * exactly which class loader loaded a plugin type. Plugins never see this API.
     */
    public ClassLoader pluginClassLoader() {
        return classLoader;
    }

    PluginConfigView configView() {
        return config;
    }

    // ------------------------------------------------------------------ context impl

    private final class SmsProviderContextImpl implements com.hsin.sms.spi.SmsProviderContext {

        private final String providerId;

        private SmsProviderContextImpl(String providerId) {
            this.providerId = providerId == null || providerId.isBlank()
                    ? descriptor.id() : providerId;
        }

        @Override
        public String pluginId() {
            return descriptor.id();
        }

        @Override
        public String pluginVersion() {
            return descriptor.version();
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public PluginConfig config() {
            return config;
        }

        @Override
        public Optional<String> secret(String key) {
            return secretProvider.resolve(key);
        }

        @Override
        public <T extends AutoCloseable> T registerResource(String name, T resource) {
            return resourceRegistry.register(name, resource);
        }

        @Override
        public java.util.concurrent.ThreadFactory newThreadFactory(String namePrefix) {
            return new PluginThreadFactory(descriptor.id(), namePrefix, classLoader, metrics);
        }

        @Override
        public ExecutorService newExecutor(String name, int poolSize) {
            ThreadFactory factory = new PluginThreadFactory(
                    descriptor.id(), "sms-plugin-" + descriptor.id() + "-" + name,
                    classLoader, metrics);
            ExecutorService executor = Executors.newFixedThreadPool(poolSize, factory);
            resourceRegistry.register("executor:" + name, () -> executor.shutdownNow());
            return executor;
        }

        @Override
        public java.util.concurrent.ScheduledExecutorService newScheduledExecutor(String name, int poolSize) {
            ThreadFactory factory = new PluginThreadFactory(
                    descriptor.id(), "sms-plugin-" + descriptor.id() + "-" + name,
                    classLoader, metrics);
            java.util.concurrent.ScheduledExecutorService executor =
                    Executors.newScheduledThreadPool(poolSize, factory);
            resourceRegistry.register("scheduled-executor:" + name,
                    () -> executor.shutdownNow());
            return executor;
        }
    }

}
