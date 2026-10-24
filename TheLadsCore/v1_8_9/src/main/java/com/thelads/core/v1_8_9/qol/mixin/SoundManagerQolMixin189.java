package com.thelads.core.v1_8_9.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.SoundVolumes;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sound Volumes: a sound's own volume times its group's slider, where the sound manager works out the volume it plays at (then caps it). */
@Mixin(SoundManager.class)
public abstract class SoundManagerQolMixin189 {
    @WrapOperation(method = "getNormalizedVolume", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/ISound;getVolume()F"))
    private float ladsSoundVolume(ISound sound, Operation<Float> original) {
        return SoundVolumes.volume(original.call(sound), sound.getSoundLocation().getResourcePath());
    }
}
