package com.thelads.core.v1_21_1.feature.qa.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The GLFW mouse-button callback, so QA clicks take a physical button's path (CPS from press events). */
@Mixin(MouseHandler.class)
public interface MouseHandlerQaInvoker {
    @Invoker("onPress") void ladsQaPress(long window, int button, int action, int modifiers);
}
