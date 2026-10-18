package com.thelads.core.v1_8_9.mixin.ionium;

import com.thelads.core.v1_8_9.render.LadsClouds189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ionium's fancy clouds (RenderGlobal.renderCloudsFancy goes there): Lads' fast clouds draw them instead (LadsClouds189). */
@Pseudo
@Mixin(targets = "org.taumc.celeritas.impl.render.clouds.FancyCloudRenderer", remap = false)
public abstract class IoniumCloudsMixin189 {
    @Inject(method = "render(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/WorldClient;IFI)V", at = @At("HEAD"),
        cancellable = true, require = 0, remap = false)
    private static void lads$fastClouds(Minecraft mc, WorldClient world, int ticks, float partialTicks, int anaglyphPass, CallbackInfo ci) {
        if (LadsClouds189.render(mc, world, ticks, partialTicks, anaglyphPass)) ci.cancel();
    }
}
