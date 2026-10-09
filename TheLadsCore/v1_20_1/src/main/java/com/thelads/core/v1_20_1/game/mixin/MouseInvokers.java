package com.thelads.core.v1_20_1.game.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** QA only: the GLFW mouse button and scroll callbacks, so a probe clicks through the handlers a real click reaches. */
@Mixin(MouseHandler.class)
public interface MouseInvokers {
    @Invoker("onPress")
    void ladsPress(long window, int button, int action, int modifiers);

    @Invoker("onScroll")
    void ladsScroll(long window, double horizontal, double vertical);

    /** QA only: a window that is never focused cannot grab the cursor, and the game only mines while the mouse is grabbed. */
    @org.spongepowered.asm.mixin.gen.Accessor("mouseGrabbed")
    void ladsGrabbed(boolean grabbed);
}
