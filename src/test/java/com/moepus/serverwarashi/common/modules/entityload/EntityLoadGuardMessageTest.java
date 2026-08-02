package com.moepus.serverwarashi.common.modules.entityload;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EntityLoadGuardMessageTest {
    @Test
    void reportsDimensionAndChunkCenterWithLocalizedTypeName() {
        Component typeName = Component.translatable("entity.minecraft.cow");

        Component message = EntityLoadGuard.deletionMessage(
                ResourceLocation.parse("minecraft:the_nether"),
                new ChunkPos(12, -3), typeName, 42);
        TranslatableContents contents = (TranslatableContents) message.getContents();

        assertEquals(EntityLoadGuard.MESSAGE_KEY, contents.getKey());
        assertEquals("minecraft:the_nether (200, -40)", contents.getArgs()[0]);
        assertSame(typeName, contents.getArgs()[1]);
        assertEquals(42, contents.getArgs()[2]);
    }
}
