package com.moepus.serverwarashi.common.mixin.compat.xaero;

import com.moepus.serverwarashi.api.client.map.MapOverlayApi;
import com.moepus.serverwarashi.api.client.map.MapOverlayControls;
import com.moepus.serverwarashi.api.client.map.MapRenderContext;
import com.moepus.serverwarashi.client.compat.xaero.XaeroContextMenu;
import com.moepus.serverwarashi.client.compat.xaero.XaeroHeatmapOverlay;
import com.moepus.serverwarashi.client.compat.xaero.XaeroMapIntegration;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

@Mixin(GuiMap.class)
public abstract class XaeroGuiMapMixin {
    @Unique
    private boolean serverwarashi$integrationFailed;

    @Unique
    private MapOverlayControls serverwarashi$perfControls;

    @Inject(method = "init()V", at = @At("RETURN"), require = 0)
    private void serverwarashi$addPerfControls(CallbackInfo callback) {
        if (serverwarashi$isDisabled()) {
            return;
        }
        try {
            GuiMap map = (GuiMap) (Object) this;
            serverwarashi$perfControls = MapOverlayApi.createControls(
                    XaeroMapIntegration.ID, map::addButton).orElse(null);
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
        if (serverwarashi$isDisabled()) {
            return;
        }
        try {
            if (serverwarashi$perfControls != null) {
                serverwarashi$perfControls.update();
            }
            var input = new XaeroHeatmapOverlay.RenderInput(
                    new MapRenderContext.ScreenPoint(mouseX, mouseY),
                    serverwarashi$perfControls);
            XaeroHeatmapOverlay.render(graphics, (GuiMap) (Object) this, input);
        } catch (Throwable throwable) {
            serverwarashi$disable("render", throwable);
        }
    }

    @Inject(method = "getRightClickOptions()Ljava/util/ArrayList;", at = @At("RETURN"), require = 0)
    private void serverwarashi$addWeakClearOption(
            CallbackInfoReturnable<ArrayList<RightClickOption>> callback) {
        if (serverwarashi$isDisabled()) {
            return;
        }
        try {
            XaeroContextMenu.addOptions((GuiMap) (Object) this, callback.getReturnValue());
        } catch (Throwable throwable) {
            serverwarashi$disable("right-click menu", throwable);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("RETURN"), require = 0)
    private void serverwarashi$renderPerfStatus(GuiGraphics graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo callback) {
        if (serverwarashi$isDisabled()) {
            return;
        }
        try {
            if (serverwarashi$perfControls != null) {
                serverwarashi$perfControls.renderStatus(graphics, mouseX, mouseY);
            }
        } catch (Throwable throwable) {
            serverwarashi$disable("status render", throwable);
        }
    }

    @Unique
    private void serverwarashi$disable(String phase, Throwable throwable) {
        serverwarashi$integrationFailed = true;
        if (serverwarashi$perfControls != null) {
            serverwarashi$perfControls.hide();
        }
        XaeroMapIntegration.disable(phase, throwable);
    }

    @Unique
    private boolean serverwarashi$isDisabled() {
        boolean disabled = serverwarashi$integrationFailed
                || !MapOverlayApi.isEnabled(XaeroMapIntegration.ID);
        if (disabled && serverwarashi$perfControls != null) {
            serverwarashi$perfControls.hide();
        }
        return disabled;
    }
}
