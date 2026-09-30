package com.thelads.core.v1_21_11.feature.qa.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The GLFW key and character callbacks, so QA input takes the same path as a physical key (reflection cannot find them: 1.21.x runs remapped). */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerQaInvoker {
    @Invoker("keyPress") void ladsQaKeyPress(long window, int action, KeyEvent event);
    @Invoker("charTyped") void ladsQaCharTyped(long window, CharacterEvent event);
}
