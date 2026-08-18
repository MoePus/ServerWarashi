package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ServerWarashi.MODID)
@PrefixGameTestTemplate(false)
public final class PerfStackAttributorGameTest {
    private PerfStackAttributorGameTest() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void serverStartsWithStackSampling(GameTestHelper helper) {
        helper.succeed();
    }
}
