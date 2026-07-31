package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.mojang.logging.LogUtils;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import org.slf4j.Logger;

import java.util.Locale;

final class WindowsTimerResolution {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PERIOD_MILLIS = 1;
    private static final int SUCCESS = 0;
    private static final int PROCESS_POWER_THROTTLING = 4;
    private static final int POWER_THROTTLING_VERSION = 1;
    private static final int IGNORE_TIMER_RESOLUTION = 1 << 2;
    private static final boolean WINDOWS = isWindows(System.getProperty("os.name", ""));

    private WinMM winMM;
    private Kernel32 kernel32;
    private ProcessPowerState previousPowerState;
    private boolean enabled;
    private boolean unavailable;

    void enable() {
        if (!WINDOWS || enabled || unavailable) {
            return;
        }
        claimTimerPolicy();
        try {
            if (winMM == null) {
                winMM = Native.load("winmm", WinMM.class);
            }
            int result = winMM.timeBeginPeriod(PERIOD_MILLIS);
            enabled = result == SUCCESS;
            unavailable = !enabled;
            if (!enabled) {
                LOGGER.warn("timeBeginPeriod({}) failed with result {}", PERIOD_MILLIS, result);
                restoreTimerPolicy();
            }
        } catch (LinkageError | RuntimeException exception) {
            unavailable = true;
            restoreTimerPolicy();
            LOGGER.warn("Unable to enable the Windows high-resolution timer", exception);
        }
    }

    void disable() {
        if (!enabled) {
            return;
        }
        enabled = false;
        try {
            int result = winMM.timeEndPeriod(PERIOD_MILLIS);
            if (result != SUCCESS) {
                LOGGER.warn("timeEndPeriod({}) failed with result {}", PERIOD_MILLIS, result);
            }
        } catch (LinkageError | RuntimeException exception) {
            LOGGER.warn("Unable to disable the Windows high-resolution timer", exception);
        } finally {
            restoreTimerPolicy();
        }
    }

    private void claimTimerPolicy() {
        try {
            if (kernel32 == null) {
                kernel32 = Native.load("kernel32", Kernel32.class);
            }
            ProcessPowerState original = readPowerState();
            TimerPolicy desired = honorTimerResolution(original.controlMask, original.stateMask);
            if (desired.controlMask() == original.controlMask
                    && desired.stateMask() == original.stateMask) {
                return;
            }
            writePowerState(desired.controlMask(), desired.stateMask());
            previousPowerState = original;
        } catch (LinkageError | RuntimeException exception) {
            LOGGER.warn("Unable to keep Windows timer resolution active for the sampler", exception);
        }
    }

    private ProcessPowerState readPowerState() {
        ProcessPowerState state = new ProcessPowerState();
        state.version = POWER_THROTTLING_VERSION;
        state.write();
        int result = kernel32.GetProcessInformation(
                kernel32.GetCurrentProcess(), PROCESS_POWER_THROTTLING, state, state.size());
        if (result == 0) {
            throw new IllegalStateException("GetProcessInformation failed with error " + Native.getLastError());
        }
        state.read();
        return state;
    }

    private void writePowerState(int controlMask, int stateMask) {
        ProcessPowerState state = new ProcessPowerState();
        state.version = POWER_THROTTLING_VERSION;
        state.controlMask = controlMask;
        state.stateMask = stateMask;
        state.write();
        int result = kernel32.SetProcessInformation(
                kernel32.GetCurrentProcess(), PROCESS_POWER_THROTTLING, state, state.size());
        if (result == 0) {
            throw new IllegalStateException("SetProcessInformation failed with error " + Native.getLastError());
        }
    }

    private void restoreTimerPolicy() {
        ProcessPowerState previous = previousPowerState;
        if (previous == null) {
            return;
        }
        try {
            writePowerState(previous.controlMask, previous.stateMask);
            previousPowerState = null;
        } catch (LinkageError | RuntimeException exception) {
            LOGGER.warn("Unable to restore the Windows timer-resolution policy", exception);
        }
    }

    static boolean isWindows(String osName) {
        return osName.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    static TimerPolicy honorTimerResolution(int controlMask, int stateMask) {
        return new TimerPolicy(controlMask | IGNORE_TIMER_RESOLUTION,
                stateMask & ~IGNORE_TIMER_RESOLUTION);
    }

    record TimerPolicy(int controlMask, int stateMask) {
    }

    private interface WinMM extends StdCallLibrary {
        int timeBeginPeriod(int periodMillis);

        int timeEndPeriod(int periodMillis);
    }

    private interface Kernel32 extends StdCallLibrary {
        Pointer GetCurrentProcess();

        int GetProcessInformation(Pointer process,
                                  int informationClass,
                                  ProcessPowerState state,
                                  int informationSize);

        int SetProcessInformation(Pointer process,
                                  int informationClass,
                                  ProcessPowerState state,
                                  int informationSize);
    }

    @Structure.FieldOrder({"version", "controlMask", "stateMask"})
    public static final class ProcessPowerState extends Structure {
        public int version;
        public int controlMask;
        public int stateMask;
    }
}
