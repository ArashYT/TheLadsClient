package com.thelads.core.v1_20_1.input.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = RenderSystem.class, remap = false)
public interface RenderSystemInvoker1201 {
    /** Vanilla's event poll (it flags the poll for the freeze watchdog). */
    @Invoker("pollEvents")
    static void lads$pollEvents() {
        throw new AssertionError();
    }
}
