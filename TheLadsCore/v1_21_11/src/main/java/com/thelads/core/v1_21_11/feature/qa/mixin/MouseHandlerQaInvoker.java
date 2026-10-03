package com.thelads.core.v1_21_11.feature.qa.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The GLFW scroll callback, so QA scrolls take a physical wheel's path (ZoomCapture: the hotbar must keep its slot). */
@Mixin(MouseHandler.class)
public interface MouseHandlerQaInvoker {
    @Invoker("onScroll") void ladsQaScroll(long window, double horizontal, double vertical);
}
