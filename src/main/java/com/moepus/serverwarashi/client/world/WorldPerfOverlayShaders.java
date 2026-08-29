package com.moepus.serverwarashi.client.world;

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
public final class WorldPerfOverlayShaders {
    private static final ResourceLocation OBJECT_SHADER = ResourceLocation.fromNamespaceAndPath(
            ServerWarashi.MODID, "world_perf_object");
    private static final ResourceLocation BEAM_SHADER = ResourceLocation.fromNamespaceAndPath(
            ServerWarashi.MODID, "world_perf_beam");
    private static ShaderInstance objectShader;
    private static ShaderInstance beamShader;

    private WorldPerfOverlayShaders() {
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), OBJECT_SHADER,
                DefaultVertexFormat.POSITION), loaded -> objectShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), BEAM_SHADER,
                DefaultVertexFormat.POSITION_COLOR), loaded -> beamShader = loaded);
    }

    static ShaderInstance objectShader() {
        return objectShader;
    }

    static ShaderInstance beamShader() {
        return beamShader;
    }
}
