package com.moepus.serverwarashi;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(Serverwarashi.MODID)
public class Serverwarashi {
    public static final String MODID = "serverwarashi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Serverwarashi(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        WarashiCommands.register(event.getDispatcher());
    }
}
