package com.moepus.serverwarashi.client.world;

import com.moepus.serverwarashi.client.config.ClientConfig;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldOverlayMathTest {
    @Test
    void mapsIntegerStrengthsToColorAnchors() {
        assertEquals(0x40DC78, WorldOverlayMath.colorRgb(1.0D));
        assertEquals(0xFFD640, WorldOverlayMath.colorRgb(2.0D));
        assertEquals(0xFF9120, WorldOverlayMath.colorRgb(3.0D));
        assertEquals(0xF44336, WorldOverlayMath.colorRgb(4.0D));
        assertEquals(0xBE2DDC, WorldOverlayMath.colorRgb(5.0D));
        assertEquals(0xA0D95C, WorldOverlayMath.colorRgb(1.5D));
    }

    @Test
    void appliesConfiguredVisibilityThreshold() {
        assertTrue(WorldOverlayMath.shouldRender(0.1D, ClientConfig.WorldOverlayFilter.ALL));
        assertFalse(WorldOverlayMath.shouldRender(1.999D,
                ClientConfig.WorldOverlayFilter.TWO_OR_MORE));
        assertTrue(WorldOverlayMath.shouldRender(2.0D,
                ClientConfig.WorldOverlayFilter.TWO_OR_MORE));
    }

    @Test
    void packsLabelValueIntoSignedUvWords() {
        for (int value : new int[]{0, 1, 32_767, 32_768,
                WorldOverlayMath.MAX_DISPLAY_MICROS, WorldOverlayMath.OVERFLOW_MICROS}) {
            WorldOverlayMath.PackedLabel packed = WorldOverlayMath.packLabel(value);
            assertTrue(packed.markedHighWord() < 0);
            assertEquals(value, WorldOverlayMath.unpackLabel(
                    packed.lowWord(), packed.markedHighWord()));
        }
    }

    @Test
    void roundsAndCapsMicroseconds() {
        assertEquals(1_235, WorldOverlayMath.roundedMicros(
                new PerfReportData.Metric(1.2346D, 1.2346D, 1L)));
        assertEquals(WorldOverlayMath.OVERFLOW_MICROS, WorldOverlayMath.roundedMicros(
                new PerfReportData.Metric(1_000_000.0D, 1_000_000.0D, 1L)));
        assertEquals(0, WorldOverlayMath.roundedMicros(
                new PerfReportData.Metric(Double.NaN, 0.0D, 1L)));
    }

    @Test
    void packsFlameStrengthForVertexAttributes() {
        assertEquals(0, WorldOverlayMath.packFlameStrength(0.0D));
        assertEquals(2.5D, WorldOverlayMath.unpackFlameStrength(
                WorldOverlayMath.packFlameStrength(2.5D)), 1.0D / 1_024.0D);
        assertEquals(32_767, WorldOverlayMath.packFlameStrength(Double.MAX_VALUE));
        assertEquals(255, WorldOverlayMath.packLabelStrength(Double.MAX_VALUE));
    }

    @Test
    void exposesShaderContractConstants() {
        assertEquals(80.0D, WorldOverlayMath.OBJECT_FADE_START);
        assertEquals(96.0D, WorldOverlayMath.OBJECT_FADE_END);
        assertEquals(64.0D, WorldOverlayMath.BEAM_FADE_START);
        assertEquals(128.0D, WorldOverlayMath.BEAM_FADE_END);
        assertEquals(8.0D, WorldOverlayMath.LABEL_FIXED_DISTANCE);
        assertEquals(12.0D, WorldOverlayMath.LABEL_TRANSITION_END);
        assertEquals(20.0D, WorldOverlayMath.LABEL_FADE_START);
        assertEquals(40.0D, WorldOverlayMath.LABEL_FADE_END);
        assertEquals(0.18D, WorldOverlayMath.MAX_FLAME_VIEWPORT_FRACTION);
        assertEquals(32_768, WorldOverlayMath.LABEL_WORD_BASE);
        assertEquals(1_024, WorldOverlayMath.FLAME_STRENGTH_SCALE);
    }

    @Test
    void atlasContainsEveryShaderGlyphCell() {
        assertEquals(17 * 16, WorldGlyphAtlas.atlasWidth());
        assertEquals(12.0F, WorldPerfObjectRenderer.LABEL_PIXELS);
        assertEquals(64, WorldFlameNoiseTexture.SIZE);
    }
}
