package com.moepus.serverwarashi.client.map;

import com.moepus.serverwarashi.api.client.map.MapMenuItem;
import com.moepus.serverwarashi.api.client.map.MapTarget;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import com.moepus.serverwarashi.common.network.LoaderFreezeActionPayload;
import com.moepus.serverwarashi.common.network.WeakChunkClearPayload;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapWeakChunkClearMenuTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final WeakEntityType ITEM =
            new WeakEntityType(WeakEntityType.Kind.ITEM, "minecraft:cobblestone");
    private static final WeakEntityType ENTITY =
            new WeakEntityType(WeakEntityType.Kind.ENTITY, "minecraft:cow");

    @AfterEach
    void reset() {
        MapContextMenuService.clear();
    }

    @Test
    void requiresSameChunkAndTypeForConfirmationAndSendsOnSecondSelection() {
        var clients = new RecordingClients(true, false);
        MapTarget target = new MapTarget(OVERWORLD, 1, 2);

        MapMenuItem first = onlyItem(target, chunk(ITEM), clients);
        assertEquals("gui.serverwarashi.weak_clear", key(first));
        first.action().run();

        assertEquals("gui.serverwarashi.weak_clear_confirm",
                key(onlyItem(target, chunk(ITEM), clients)));
        assertEquals("gui.serverwarashi.weak_clear",
                key(onlyItem(new MapTarget(OVERWORLD, 2, 2), chunk(ITEM), clients)));
        assertEquals("gui.serverwarashi.weak_clear",
                key(onlyItem(target, chunk(ENTITY), clients)));

        onlyItem(target, chunk(ITEM), clients).action().run();
        assertEquals(List.of(new WeakChunkClearPayload(
                OVERWORLD.toString(), 1, 2, ITEM)), clients.weakRequests);
        assertEquals("gui.serverwarashi.weak_clear", key(onlyItem(target, chunk(ITEM), clients)));
    }

    @Test
    void omitsUnavailableAndNonRedWeakChunkActionsAndClearResetsConfirmation() {
        MapTarget target = new MapTarget(OVERWORLD, 1, 2);
        var unavailable = new RecordingClients(false, false);
        assertTrue(items(target, chunk(ITEM), unavailable).isEmpty());

        var clients = new RecordingClients(true, false);
        WeakChunkSnapshot.ChunkEntry warning = new WeakChunkSnapshot.ChunkEntry(
                1, 2, WeakChunkSnapshot.ENTITY_WARNING_THRESHOLD,
                Map.of(ITEM, WeakChunkSnapshot.ENTITY_WARNING_THRESHOLD));
        assertTrue(items(target, warning, clients).isEmpty());

        onlyItem(target, chunk(ITEM), clients).action().run();
        MapContextMenuService.clear();
        assertEquals("gui.serverwarashi.weak_clear", key(onlyItem(target, chunk(ITEM), clients)));
    }

    private static MapMenuItem onlyItem(MapTarget target,
                                        WeakChunkSnapshot.ChunkEntry chunk,
                                        RecordingClients clients) {
        List<MapMenuItem> items = items(target, chunk, clients);
        assertEquals(1, items.size());
        return items.getFirst();
    }

    private static List<MapMenuItem> items(MapTarget target,
                                           WeakChunkSnapshot.ChunkEntry chunk,
                                           RecordingClients clients) {
        var state = new MapContextMenuService.MenuState(chunk, null, 0L);
        return MapContextMenuService.items(target, state, clients);
    }

    private static WeakChunkSnapshot.ChunkEntry chunk(WeakEntityType type) {
        int count = WeakChunkSnapshot.ENTITY_RED_THRESHOLD;
        return new WeakChunkSnapshot.ChunkEntry(1, 2, count, Map.of(type, count));
    }

    private static String key(MapMenuItem item) {
        return ((TranslatableContents) item.label().getContents()).getKey();
    }

    private static final class RecordingClients implements MapContextMenuService.ClientActions {
        private final boolean weakAvailable;
        private final boolean loaderAvailable;
        private final List<WeakChunkClearPayload> weakRequests = new ArrayList<>();

        private RecordingClients(boolean weakAvailable, boolean loaderAvailable) {
            this.weakAvailable = weakAvailable;
            this.loaderAvailable = loaderAvailable;
        }

        @Override
        public boolean canClearWeakChunk() {
            return weakAvailable;
        }

        @Override
        public void clearWeakChunk(WeakChunkClearPayload payload) {
            weakRequests.add(payload);
        }

        @Override
        public boolean canControlLoader() {
            return loaderAvailable;
        }

        @Override
        public void controlLoader(LoaderFreezeActionPayload payload) {
        }
    }
}
