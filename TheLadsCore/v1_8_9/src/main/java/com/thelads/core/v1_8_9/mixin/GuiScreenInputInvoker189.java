package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** QA: a screen's own mouse and key events, without the player's real mouse. */
@Mixin(GuiScreen.class)
public interface GuiScreenInputInvoker189 {
    @Invoker("mouseClicked") void ladsMouseClicked(int x, int y, int button);
    @Invoker("mouseReleased") void ladsMouseReleased(int x, int y, int button);
    @Invoker("keyTyped") void ladsKeyTyped(char typed, int key);
}
