package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class AsyncSampleIngress {
    private static final int QUEUE_CAPACITY = 8_192;
    private static final long POLL_MILLIS = 10L;
    private static final long DRAIN_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5L);

    private final ArrayBlockingQueue<AsyncStackSample> queue =
            new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong droppedSamples = new AtomicLong();
    private final AtomicLong enqueuedSamples = new AtomicLong();
    private final AtomicLong processedSamples = new AtomicLong();
    private final AtomicLong workerCycles = new AtomicLong();
    private final AsyncStackSampler sampler;
    private final Consumer<AsyncStackSample> processor;
    private final Runnable idleWork;
    private final Thread worker;
    private volatile boolean shutdown;
    private volatile String ingressFailure = "";
    private boolean backendRunning;
    private boolean workerStarted;

    AsyncSampleIngress(AsyncStackSampler sampler,
                       Consumer<AsyncStackSample> processor,
                       Runnable idleWork) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.idleWork = Objects.requireNonNull(idleWork, "idleWork");
        worker = new Thread(this::runWorker, "ServerWarashi-PerfAggregation");
        worker.setDaemon(true);
    }

    void start(Thread target, int intervalMicros) {
        if (workerStarted) {
            throw new IllegalStateException("Sample ingress has already started");
        }
        workerStarted = true;
        worker.start();
        tryStartBackend(target, intervalMicros);
    }

    void pause() {
        stopBackend();
        awaitDrain();
    }

    void resume(Thread target, int intervalMicros) {
        tryStartBackend(target, intervalMicros);
    }

    void stop() {
        stopBackend();
        shutdown = true;
        joinWorker();
    }

    void awaitDrain() {
        long deadline = System.nanoTime() + DRAIN_TIMEOUT_NANOS;
        long target = enqueuedSamples.get();
        long completedCycle = workerCycles.get();
        while ((processedSamples.get() < target || workerCycles.get() <= completedCycle)
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    long droppedSamples() {
        return droppedSamples.get();
    }

    String backendName() {
        return sampler.backendName();
    }

    String degradationReason() {
        String backendFailure = sampler.degradationReason();
        if (ingressFailure.isEmpty()) {
            return backendFailure;
        }
        return backendFailure.isEmpty()
                ? ingressFailure : ingressFailure + "; " + backendFailure;
    }

    long settlingNanos() {
        return sampler.settlingNanos();
    }

    private void runWorker() {
        try {
            while (!shutdown || !queue.isEmpty()) {
                AsyncStackSample sample = queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (sample != null) {
                    process(sample);
                }
                runIdleWork();
                workerCycles.incrementAndGet();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            AsyncStackSample sample;
            while ((sample = queue.poll()) != null) {
                process(sample);
            }
        }
    }

    private void process(AsyncStackSample sample) {
        try {
            processor.accept(sample);
        } catch (RuntimeException | LinkageError failure) {
            ingressFailure = failureMessage("detail processing failed", failure);
        } finally {
            processedSamples.incrementAndGet();
        }
    }

    private void runIdleWork() {
        try {
            idleWork.run();
        } catch (RuntimeException | LinkageError failure) {
            ingressFailure = failureMessage("detail finalization failed", failure);
        }
    }

    private void tryStartBackend(Thread target, int intervalMicros) {
        try {
            sampler.start(target, intervalMicros, this::enqueue);
            backendRunning = true;
        } catch (RuntimeException | LinkageError failure) {
            backendRunning = false;
            ingressFailure = failureMessage("detail sampler startup failed", failure);
            try {
                sampler.stop();
            } catch (RuntimeException | LinkageError ignored) {
                // The primary sampler remains authoritative when detail startup fails.
            }
        }
    }

    private void stopBackend() {
        if (!backendRunning) {
            return;
        }
        backendRunning = false;
        sampler.stop();
    }

    private void enqueue(AsyncStackSample sample) {
        if (queue.offer(sample)) {
            enqueuedSamples.incrementAndGet();
        } else {
            droppedSamples.incrementAndGet();
        }
    }

    private void joinWorker() {
        if (!workerStarted) {
            return;
        }
        try {
            worker.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping sample ingress", exception);
        }
    }

    private static String failureMessage(String prefix, Throwable failure) {
        return prefix + ": " + failure.getClass().getSimpleName();
    }
}
