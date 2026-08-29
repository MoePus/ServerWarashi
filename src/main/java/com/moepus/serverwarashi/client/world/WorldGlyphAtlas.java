package com.moepus.serverwarashi.client.world;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;

final class WorldGlyphAtlas {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String GLYPHS = "0123456789<+ us/t";
    static final int GLYPH_COUNT = GLYPHS.length();
    private static final int CELL_SIZE = 16;
    private static final int SOURCE_GRID_SIZE = 16;
    private static final int SOURCE_ADVANCE = 6;
    private static final int GLYPH_X = 3;
    private static final int GLYPH_Y = 1;
    private static final int PIXELS_PER_BIT = 2;
    private static final ResourceLocation DEFAULT_ASCII = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "textures/font/ascii.png");
    private static final int[][] ROWS = {
            {31, 17, 19, 21, 25, 17, 31}, // 0
            {4, 12, 4, 4, 4, 4, 14},      // 1
            {30, 1, 1, 14, 16, 16, 31},   // 2
            {30, 1, 1, 14, 1, 1, 30},     // 3
            {2, 6, 10, 18, 31, 2, 2},     // 4
            {31, 16, 16, 30, 1, 1, 30},   // 5
            {14, 16, 16, 30, 17, 17, 14}, // 6
            {31, 1, 2, 4, 8, 8, 8},       // 7
            {14, 17, 17, 14, 17, 17, 14}, // 8
            {14, 17, 17, 15, 1, 1, 14},   // 9
            {2, 4, 8, 16, 8, 4, 2},       // <
            {0, 4, 4, 31, 4, 4, 0},       // +
            {0, 0, 0, 0, 0, 0, 0},        // space
            {0, 0, 17, 17, 17, 19, 13},   // u
            {0, 0, 15, 16, 14, 1, 30},    // s
            {1, 2, 2, 4, 8, 8, 16},       // /
            {8, 8, 30, 8, 8, 9, 6}        // t
    };
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            ServerWarashi.MODID, "dynamic/world_perf_glyphs");
    private static DynamicTexture texture;

    private WorldGlyphAtlas() {
    }

    static int textureId() {
        if (texture == null) {
            texture = new DynamicTexture(createImage());
            texture.setFilter(true, false);
            Minecraft.getInstance().getTextureManager().register(ID, texture);
        }
        return texture.getId();
    }

    static int atlasWidth() {
        return GLYPH_COUNT * CELL_SIZE;
    }

    private static NativeImage createImage() {
        try {
            Resource resource = Minecraft.getInstance().getResourceManager()
                    .getResourceOrThrow(DEFAULT_ASCII);
            try (var stream = resource.open(); NativeImage source = NativeImage.read(stream)) {
                validateSource(source);
                return createImage((glyph, x, y) -> sourceInside(source, glyph, x, y));
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Unable to load the default Minecraft font; using fallback label glyphs",
                    exception);
            return createImage(WorldGlyphAtlas::fallbackInside);
        }
    }

    private static NativeImage createImage(GlyphMask mask) {
        NativeImage image = new NativeImage(atlasWidth(), CELL_SIZE, true);
        for (int glyph = 0; glyph < GLYPH_COUNT; glyph++) {
            for (int y = 0; y < CELL_SIZE; y++) {
                for (int x = 0; x < CELL_SIZE; x++) {
                    int value = distanceValue(mask, glyph, x, y);
                    image.setPixelRGBA(glyph * CELL_SIZE + x, y,
                            0xFF000000 | value << 16 | value << 8 | value);
                }
            }
        }
        return image;
    }

    private static int distanceValue(GlyphMask mask, int glyph, int x, int y) {
        boolean inside = mask.inside(glyph, x, y);
        double nearest = CELL_SIZE;
        for (int otherY = 0; otherY < CELL_SIZE; otherY++) {
            for (int otherX = 0; otherX < CELL_SIZE; otherX++) {
                if (mask.inside(glyph, otherX, otherY) != inside) {
                    nearest = Math.min(nearest, Math.hypot(x - otherX, y - otherY));
                }
            }
        }
        double signed = 0.5D + (inside ? nearest : -nearest) / 8.0D;
        return (int) Math.round(Math.max(0.0D, Math.min(1.0D, signed)) * 255.0D);
    }

    private static void validateSource(NativeImage source) throws IOException {
        if (source.getWidth() % SOURCE_GRID_SIZE != 0
                || source.getHeight() % SOURCE_GRID_SIZE != 0) {
            throw new IOException("Unexpected default font texture size "
                    + source.getWidth() + "x" + source.getHeight());
        }
    }

    private static boolean sourceInside(NativeImage source, int glyph, int x, int y) {
        int codePoint = GLYPHS.charAt(glyph);
        int sourceCellWidth = source.getWidth() / SOURCE_GRID_SIZE;
        int sourceHeight = source.getHeight() / SOURCE_GRID_SIZE;
        int sampledWidth = Math.min(SOURCE_ADVANCE, sourceCellWidth);
        int sourceX = codePoint % SOURCE_GRID_SIZE * sourceCellWidth
                + x * sampledWidth / CELL_SIZE;
        int sourceY = codePoint / SOURCE_GRID_SIZE * sourceHeight
                + y * sourceHeight / CELL_SIZE;
        return Byte.toUnsignedInt(source.getLuminanceOrAlpha(sourceX, sourceY)) >= 128;
    }

    private static boolean fallbackInside(int glyph, int x, int y) {
        int bitX = Math.floorDiv(x - GLYPH_X, PIXELS_PER_BIT);
        int bitY = Math.floorDiv(y - GLYPH_Y, PIXELS_PER_BIT);
        if (bitX < 0 || bitX >= 5 || bitY < 0 || bitY >= 7) {
            return false;
        }
        return (ROWS[glyph][bitY] & 1 << (4 - bitX)) != 0;
    }

    @FunctionalInterface
    private interface GlyphMask {
        boolean inside(int glyph, int x, int y);
    }
}
