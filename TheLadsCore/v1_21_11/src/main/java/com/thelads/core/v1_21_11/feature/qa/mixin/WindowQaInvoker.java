package com.thelads.core.v1_21_11.feature.qa.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** QA: applies a fullscreen toggle now (the frame's updateDisplay does the same through updateFullscreen). */
@Mixin(Window.class)
public interface WindowQaInvoker {
    @Invoker("setMode") void ladsQaSetMode();
}
