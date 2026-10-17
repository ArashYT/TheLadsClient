package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.SoundBreakProbe189;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QA only: the sounds played while SoundBreakProbe189 runs. */
@Mixin(SoundHandler.class)
public abstract class SoundHandlerQaMixin {
    @Inject(method = "playSound", at = @At("HEAD"))
    private void lads$qaSound(ISound sound, CallbackInfo ci) {
        if (SoundBreakProbe189.active) SoundBreakProbe189.played(sound);
    }
}
