package com.moepus.serverwarashi.client.event;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.client.world.WorldPerfOverlayRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = ServerWarashi.MODID)
public final class WorldPerfOverlayEvents {
    private WorldPerfOverlayEvents() {
    }

    @SubscribeEvent
    public static void renderWorldOverlay(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            WorldPerfOverlayRenderer.render(event);
        }
    }
}
