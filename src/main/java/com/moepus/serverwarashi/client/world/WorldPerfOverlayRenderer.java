package com.moepus.serverwarashi.client.world;

import com.moepus.serverwarashi.client.config.ClientConfig;
import com.moepus.serverwarashi.client.state.ClientPerfMapState;
import com.moepus.serverwarashi.client.state.ClientPerfWorldState;
import com.moepus.serverwarashi.client.state.ClientWeakChunkState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class WorldPerfOverlayRenderer {
    private static final WorldPerfObjectRenderer OBJECTS = new WorldPerfObjectRenderer();
    private static final WorldPerfBeamRenderer BEAMS = new WorldPerfBeamRenderer();

    private WorldPerfOverlayRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (!ClientConfig.isLoaded()) {
            close();
            return;
        }
        if (!ClientConfig.worldOverlayEnabled()) {
            close();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            close();
            return;
        }
        ClientPerfMapState.Snapshot map = ClientPerfMapState.snapshot();
        ClientPerfWorldState.Snapshot world = ClientPerfWorldState.snapshot();
        boolean hasMap = map.report() != null && map.report().tickCount() > 0L;
        if (!hasMap && world.tickCount() == 0L) {
            close();
            return;
        }
        if (hasMap) {
            BEAMS.render(event, level, map, ClientWeakChunkState.snapshot());
        } else {
            BEAMS.close();
        }
        OBJECTS.render(event, level, world);
    }

    public static void close() {
        OBJECTS.close();
        BEAMS.close();
    }
}
