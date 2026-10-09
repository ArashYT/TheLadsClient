package com.thelads.core.v1_20_1.game.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** QA only: the GLFW key callback, so a probe presses keys through the same handlers a real key press reaches. */
@Mixin(KeyboardHandler.class)
public interface KeyboardInvokers {
    @Invoker("keyPress")
    void ladsKeyPress(long window, int key, int scancode, int action, int modifiers);
}
