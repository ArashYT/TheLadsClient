package com.thelads.core.v1_20_1.qol.mixin;

import com.thelads.core.perf.SmoothSingleplayer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth Singleplayer: stamps the end of every client tick and tells it whether a private integrated server is hosted. */
@Mixin(Minecraft.class)
abstract class SmoothClientMixin1201 {
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void lads$clientTickEnd(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
        SmoothSingleplayer.clientTickEnd(server != null && !server.isPublished() && mc.level != null);
    }
}
