package com.thelads.core.v1_8_9.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.SoundFilters;
import com.thelads.core.client.SoundVolumes;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sound Volumes & Sound Filters: volume and pitch calculation hooks for 1.8.9. */
@Mixin(SoundManager.class)
public abstract class SoundManagerQolMixin189 {
    @WrapOperation(method = "getNormalizedVolume", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/ISound;getVolume()F"))
    private float ladsSoundVolume(ISound sound, Operation<Float> original) {
        String path = sound.getSoundLocation().getResourcePath();
        float vol = SoundVolumes.volume(original.call(sound), path);
        return SoundFilters.filterVolume(vol, path);
    }

    @WrapOperation(method = "getNormalizedPitch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/ISound;getPitch()F"))
    private float ladsSoundPitch(ISound sound, Operation<Float> original) {
        String path = sound.getSoundLocation().getResourcePath();
        return SoundFilters.filterPitch(original.call(sound), path);
    }
}
