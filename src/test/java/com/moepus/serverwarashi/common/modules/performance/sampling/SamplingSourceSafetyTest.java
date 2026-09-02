package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SamplingSourceSafetyTest {
    @Test
    void mainSourceContainsNoSynchronousCrossThreadStackCapture() throws IOException {
        Path root = Path.of("src/main/java");
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("ThreadMXBean"), path.toString());
                assertFalse(source.contains("getThreadInfo("), path.toString());
                assertFalse(source.contains("ThreadDump"), path.toString());
                assertFalse(source.contains("Thread.getStackTrace("), path.toString());
            }
        }
    }
}
