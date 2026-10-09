package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.feature.NativeDynamicFps1201;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftFramerateMixin {
    @Inject(method = "getFramerateLimit()I", at = @At("RETURN"), cancellable = true, require = 0)
    private void ladsDynamicFramerate(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(NativeDynamicFps1201.limit(cir.getReturnValueI()));
    }
}
