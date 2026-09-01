package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class UnknownStackReservoirTest {
    @Test
    void retainsAtMostTwoHundredFiftySixRawStacks() {
        UnknownStackReservoir reservoir = new UnknownStackReservoir(7L);
        StackTraceElement[] first = stack(0);
        reservoir.record(first, 1L);
        for (int sample = 1; sample < 1_000; sample++) {
            reservoir.record(stack(sample), sample / 10L + 1L);
        }

        UnknownStackSnapshot snapshot = reservoir.snapshot(3L, 100L, 1_000);

        assertEquals(1_000L, snapshot.totalObserved());
        assertEquals(UnknownStackReservoir.CAPACITY, snapshot.retainedCount());
        assertEquals(1_000L, snapshot.revision());
        UnknownStackReservoir initialOnly = new UnknownStackReservoir(1L);
        initialOnly.record(first, 9L);
        assertSame(first, initialOnly.snapshot(1L, 1L, 1_000).stackAt(0));
    }

    @Test
    void replacementIsDeterministicForASeed() {
        UnknownStackReservoir first = new UnknownStackReservoir(123L);
        UnknownStackReservoir second = new UnknownStackReservoir(123L);
        for (int sample = 0; sample < 2_000; sample++) {
            first.record(stack(sample), sample / 4L + 1L);
            second.record(stack(sample), sample / 4L + 1L);
        }

        UnknownStackSnapshot left = first.snapshot(1L, 500L, 1_000);
        UnknownStackSnapshot right = second.snapshot(1L, 500L, 1_000);
        for (int index = 0; index < left.retainedCount(); index++) {
            assertEquals(left.stackAt(index)[0].getMethodName(),
                    right.stackAt(index)[0].getMethodName());
            assertEquals(left.tickIdAt(index), right.tickIdAt(index));
        }
    }

    @Test
    void recordingHotPathContainsNoInvokeDynamicInstruction() throws Exception {
        assertNoInvokeDynamic(PerfSamplerSession.class, "sampleCurrent");
        assertNoInvokeDynamic(UnknownStackReservoir.class, "record");
    }

    private static void assertNoInvokeDynamic(Class<?> type, String methodName) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(type.getName()).accept(node, 0);
        var record = node.methods.stream()
                .filter(method -> method.name.equals(methodName))
                .findFirst().orElseThrow();

        List<?> instructions = List.of(record.instructions.toArray());
        assertFalse(instructions.stream().anyMatch(InvokeDynamicInsnNode.class::isInstance));
    }

    private static StackTraceElement[] stack(int sample) {
        return new StackTraceElement[]{new StackTraceElement(
                "example.Work" + sample, "sample" + sample, "Work.java", sample + 1)};
    }
}
