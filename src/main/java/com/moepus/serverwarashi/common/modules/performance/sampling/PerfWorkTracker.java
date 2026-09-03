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
    private static final Snapshot UNKNOWN = new Snapshot(
            TickType.TASK, null, UnknownWork.class, null, TickDetailKind.NONE,
            -1, false, 0, 0, 0, false, "", "Unknown", -1);

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
        for (int index = 0; index < depth; index++) {
            SLOTS[index].deactivate();
        }
        depth = 0;
        overflowDepth = 0;
        current = null;
    }

    public static boolean enterChunk(ServerLevel level, LevelChunk chunk) {
        Slot slot = claimSlot();
        if (slot != null) {
            slot.publishChunk(level.dimension().location(), chunk);
            current = slot;
        }
        return true;
    }

    public static boolean enterEntity(ServerLevel level, Entity entity) {
        Slot slot = claimSlot();
        if (slot != null) {
            slot.publishEntity(level.dimension().location(), entity);
            current = slot;
        }
        return true;
    }

    public static boolean enterBlockEntity(BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        Slot slot = claimSlot();
        if (slot != null) {
            slot.publishBlockEntity(level.dimension().location(), blockEntity);
            current = slot;
        }
        return true;
    }

    public static boolean exitBlockEntity(BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel)) {
            return false;
        }
        return exit(TickType.BLOCK_ENTITY);
    }

    public static boolean enterTask(BlockableEventLoop<?> eventLoop, Runnable task) {
        return enterTask(eventLoop.name(), task);
    }

    public static boolean enterTask(String dispatcher, Runnable task) {
        Slot slot = claimSlot();
        if (slot != null) {
            slot.publishTask(dispatcher, unwrap(task));
            current = slot;
        }
        return true;
    }

    public static boolean enterHandler() {
        Slot slot = claimSlot();
        if (slot != null) {
            slot.publishHandler();
            current = slot;
        }
        return true;
    }

    public static boolean exit(TickType expectedType) {
        if (overflowDepth > 0) {
            overflowDepth--;
            return true;
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

    static Snapshot unknownSnapshot() {
        return UNKNOWN;
    }

    static Thread profiledThread() {
        return isProfiling ? serverThread : null;
    }

    private static Slot claimSlot() {
        if (depth == MAX_DEPTH) {
            overflowDepth++;
            return null;
        }
        return SLOTS[depth++];
    }

    private static Runnable unwrap(Runnable task) {
        return task instanceof TickTask
                ? ((TickTaskAccessor) task).serverWarashi$getRunnable() : task;
    }

    private static Slot[] createSlots() {
        Slot[] slots = new Slot[MAX_DEPTH];
        for (int index = 0; index < slots.length; index++) {
            slots[index] = new Slot();
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
                           String displayName,
                           int entityAgeTicks) {
        public Snapshot(TickType type, ResourceLocation dimension, Class<?> workClass,
                        ResourceLocation detailId, TickDetailKind detailKind, int entityId,
                        boolean positioned, int x, int y, int z, boolean chunkPosition,
                        String eventLoop, String displayName) {
            this(type, dimension, workClass, detailId, detailKind, entityId, positioned,
                    x, y, z, chunkPosition, eventLoop, displayName, -1);
        }

        public Snapshot(TickType type, ResourceLocation dimension, Class<?> workClass,
                        ResourceLocation detailId, TickDetailKind detailKind, int entityId,
                        boolean positioned, int x, int y, int z, boolean chunkPosition,
                        String eventLoop) {
            this(type, dimension, workClass, detailId, detailKind, entityId, positioned,
                    x, y, z, chunkPosition, eventLoop, "", -1);
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
        private int entityAgeTicks;

        void publishChunk(ResourceLocation dimension, LevelChunk chunk) {
            var pos = chunk.getPos();
            beginPublish(TickType.CHUNK, chunk.getClass());
            setCommon(dimension, null, TickDetailKind.NONE, -1);
            setPosition(pos.x, 0, pos.z, true);
            entityAgeTicks = -1;
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
            setPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ(), false);
            eventLoop = null;
            entityAgeTicks = Math.max(1, entity.tickCount + 1);
            finishPublish();
        }

        void publishBlockEntity(ResourceLocation dimension, BlockEntity blockEntity) {
            var pos = blockEntity.getBlockPos();
            beginPublish(TickType.BLOCK_ENTITY, blockEntity.getClass());
            setCommon(dimension, BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()),
                    TickDetailKind.BLOCK, -1);
            setPosition(pos.getX(), pos.getY(), pos.getZ(), false);
            entityAgeTicks = -1;
            finishPublish();
        }

        void publishTask(String eventLoop, Runnable task) {
            beginPublish(TickType.TASK, task.getClass());
            setCommon(null, null, TickDetailKind.NONE, -1);
            clearPosition();
            this.eventLoop = eventLoop;
            entityAgeTicks = -1;
            finishPublish();
        }

        void publishHandler() {
            beginPublish(TickType.HANDLER, HandlerWork.class);
            setCommon(null, null, TickDetailKind.NONE, -1);
            clearPosition();
            eventLoop = "";
            displayName = "Handler";
            entityAgeTicks = -1;
            finishPublish();
        }

        private void beginPublish(TickType type, Class<?> workClass) {
            version++;
            this.type = type;
            this.workClass = workClass;
            displayName = "";
        }

        private void setCommon(ResourceLocation dimension, ResourceLocation detailId,
                               TickDetailKind detailKind, int entityId) {
            this.dimension = dimension;
            this.detailId = detailId;
            this.detailKind = detailKind;
            this.entityId = entityId;
            eventLoop = null;
        }

        private void setPosition(int x, int y, int z, boolean chunkPosition) {
            positioned = true;
            this.x = x;
            this.y = y;
            this.z = z;
            this.chunkPosition = chunkPosition;
        }

        private void clearPosition() {
            positioned = false;
            x = 0;
            y = 0;
            z = 0;
            chunkPosition = false;
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
                    entityId, positioned, x, y, z, chunkPosition,
                    eventLoop, displayName, entityAgeTicks);
            return before == version ? result : null;
        }
    }

    private static final class HandlerWork {
    }

    private static final class UnknownWork {
    }
}
