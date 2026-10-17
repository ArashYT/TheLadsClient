package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Dynamic FPS (DynamicFPS189): the sound system's master volume without SoundHandler.setSoundLevel stopping every sound at 0. */
@Mixin(SoundHandler.class)
public interface SoundHandlerAccessor189 {
    @Accessor("sndManager") SoundManager ladsSoundManager();
}
