package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.util.function.Consumer;

public final class SamplingPlatform {
    private static final SamplingPlatform INSTANCE = new SamplingPlatform();
    private final boolean jfrAvailable = JfrAsyncStackSampler.isAvailable();

    private SamplingPlatform() {
    }

    static SamplingPlatform instance() {
        return INSTANCE;
    }

    public static void initialize() {
        instance();
    }

    AsyncStackSampler createSampler() {
        return new DetailSampler(jfrAvailable);
    }

    private static final class DetailSampler implements AsyncStackSampler {
        private final boolean preferJfr;
        private AsyncStackSampler delegate;
        private String startupFailure = "";

        DetailSampler(boolean preferJfr) {
            this.preferJfr = preferJfr;
        }

        @Override
        public void start(Thread target, int intervalMicros, Consumer<AsyncStackSample> sink) {
            if (preferJfr) {
                try {
                    delegate = new JfrAsyncStackSampler();
                    delegate.start(target, intervalMicros, sink);
                    startupFailure = "";
                    return;
                } catch (LinkageError | RuntimeException exception) {
                    startupFailure = "JFR startup failed: " + exception.getClass().getSimpleName();
                    if (delegate != null) {
                        delegate.stop();
                    }
                }
            }
            delegate = new UnavailableSampler(startupFailure.isEmpty()
                    ? "JFR detail sampling is unavailable" : startupFailure);
            delegate.start(target, intervalMicros, sink);
        }

        @Override
        public void stop() {
            if (delegate != null) {
                delegate.stop();
            }
        }

        @Override
        public String backendName() {
            return delegate == null ? "Not started" : delegate.backendName();
        }

        @Override
        public String degradationReason() {
            if (!startupFailure.isEmpty()) {
                return startupFailure;
            }
            return delegate == null ? "Sampler has not started" : delegate.degradationReason();
        }

        @Override
        public long settlingNanos() {
            return delegate == null ? 0L : delegate.settlingNanos();
        }
    }

    private static final class UnavailableSampler implements AsyncStackSampler {
        private final String reason;

        UnavailableSampler(String reason) {
            this.reason = reason;
        }

        @Override
        public void start(Thread target, int intervalMicros, Consumer<AsyncStackSample> sink) {
        }

        @Override
        public void stop() {
        }

        @Override
        public String backendName() {
            return "No JFR detail";
        }

        @Override
        public String degradationReason() {
            return reason;
        }

        @Override
        public long settlingNanos() {
            return 0L;
        }
    }
}
