package com.thelads.smp.qa.mixin;

import com.thelads.smp.qa.FrameWatch;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftFrame {
    @Inject(method = "renderFrame", at = @At("RETURN"))
    private void ladsqa$frame(boolean advance, CallbackInfo ci) {
        if (FrameWatch.ON) FrameWatch.frame();
    }
}

