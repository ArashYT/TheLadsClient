package com.thelads.core.v26_2.feature.dynamicfps.mixin;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
    @Accessor("soundEngine") SoundEngine lads$backgroundSoundEngine();
}
