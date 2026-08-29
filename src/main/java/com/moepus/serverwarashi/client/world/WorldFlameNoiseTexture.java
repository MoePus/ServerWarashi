package com.moepus.serverwarashi.client.world;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

final class WorldFlameNoiseTexture {
    static final int SIZE = 64;
    private static final int OCTAVES = 3;
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            ServerWarashi.MODID, "dynamic/world_perf_flame_noise");
    private static DynamicTexture texture;

    private WorldFlameNoiseTexture() {
    }

    static int textureId() {
        if (texture == null) {
            texture = new DynamicTexture(createImage());
            texture.setFilter(true, false);
            Minecraft.getInstance().getTextureManager().register(ID, texture);
            RenderSystem.bindTexture(texture.getId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        }
        return texture.getId();
    }

    private static NativeImage createImage() {
        NativeImage image = new NativeImage(SIZE, SIZE, true);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int value = (int) Math.round(fractal(
                        (x + 0.5D) / SIZE, (y + 0.5D) / SIZE) * 255.0D);
                image.setPixelRGBA(x, y, 0xFF000000 | value << 16 | value << 8 | value);
            }
        }
        return image;
    }

    private static double fractal(double x, double y) {
        Point point = new Point(x, y);
        double value = 0.0D;
        double weight = 0.0D;
        double amplitude = 1.0D;
        int cells = 4;
        for (int octave = 0; octave < OCTAVES; octave++) {
            value += valueNoise(point, new Band(cells, octave)) * amplitude;
            weight += amplitude;
            amplitude *= 0.5D;
            cells *= 2;
        }
        return value / weight;
    }

    private static double valueNoise(Point point, Band band) {
        double gridX = point.x() * band.cells();
        double gridY = point.y() * band.cells();
        int cellX = (int) Math.floor(gridX);
        int cellY = (int) Math.floor(gridY);
        double localX = smooth(gridX - cellX);
        double localY = smooth(gridY - cellY);
        double low = lerp(lattice(cellX, cellY, band),
                lattice(cellX + 1, cellY, band), localX);
        double high = lerp(lattice(cellX, cellY + 1, band),
                lattice(cellX + 1, cellY + 1, band), localX);
        return lerp(low, high, localY);
    }

    private static double lattice(int x, int y, Band band) {
        int hash = Math.floorMod(x, band.cells()) * 0x1F123BB5
                ^ Math.floorMod(y, band.cells()) * 0x5F356495
                ^ band.seed() * 0x2C9277B5;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        return (hash >>> 8 & 0xFF_FFFF) / (double) 0xFF_FFFF;
    }

    private static double smooth(double value) {
        return value * value * (3.0D - 2.0D * value);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private record Point(double x, double y) {
    }

    private record Band(int cells, int seed) {
    }
}
