package com.moepus.serverwarashi.client.heatmap;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

@EventBusSubscriber(value = Dist.CLIENT, modid = ServerWarashi.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ChunkHeatmapShaders {
    private static final ResourceLocation SHADER_ID = ResourceLocation.fromNamespaceAndPath(
            ServerWarashi.MODID, "chunk_heatmap");
    private static final ResourceLocation ICON_SHADER_ID = ResourceLocation.fromNamespaceAndPath(
            ServerWarashi.MODID, "chunk_loader_icon");
    private static ShaderInstance shader;
    private static ShaderInstance iconShader;

    private ChunkHeatmapShaders() {
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER_ID,
                DefaultVertexFormat.POSITION_TEX_COLOR), loaded -> shader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ICON_SHADER_ID,
                DefaultVertexFormat.POSITION_TEX_COLOR), loaded -> iconShader = loaded);
    }

    static ShaderInstance shader() {
        return shader;
    }

    static ShaderInstance iconShader() {
        return iconShader;
    }
}
