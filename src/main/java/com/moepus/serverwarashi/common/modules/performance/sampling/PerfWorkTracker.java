package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.mixin.accessor.TickTaskAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public final class PerfWorkTracker {
    private static final int MAX_DEPTH = 32;
    private static final Slot[] SLOTS = createSlots();

    public static volatile boolean isProfiling;
    private static volatile Slot current;
    private static volatile Thread serverThread;
    private static int depth;
    private static int overflowDepth;

    private PerfWorkTracker() {
    }

    public static void activate(Thread thread) {
        serverThread = thread;
        depth = 0;
        overflowDepth = 0;
        current = null;
        isProfiling = true;
    }

    public static void deactivate() {
        isProfiling = false;
        clearCurrent();
        serverThread = null;
    }

    public static void clearCurrent() {
        for (int i = 0; i < depth; i++) {
            SLOTS[i].deactivate();
        }
        depth = 0;
        overflowDepth = 0;
        current = null;
    }

    public static boolean enterChunk(ServerLevel level, LevelChunk chunk) {
        if (!canPublish()) {
            return false;
        }
        Slot slot = claimSlot();
        if (slot != null) {
            slot.publishChunk(level.dimension().location(), chunk);
            current = slot;
        }
        return true;
    }

    public static boolean enterEntity(ServerLevel level, Entity entity) {
        if (!canPublish()) {
            return false;
        }
        Slot slot = claimSlot();
        if (slot != null) {
            slot.publishEntity(level.dimension().location(), entity);
            current = slot;
        }
        return true;
    }

    public static boolean enterBlockEntity(BlockEntity blockEntity) {
        if (!canPublish() || !(blockEntity.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        Slot slot = claimSlot();
        if (slot != null) {
            slot.publishBlockEntity(level.dimension().location(), blockEntity);
            current = slot;
        }
        return true;
    }

    public static boolean enterTask(BlockableEventLoop<?> eventLoop, Runnable task) {
        if (!canPublish()) {
            return false;
        }
        Slot slot = claimSlot();
        if (slot != null) {
            slot.publishTask(eventLoop.name(), unwrap(task));
            current = slot;
        }
        return true;
    }

    public static boolean exit(TickType expectedType) {
        if (Thread.currentThread() != serverThread || overflowDepth > 0) {
            return exitOverflow();
        }
        Slot slot = current;
        if (slot == null || slot.type != expectedType) {
            return false;
        }
        slot.deactivate();
        depth--;
        current = depth == 0 ? null : SLOTS[depth - 1];
        return true;
    }

    public static Snapshot snapshot() {
        for (int attempt = 0; attempt < 3; attempt++) {
            Slot slot = current;
            if (slot == null) {
                return null;
            }
            Snapshot snapshot = slot.snapshot();
            if (snapshot != null) {
                return snapshot;
            }
        }
        return null;
    }

    static Thread profiledThread() {
        return isProfiling ? serverThread : null;
    }

    private static boolean canPublish() {
        return isProfiling && Thread.currentThread() == serverThread;
    }

    private static Slot claimSlot() {
        if (depth == MAX_DEPTH) {
            overflowDepth++;
            return null;
        }
        return SLOTS[depth++];
    }

    private static boolean exitOverflow() {
        if (Thread.currentThread() != serverThread || overflowDepth == 0) {
            return false;
        }
        overflowDepth--;
        return true;
    }

    private static Runnable unwrap(Runnable task) {
        if (task instanceof TickTask) {
            return ((TickTaskAccessor) task).serverWarashi$getRunnable();
        }
        return task;
    }

    private static Slot[] createSlots() {
        Slot[] slots = new Slot[MAX_DEPTH];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new Slot();
        }
        return slots;
    }

    public record Snapshot(TickType type,
                           ResourceLocation dimension,
                           Class<?> workClass,
                           ResourceLocation detailId,
                           TickDetailKind detailKind,
                           int entityId,
                           boolean positioned,
                           int x,
                           int y,
                           int z,
                           boolean chunkPosition,
                           String eventLoop,
                           String displayName) {
        public Snapshot(TickType type,
                        ResourceLocation dimension,
                        Class<?> workClass,
                        ResourceLocation detailId,
                        TickDetailKind detailKind,
                        int entityId,
                        boolean positioned,
                        int x,
                        int y,
                        int z,
                        boolean chunkPosition,
                        String eventLoop) {
            this(type, dimension, workClass, detailId, detailKind, entityId,
                    positioned, x, y, z, chunkPosition, eventLoop, "");
        }
    }

    private static final class Slot {
        private volatile long version;
        private boolean active;
        private TickType type;
        private ResourceLocation dimension;
        private Class<?> workClass;
        private ResourceLocation detailId;
        private TickDetailKind detailKind;
        private int entityId;
        private boolean positioned;
        private int x;
        private int y;
        private int z;
        private boolean chunkPosition;
        private String eventLoop;
        private String displayName;

        void publishChunk(ResourceLocation dimension, LevelChunk chunk) {
            var pos = chunk.getPos();
            beginPublish(TickType.CHUNK, chunk.getClass());
            this.dimension = dimension;
            detailId = null;
            detailKind = TickDetailKind.NONE;
            entityId = -1;
            positioned = true;
            x = pos.x;
            y = 0;
            z = pos.z;
            chunkPosition = true;
            eventLoop = null;
            finishPublish();
        }

        void publishEntity(ResourceLocation dimension, Entity entity) {
            beginPublish(TickType.ENTITY, entity.getClass());
            this.dimension = dimension;
            if (entity instanceof ItemEntity itemEntity) {
                detailId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
                detailKind = TickDetailKind.ITEM;
            } else {
                detailId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                detailKind = TickDetailKind.ENTITY;
            }
            entityId = entity.getId();
            positioned = true;
            x = entity.getBlockX();
            y = entity.getBlockY();
            z = entity.getBlockZ();
            chunkPosition = false;
            eventLoop = null;
            finishPublish();
        }

        void publishBlockEntity(ResourceLocation dimension, BlockEntity blockEntity) {
            var pos = blockEntity.getBlockPos();
            beginPublish(TickType.BLOCK_ENTITY, blockEntity.getClass());
            this.dimension = dimension;
            detailId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
            detailKind = TickDetailKind.BLOCK;
            entityId = -1;
            positioned = true;
            x = pos.getX();
            y = pos.getY();
            z = pos.getZ();
            chunkPosition = false;
            eventLoop = null;
            finishPublish();
        }

        void publishTask(String eventLoop, Runnable task) {
            beginPublish(TickType.TASK, task.getClass());
            dimension = null;
            detailId = null;
            detailKind = TickDetailKind.NONE;
            entityId = -1;
            positioned = false;
            x = 0;
            y = 0;
            z = 0;
            chunkPosition = false;
            this.eventLoop = eventLoop;
            finishPublish();
        }

        private void beginPublish(TickType type, Class<?> workClass) {
            version++;
            this.type = type;
            this.workClass = workClass;
            displayName = "";
        }

        private void finishPublish() {
            active = true;
            version++;
        }

        void deactivate() {
            version++;
            active = false;
            version++;
        }

        Snapshot snapshot() {
            long before = version;
            if ((before & 1L) != 0L || !active) {
                return null;
            }
            Snapshot result = new Snapshot(type, dimension, workClass, detailId, detailKind,
                    entityId, positioned,
                    x, y, z, chunkPosition, eventLoop, displayName);
            return before == version ? result : null;
        }
    }
}
