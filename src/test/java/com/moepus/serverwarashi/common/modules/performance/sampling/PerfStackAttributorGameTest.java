package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;
import java.util.UUID;

@GameTestHolder(ServerWarashi.MODID)
@PrefixGameTestTemplate(false)
public final class PerfStackAttributorGameTest {
    private static final long HANDLER_WORK_NANOS = 50_000_000L;
    private static final int HANDLER_INVOCATIONS = 8;

    private PerfStackAttributorGameTest() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void attributesRealServerTickListener(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RuntimeProbe probe = new RuntimeProbe();
        NeoForge.EVENT_BUS.register(probe);
        if (!TicketPerfRuntime.start(server, 5, UUID.randomUUID()).started()) {
            helper.fail("Could not start performance sampling");
        }
        helper.runAfterDelay(2, () -> probe.fireNestedTickHooks(helper.getLevel()));
        helper.runAfterDelay(15, () -> probe.finish(server));
        helper.succeedWhen(() -> probe.assertCaptured(helper));
    }

    private static final class RuntimeProbe {
        private PerfReportData report;
        private int invocations;
        private Entity probeEntity;
        private Player probePlayer;
        private boolean entityHandlerScoped;
        private boolean playerHandlerScoped;
        private boolean entityTickScoped;
        private boolean entityScopeRestored;

        @SubscribeEvent
        public void onServerTick(ServerTickEvent.Pre event) {
            if (invocations >= HANDLER_INVOCATIONS || !TicketPerfRuntime.isProfiling()) {
                return;
            }
            invocations++;
            doHandlerWork();
            if (invocations == HANDLER_INVOCATIONS) {
                NeoForge.EVENT_BUS.unregister(this);
            }
        }

        @SubscribeEvent
        public void onEntityTick(EntityTickEvent.Pre event) {
            if (event.getEntity() != probeEntity) {
                return;
            }
            entityHandlerScoped = isHandlerScope();
            doHandlerWork();
        }

        @SubscribeEvent
        public void onPlayerTick(PlayerTickEvent.Pre event) {
            if (event.getEntity() != probePlayer) {
                return;
            }
            playerHandlerScoped = isHandlerScope();
            doHandlerWork();
        }

        void fireNestedTickHooks(ServerLevel level) {
            probeEntity = createProbeEntity(level);
            probePlayer = FakePlayerFactory.getMinecraft(level);
            fireRealEntityTick(level);
            fireEntityHook(level, probeEntity);
            firePlayerHook(level, probePlayer);
        }

        private Entity createProbeEntity(ServerLevel level) {
            return new Pig(EntityType.PIG, level) {
                @Override
                public void tick() {
                    entityTickScoped = isEntityScope();
                    super.tick();
                }
            };
        }

        private void fireRealEntityTick(ServerLevel level) {
            PerfWorkTracker.Snapshot parent = PerfWorkTracker.snapshot();
            level.tickNonPassenger(probeEntity);
            entityScopeRestored = Objects.equals(parent, PerfWorkTracker.snapshot());
        }

        private static void fireEntityHook(ServerLevel level, Entity entity) {
            PerfWorkTracker.enterEntity(level, entity);
            try {
                EventHooks.fireEntityTickPre(entity);
            } finally {
                PerfWorkTracker.exit(TickType.ENTITY);
            }
        }

        private static void firePlayerHook(ServerLevel level, Player player) {
            PerfWorkTracker.enterEntity(level, player);
            try {
                EventHooks.firePlayerTickPre(player);
            } finally {
                PerfWorkTracker.exit(TickType.ENTITY);
            }
        }

        void finish(MinecraftServer server) {
            NeoForge.EVENT_BUS.unregister(this);
            report = TicketPerfRuntime.stopCurrentAnalysis(server).data();
        }

        void assertCaptured(GameTestHelper helper) {
            if (invocations != HANDLER_INVOCATIONS || report == null) {
                helper.fail("Waiting for finalized async sampling report");
            }
            if (!report.sampling().backend().startsWith("Stackless primary + JFR")) {
                helper.fail("Unexpected sampling backend: " + report.sampling().backend());
            }
            if (!entityHandlerScoped || !playerHandlerScoped) {
                helper.fail("EntityTickEvent/PlayerTickEvent did not override Entity context");
            }
            if (!entityTickScoped || !entityScopeRestored) {
                helper.fail("Entity tick scope did not wrap only Entity.tick() and restore its parent");
            }
            PerfReportData.TickableEntry handler = report.tickables().stream()
                    .filter(entry -> entry.type() == TickType.HANDLER)
                    .findFirst().orElse(null);
            if (handler == null) {
                helper.fail("ServerTickEvent.Pre listener was not attributed as HANDLER: backend="
                        + report.sampling() + ", totalSamples=" + report.totalSamples());
            }
            boolean generic = handler.attributionSource()
                    == PerfReportData.AttributionSource.TASK_CONTEXT
                    && handler.confidence() == PerfReportData.Confidence.HIGH;
            boolean detailed = handler.attributionSource()
                    == PerfReportData.AttributionSource.ASYNC_STACK
                    && handler.confidence() == PerfReportData.Confidence.MEDIUM;
            if (!generic && !detailed) {
                helper.fail("Unexpected handler attribution quality");
            }
        }

        private static boolean isHandlerScope() {
            PerfWorkTracker.Snapshot snapshot = PerfWorkTracker.snapshot();
            return snapshot != null && snapshot.type() == TickType.HANDLER;
        }

        private static boolean isEntityScope() {
            PerfWorkTracker.Snapshot snapshot = PerfWorkTracker.snapshot();
            return snapshot != null && snapshot.type() == TickType.ENTITY;
        }

        private static void doHandlerWork() {
            long deadline = System.nanoTime() + HANDLER_WORK_NANOS;
            while (System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
        }

    }
}
