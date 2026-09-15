package com.moepus.serverwarashi.common.gametest;

import com.moepus.serverwarashi.ServerWarashi;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Forces target transformation during the otherwise empty GameTest server startup. */
@EventBusSubscriber(modid = ServerWarashi.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class MixinLoadProbe {
    private static final String GAME_TEST_PROPERTY = "neoforge.gameTestServer";
    private static final String[] TARGET_CLASSES = {
            "net.minecraft.server.level.DistanceManager",
            "net.minecraft.server.level.ServerChunkCache",
            "net.minecraft.world.level.entity.PersistentEntitySectionManager",
            "net.minecraft.world.level.entity.ChunkEntities"
    };

    private MixinLoadProbe() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!Boolean.getBoolean(GAME_TEST_PROPERTY)) {
            return;
        }
        for (String className : TARGET_CLASSES) {
            load(className);
        }
    }

    private static void load(String className) {
        try {
            Class.forName(className, false, MixinLoadProbe.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to load Mixin target " + className, exception);
        }
    }
}
