package com.moepus.serverwarashi.client.compat.xaero;

import com.moepus.serverwarashi.api.client.map.MapMenuItem;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XaeroContextMenuTest {
    @Test
    void escapesLiteralPercentSignsBeforeXaeroFormatsTheLabel() {
        MapMenuItem item = new MapMenuItem(Component.literal("Loader 50%"), true, () -> {
        });

        assertEquals("Loader 50%%", XaeroContextMenu.literalName(item));
    }
}
