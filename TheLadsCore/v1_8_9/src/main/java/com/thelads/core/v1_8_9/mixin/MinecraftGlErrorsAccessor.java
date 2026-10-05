package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla asks the driver for GL errors after most render steps (glGetError); the flag is switched off once the game has started. */
@Mixin(Minecraft.class)
public interface MinecraftGlErrorsAccessor {
    @Accessor("enableGLErrorChecking") boolean ladsGlErrors();
    @Accessor("enableGLErrorChecking") void ladsSetGlErrors(boolean on);
}
