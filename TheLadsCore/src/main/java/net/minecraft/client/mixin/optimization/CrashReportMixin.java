package net.minecraft.client.mixin.optimization;

import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrashReport.class)
public class CrashReportMixin {
    @Inject(method = "preload", at = @At("HEAD"), cancellable = true)
    private static void onPreload(CallbackInfo ci) {
        ci.cancel();
    }
}
