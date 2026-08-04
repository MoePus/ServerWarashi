package com.moepus.serverwarashi.common.modules.entityload;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EntityLoadGuardMessageTest {
    @Test
    void reportsLiteralMessageWithLocalizedTypeName() {
        Component typeName = Component.translatable("entity.minecraft.cow");

        Component message = EntityLoadGuard.deletionMessage(
                ResourceLocation.parse("minecraft:the_nether"),
                new ChunkPos(12, -3), typeName, 42);
        LiteralContents contents = (LiteralContents) message.getContents();

        assertEquals("Warashi deleted 42 ", contents.text());
        assertSame(typeName, message.getSiblings().get(0));
        assertEquals(" at minecraft:the_nether (200, -40)",
                ((LiteralContents) message.getSiblings().get(1).getContents()).text());
        assertEquals("entity.minecraft.cow",
                ((TranslatableContents) typeName.getContents()).getKey());
    }
}
