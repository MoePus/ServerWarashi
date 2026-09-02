package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfWorkTrackerAllocationTest {
    private static final Set<Integer> ALLOCATING_OPCODES = Set.of(
            Opcodes.NEW, Opcodes.NEWARRAY, Opcodes.ANEWARRAY,
            Opcodes.MULTIANEWARRAY, Opcodes.INVOKEDYNAMIC);

    @Test
    void serverThreadPublicationMethodsContainNoAllocatingBytecode() throws Exception {
        assertAllocationFree(PerfWorkTracker.class, Set.of(
                "enterChunk", "enterEntity", "enterBlockEntity", "enterTask", "enterHandler",
                "exit", "claimSlot", "unwrap"));
        Class<?> slot = Class.forName(PerfWorkTracker.class.getName() + "$Slot");
        assertAllocationFree(slot, Set.of(
                "publishChunk", "publishEntity", "publishBlockEntity", "publishTask",
                "publishHandler", "beginPublish", "setCommon", "setPosition",
                "clearPosition", "finishPublish", "deactivate"));
    }

    @Test
    void trackerHotPathDoesNotReadTheClock() throws Exception {
        assertNoInvocation(PerfWorkTracker.class, "nanoTime");
    }

    @Test
    void primaryObservationPublicationDoesNotAllocate() throws Exception {
        assertAllocationFree(PrimaryObservationRing.class, Set.of("record", "index"));
    }

    @Test
    void transitionTimelineClassesAreRemoved() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.moepus.serverwarashi.common.modules.performance.sampling.ContextTimeline"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.moepus.serverwarashi.common.modules.performance.sampling.TaskContext"));
    }

    private static void assertAllocationFree(Class<?> type, Set<String> methodNames)
            throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(type.getName()).accept(node, 0);
        int matched = 0;
        for (var method : node.methods) {
            if (!methodNames.contains(method.name)) {
                continue;
            }
            matched++;
            for (AbstractInsnNode instruction : method.instructions) {
                assertFalse(ALLOCATING_OPCODES.contains(instruction.getOpcode()),
                        type.getSimpleName() + "." + method.name
                                + " contains allocating opcode " + instruction.getOpcode());
            }
        }
        assertTrue(matched >= methodNames.size(), "Missing hot-path method in " + type.getName());
    }

    private static void assertNoInvocation(Class<?> type, String methodName) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(type.getName()).accept(node, 0);
        for (var method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode invocation) {
                    assertFalse(invocation.name.equals(methodName),
                            type.getSimpleName() + "." + method.name + " calls " + methodName);
                }
            }
        }
    }
}
