package com.moepus.serverwarashi.common.modules.performance.sampling;

import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class JfrAsyncStackSampler implements AsyncStackSampler {
    private static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    private static final String NATIVE_SAMPLE = "jdk.NativeMethodSample";
    private static final int MAX_STACK_DEPTH = 96;
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 256;
    private static final long SETTLING_NANOS = Duration.ofSeconds(1L).toNanos();
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5L);
    private static final long START_TIMEOUT_SECONDS = 5L;

    private RecordingStream stream;
    private final DetailEventGate eventGate = new DetailEventGate();
    private volatile String degradationReason = "";
    private long targetThreadId;
    private long baseNanos;
    private Instant baseInstant;
    private Consumer<AsyncStackSample> sink;
    private volatile boolean stopping;

    static boolean isAvailable() {
        try {
            return FlightRecorder.isAvailable();
        } catch (LinkageError | RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void start(Thread target, int intervalMicros, Consumer<AsyncStackSample> sampleSink) {
        targetThreadId = target.threadId();
        sink = sampleSink;
        eventGate.reset(intervalMicros * 1_000L);
        stopping = false;
        degradationReason = "";
        calibrateClock();
        RecordingStream recording = new RecordingStream();
        CountDownLatch ready = new CountDownLatch(1);
        configure(recording, intervalMicros);
        recording.onEvent(EXECUTION_SAMPLE, this::onSample);
        if (hasEvent(NATIVE_SAMPLE)) {
            recording.onEvent(NATIVE_SAMPLE, this::onSample);
        }
        recording.onError(error -> degradationReason = failureMessage(error));
        recording.onMetadata(event -> ready.countDown());
        recording.onClose(() -> {
            if (!stopping && degradationReason.isEmpty()) {
                degradationReason = "JFR detail stream closed unexpectedly";
            }
        });
        try {
            stream = recording;
            recording.startAsync();
            awaitStart(ready);
        } catch (RuntimeException | Error exception) {
            stream = null;
            recording.close();
            throw exception;
        }
    }

    @Override
    public void stop() {
        RecordingStream recording = stream;
        stream = null;
        if (recording != null) {
            stopping = true;
            try {
                recording.stop();
                recording.awaitTermination(STOP_TIMEOUT);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                degradationReason = "Interrupted while draining JFR detail stream";
            } catch (RuntimeException failure) {
                degradationReason = failureMessage(failure);
            } finally {
                close(recording);
            }
        }
    }

    @Override
    public String backendName() {
        return "JFR AsyncGetCallTrace";
    }

    @Override
    public String degradationReason() {
        return degradationReason;
    }

    @Override
    public long settlingNanos() {
        return SETTLING_NANOS;
    }

    private void configure(RecordingStream recording, int intervalMicros) {
        Duration period = Duration.ofNanos(intervalMicros * 1_000L);
        if (!hasEvent(EXECUTION_SAMPLE)) {
            throw new IllegalStateException("JFR execution sampling event is unavailable");
        }
        recording.enable(EXECUTION_SAMPLE).withPeriod(period).withStackTrace();
        if (hasEvent(NATIVE_SAMPLE)) {
            recording.enable(NATIVE_SAMPLE).withPeriod(period).withStackTrace();
        }
    }

    private void close(RecordingStream recording) {
        try {
            recording.close();
        } catch (RuntimeException failure) {
            degradationReason = failureMessage(failure);
        }
    }

    private static void awaitStart(CountDownLatch ready) {
        try {
            if (!ready.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out starting JFR detail stream");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting JFR detail stream", exception);
        }
    }

    private static boolean hasEvent(String eventName) {
        return FlightRecorder.getFlightRecorder().getEventTypes().stream()
                .anyMatch(event -> eventName.equals(event.getName()));
    }

    private void calibrateClock() {
        long before = System.nanoTime();
        baseInstant = Instant.now();
        long after = System.nanoTime();
        baseNanos = before + (after - before) / 2L;
    }

    private void onSample(RecordedEvent event) {
        RecordedThread thread = event.hasField("sampledThread")
                ? event.getThread("sampledThread") : event.getThread();
        if (thread == null || thread.getJavaThreadId() != targetThreadId) {
            return;
        }
        long timestampNanos = toNanos(event.getStartTime());
        if (!eventGate.accept(timestampNanos)) {
            return;
        }
        sink.accept(new AsyncStackSample(timestampNanos, stack(event)));
    }

    private long toNanos(Instant timestamp) {
        return baseNanos + Duration.between(baseInstant, timestamp).toNanos();
    }

    private static StackTraceElement[] stack(RecordedEvent event) {
        RecordedStackTrace trace = event.getStackTrace();
        if (trace == null) {
            return new StackTraceElement[0];
        }
        List<RecordedFrame> frames = trace.getFrames();
        int size = Math.min(frames.size(), MAX_STACK_DEPTH);
        StackTraceElement[] stack = new StackTraceElement[size];
        for (int index = 0; index < size; index++) {
            RecordedFrame frame = frames.get(index);
            stack[index] = new StackTraceElement(frame.getMethod().getType().getName(),
                    frame.getMethod().getName(), null, frame.getLineNumber());
        }
        return stack;
    }

    private static String failureMessage(Throwable error) {
        String message = error.getMessage();
        String failure = "JFR stream failed: " + error.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return failure.length() <= MAX_FAILURE_MESSAGE_LENGTH ? failure
                : failure.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }
}
