package com.thelads.core.v26_2.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.SoundFilters;
import com.thelads.core.client.SoundVolumes;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sound Volumes & Sound Filters: calculateVolume and calculatePitch hooks for 26.3. */
@Mixin(SoundEngine.class)
abstract class SoundEngineQolMixin26 {
    @WrapOperation(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"))
    private float lads$soundVolume(SoundInstance sound, Operation<Float> original) {
        String path = sound.getIdentifier().getPath();
        float vol = SoundVolumes.volume(original.call(sound), path);
        return SoundFilters.filterVolume(vol, path);
    }

    @WrapOperation(method = "calculatePitch(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getPitch()F"))
    private float lads$soundPitch(SoundInstance sound, Operation<Float> original) {
        String path = sound.getIdentifier().getPath();
        return SoundFilters.filterPitch(original.call(sound), path);
    }
}
