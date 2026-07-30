package com.moepus.serverwarashi.common.mixin.watchdog;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.common.watchdog.ServerWatchdogTicketAppender;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.ServerWatchdog;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.Path;

@Mixin(value = ServerWatchdog.class, remap = false)

public class ServerWatchdogMixin {
    @Shadow
    @Final
    private DedicatedServer server;

    @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/CrashReport;saveToFile(Ljava/nio/file/Path;Lnet/minecraft/ReportType;)Z"))
    private boolean onSaveToFile(CrashReport instance, Path path, ReportType type, Operation<Boolean> original) {
        // 看门狗保存崩溃报告前，注入各维度 ticket 信息到报告分类中。
        ServerWatchdogTicketAppender.appendTicketInfoToReport(instance, server.getAllLevels());
        return original.call(instance, path, type);
    }
}
