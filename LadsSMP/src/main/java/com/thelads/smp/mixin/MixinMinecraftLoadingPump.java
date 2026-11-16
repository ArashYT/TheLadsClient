package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame bookkeeping for {@link LoadingPump}. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftLoadingPump {
    @Inject(method = "renderFrame", at = @At("HEAD"), require = 0)
    private void ladssmp$frameStart(boolean advanceGameTime, CallbackInfo ci) {
        LoadingPump.frameStart();
    }

    @Inject(method = "renderFrame", at = @At("RETURN"), require = 0)
    private void ladssmp$frameEnd(boolean advanceGameTime, CallbackInfo ci) {
        LoadingPump.frameEnd();
    }
}
