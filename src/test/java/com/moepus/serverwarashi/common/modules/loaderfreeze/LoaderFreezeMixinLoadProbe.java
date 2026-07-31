package com.moepus.serverwarashi.common.modules.loaderfreeze;

import com.moepus.serverwarashi.ServerWarashi;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Forces target transformation during the otherwise empty GameTest server startup. */
@EventBusSubscriber(modid = ServerWarashi.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class LoaderFreezeMixinLoadProbe {
    private static final String GAME_TEST_PROPERTY = "neoforge.gameTestServer";

    private LoaderFreezeMixinLoadProbe() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!Boolean.getBoolean(GAME_TEST_PROPERTY)) {
            return;
        }
        load("net.minecraft.server.level.DistanceManager");
        load("net.minecraft.server.level.ServerChunkCache");
    }

    private static void load(String className) {
        try {
            Class.forName(className, false, LoaderFreezeMixinLoadProbe.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to load Mixin target " + className, exception);
        }
    }
}
