package com.thelads.core.v26_2.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.SoundVolumes;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sound Volumes: a sound's own volume times its group's slider, where the engine works out the volume it plays at (then caps it). */
@Mixin(SoundEngine.class)
abstract class SoundEngineQolMixin26 {
    @WrapOperation(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"))
    private float lads$soundVolume(SoundInstance sound, Operation<Float> original) {
        return SoundVolumes.volume(original.call(sound), sound.getIdentifier().getPath());
    }
}
