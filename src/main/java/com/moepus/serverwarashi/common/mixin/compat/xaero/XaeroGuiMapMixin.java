package com.moepus.serverwarashi.common.mixin.compat.xaero;

import com.mojang.logging.LogUtils;
import com.moepus.serverwarashi.client.compat.xaero.XaeroHeatmapOverlay;
import com.moepus.serverwarashi.client.compat.xaero.XaeroPerfControls;
import com.moepus.serverwarashi.client.compat.xaero.XaeroWeakChunkClearMenu;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

@Mixin(GuiMap.class)
public abstract class XaeroGuiMapMixin {
    @Unique
    private static final Logger SERVERWARASHI$LOGGER = LogUtils.getLogger();

    @Unique
    private boolean serverwarashi$heatmapFailed;

    @Unique
    private XaeroPerfControls.Controls serverwarashi$perfControls;

    @Inject(method = "init()V", at = @At("RETURN"), require = 0)
    private void serverwarashi$addPerfControls(CallbackInfo callback) {
        if (serverwarashi$heatmapFailed) {
            return;
        }
        try {
            serverwarashi$perfControls = XaeroPerfControls.install((GuiMap) (Object) this);
        } catch (Throwable throwable) {
            serverwarashi$disable("control initialization", throwable);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"),
            require = 0)
    private void serverwarashi$renderHeatmap(GuiGraphics graphics, int mouseX, int mouseY,
                                             float partialTick, CallbackInfo callback) {
        if (serverwarashi$heatmapFailed) {
            return;
        }
        try {
            XaeroPerfControls.update(serverwarashi$perfControls);
            XaeroHeatmapOverlay.render(graphics, (GuiMap) (Object) this, mouseX, mouseY);
        } catch (Throwable throwable) {
            serverwarashi$disable("render", throwable);
        }
    }

    @Inject(method = "getRightClickOptions()Ljava/util/ArrayList;", at = @At("RETURN"), require = 0)
    private void serverwarashi$addWeakClearOption(
            CallbackInfoReturnable<ArrayList<RightClickOption>> callback) {
        if (serverwarashi$heatmapFailed) {
            return;
        }
        try {
            XaeroWeakChunkClearMenu.addOption((GuiMap) (Object) this, callback.getReturnValue());
        } catch (Throwable throwable) {
            serverwarashi$disable("right-click menu", throwable);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("RETURN"), require = 0)
    private void serverwarashi$renderPerfStatus(GuiGraphics graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo callback) {
        if (serverwarashi$heatmapFailed) {
            return;
        }
        try {
            XaeroPerfControls.renderStatus(graphics, mouseX, mouseY);
        } catch (Throwable throwable) {
            serverwarashi$disable("status render", throwable);
        }
    }

    @Unique
    private void serverwarashi$disable(String phase, Throwable throwable) {
        SERVERWARASHI$LOGGER.error("Disabling Xaero chunk heatmap after a {} failure", phase, throwable);
        serverwarashi$heatmapFailed = true;
        XaeroHeatmapOverlay.disable();
    }
}
