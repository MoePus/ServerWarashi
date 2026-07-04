package com.moepus.serverwarashi;

import com.moepus.serverwarashi.client.config.ClientConfig;
import com.moepus.serverwarashi.common.config.Config;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.spongepowered.asm.mixin.Mixins;

/**
 * 模组主入口。
 * 层级：入口层。
 * 上游：NeoForge 加载器。下游：Config / ServerWarashiEvents。
 */
@Mod(Serverwarashi.MODID)
public class Serverwarashi {
    public static final String MODID = "serverwarashi";

    public Serverwarashi(ModContainer modContainer) {
        // 仅当以 neoforge.gameTestServer 启动时，动态加入 GameTest 专用 mixin 配置
        if (Boolean.getBoolean("neoforge.gameTestServer")) {
            Mixins.addConfiguration("serverwarashi-gametest.mixins.json");
        }
        // 注册服务端配置 spec（分桶 / 冻结 / 性能等服务端项）
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        // 注册客户端配置 spec（HUD 输出模式等客户端项）
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}
