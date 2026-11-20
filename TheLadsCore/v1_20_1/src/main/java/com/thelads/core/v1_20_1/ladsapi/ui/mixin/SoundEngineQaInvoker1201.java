package com.thelads.core.v1_20_1.ladsapi.ui.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** QA only (Audio1201.qa): the volume the sound engine really plays an instance at. */
@Mixin(SoundEngine.class)
public interface SoundEngineQaInvoker1201 {
    @Invoker("calculateVolume")
    float lads$volume(SoundInstance instance);
}
