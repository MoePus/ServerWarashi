package com.moepus.serverwarashi;

import com.moepus.serverwarashi.client.config.ClientConfig;
import com.moepus.serverwarashi.common.config.ServerConfig;
import com.moepus.serverwarashi.common.modules.performance.sampling.SamplingPlatform;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * 模组主入口。
 * 层级：入口层。
 * 上游：NeoForge 加载器。下游：ServerConfig / ServerWarashiEvents。
 */
@Mod(ServerWarashi.MODID)
public class ServerWarashi {
    public static final String MODID = "serverwarashi";

    public ServerWarashi(ModContainer modContainer) {
        SamplingPlatform.initialize();
        // 注册服务端配置 spec（分桶 / 冻结 / 性能等服务端项）
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        // 注册客户端配置 spec（HUD 输出模式等客户端项）
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}
