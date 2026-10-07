package com.moepus.serverwarashi.common.modules.performance.analyze;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.mixin.accessor.DistanceManagerAccessor;
import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderRef;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderTicketTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Comparator;

@GameTestHolder(ServerWarashi.MODID)
@PrefixGameTestTemplate(false)
public final class LoaderTargetLifecycleGameTests {
    private static final int DISTANCE = 2;
    private static final int CHURN_CYCLES = 1_000;
    private static final ResourceLocation CONTROLLER_ID = ResourceLocation.parse("test:loader_target");
    private static final TicketType<Object> LOADER = TicketType.create("loader_target_test", (a, b) -> 0);
    private static final TicketType<String> EXPIRING = TicketType.create(
            "loader_target_expiring_test", Comparator.<String>naturalOrder(), 1);
    private static final TicketType<String> PERMANENT = TicketType.create(
            "loader_target_permanent_test", Comparator.<String>naturalOrder());

    private LoaderTargetLifecycleGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty",
            batch = "loader-target-lifecycle")
    public static void tracksOnlyLiveTicketsDuringAndAfterAnalysis(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.start();
            fixture.churn();
            var report = fixture.controller.stop(true);
            helper.assertTrue(report.reportId() > 0L, "Stopped report must retain its attribution");
            helper.assertTrue(!TicketPerfRuntime.isProfiling(), "Analysis must be stopped");
            fixture.churn();
            fixture.expireAndClose();
            helper.succeed();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final ChunkPos chunk;
        private final BlockLoaderRef ref;
        private final Object owner;
        private final Object equalOwner;
        private final PerfSessionController controller;
        private PerfAttributionSnapshot snapshot;

        Fixture(GameTestHelper helper) throws Exception {
            this.helper = helper;
            level = helper.getLevel();
            BlockPos position = helper.absolutePos(BlockPos.ZERO);
            chunk = new ChunkPos(position);
            ref = BlockLoaderRef.of(level, CONTROLLER_ID, position);
            Class<?> ownerType = Class.forName(
                    "net.neoforged.neoforge.common.world.chunk.ForcedChunkManager$TicketOwner");
            Constructor<?> constructor = ownerType.getDeclaredConstructor(ResourceLocation.class, Comparable.class);
            constructor.setAccessible(true);
            owner = constructor.newInstance(CONTROLLER_ID, position);
            equalOwner = constructor.newInstance(CONTROLLER_ID, position);
            Field field = TicketPerfRuntime.class.getDeclaredField("CONTROLLER");
            field.setAccessible(true);
            controller = (PerfSessionController) field.get(null);
        }

        void start() {
            TicketPerfRuntime.clearRuntimeState();
            add();
            snapshot = PerfAttributionSnapshot.captureLoaderLayout(level.getServer());
            helper.assertTrue(controller.start(60, null, snapshot), "Analysis should start");
            assertTargets(1);
            remove();
            assertTargets(0);
        }

        void churn() {
            for (int i = 0; i < CHURN_CYCLES; i++) {
                add();
                Ticket<?> stored = snapshot.loaderTargets(ref).getFirst().ticket();
                add();
                assertTargets(1);
                helper.assertTrue(snapshot.loaderTargets(ref).getFirst().ticket() == stored,
                        "Renewal must preserve the stored ticket identity");
                remove();
                assertTargets(0);
            }
        }

        void expireAndClose() {
            TestDistanceManager manager = new TestDistanceManager();
            manager.addTicket(EXPIRING, chunk, 31, "expires");
            var access = (DistanceManagerAccessor) (Object) manager;
            Ticket<?> expiring = access.getTickets().get(chunk.toLong()).first();
            helper.assertTrue(snapshot.appendLoaderTarget(ref, new LoaderTicketTarget(chunk.toLong(), expiring)),
                    "Expired ticket should be tracked before purge");
            manager.purge();
            assertTargets(1);
            manager.purge();
            assertTargets(0);
            manager.addTicket(PERMANENT, chunk, 31, "closing");
            Ticket<?> closing = access.getTickets().get(chunk.toLong()).first();
            helper.assertTrue(snapshot.appendLoaderTarget(ref, new LoaderTicketTarget(chunk.toLong(), closing)),
                    "Closing ticket should be tracked before cleanup");
            manager.removeTicketsOnClosing();
            assertTargets(0);
        }

        private void add() {
            level.getChunkSource().addRegionTicket(LOADER, chunk, DISTANCE, owner, true);
        }

        private void remove() {
            level.getChunkSource().removeRegionTicket(LOADER, chunk, DISTANCE, equalOwner, true);
        }

        private void assertTargets(int expected) {
            helper.assertTrue(snapshot.loaderTargets(ref).size() == expected,
                    "Expected " + expected + " live loader targets, got " + snapshot.loaderTargets(ref).size());
        }

        @Override
        public void close() {
            remove();
            TicketPerfRuntime.clearRuntimeState();
        }
    }

    private static final class TestDistanceManager extends DistanceManager {
        TestDistanceManager() {
            super(Runnable::run, Runnable::run);
        }

        void purge() {
            purgeStaleTickets();
        }

        @Override
        protected boolean isChunkToRemove(long chunkPos) {
            return false;
        }

        @Override
        protected ChunkHolder getChunk(long chunkPos) {
            return null;
        }

        @Override
        protected ChunkHolder updateChunkScheduling(long chunkPos, int newLevel, ChunkHolder holder, int oldLevel) {
            return null;
        }
    }
}
