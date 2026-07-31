package com.moepus.serverwarashi.common.modules.loaderfreeze;

import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = ServerWarashi.MODID)
public final class LoaderPlacementEvents {
    private LoaderPlacementEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.isFakePlayer()
                || !event.getPlacedBlock().is(BlockLoaderTags.ATTRIBUTION_CANDIDATES)) {
            return;
        }
        LoaderPlacementAttribution.remember(level, new LoaderPlacementAttribution.Placement(
                event.getPos(),
                BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()),
                player.getUUID()));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LoaderPlacementAttribution.clear();
    }
}
