package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ServerWarashi.MODID)
@PrefixGameTestTemplate(false)
public final class PerfStackAttributorGameTest {
    private PerfStackAttributorGameTest() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void attributesRealServerTickListener(GameTestHelper helper) {
        RuntimeProbe probe = new RuntimeProbe();
        NeoForge.EVENT_BUS.register(probe);
        helper.runAfterDelay(10, () -> NeoForge.EVENT_BUS.unregister(probe));
        helper.succeedWhen(() -> probe.assertCaptured(helper));
    }

    private static final class RuntimeProbe {
        private PerfWorkTracker.Snapshot captured;
        private boolean invoked;

        @SubscribeEvent
        public void onServerTick(ServerTickEvent.Pre event) {
            captured = PerfStackAttributor.capture(Thread.currentThread());
            invoked = true;
            NeoForge.EVENT_BUS.unregister(this);
        }

        void assertCaptured(GameTestHelper helper) {
            if (!invoked) {
                helper.fail("ServerTickEvent.Pre listener was not invoked");
            }
            if (captured == null || captured.type() != TickType.HANDLER) {
                helper.fail("ServerTickEvent.Pre listener was not attributed as HANDLER");
            }
            String shortName = "PerfStackAttributorGameTest.RuntimeProbe#onServerTick(ServerTickEvent.Pre)";
            if (!shortName.equals(captured.displayName())) {
                helper.fail("Unexpected handler short name: " + captured.displayName());
            }
            String fullName = RuntimeProbe.class.getName().replace('$', '.') + "#onServerTick("
                    + ServerTickEvent.Pre.class.getName().replace('$', '.') + ")";
            if (!fullName.equals(captured.eventLoop())) {
                helper.fail("Unexpected handler full name: " + captured.eventLoop());
            }
        }
    }
}
