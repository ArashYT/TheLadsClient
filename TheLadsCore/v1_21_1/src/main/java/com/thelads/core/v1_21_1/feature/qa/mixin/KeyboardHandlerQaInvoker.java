package com.thelads.core.v1_21_1.feature.qa.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The GLFW character callback (keyPress is public on 1.21.1), so QA typing takes a physical key's path; 1.21.x runs remapped, so no reflection. */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerQaInvoker {
    @Invoker("charTyped") void ladsQaCharTyped(long window, int codePoint, int modifiers);
}
