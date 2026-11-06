package com.thelads.core.v1_20_1.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerQaInvoker {
    @Invoker("charTyped")
    void ladsQaCharTyped(long window, int codePoint, int modifiers);
}
