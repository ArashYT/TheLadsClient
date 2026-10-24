package com.thelads.core.v1_8_9.qol.mixin;

import com.thelads.core.v1_8_9.feature.ResolutionCache189;
import com.thelads.core.v1_8_9.qol.Qol189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * GUI Scale: the chat line under the pointer. GuiNewChat.getChatComponent divides window pixels by ScaledResolution's whole scale;
 * the pointer is handed over so that division gives the exact GUI position (GuiNewChatMixin does the same for the y it lifts).
 */
@Mixin(GuiNewChat.class)
public abstract class GuiScaleQolMixin189 {
    @ModifyVariable(method = "getChatComponent", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ladsExactChatX(int mouseX) {
        return Qol189.pixels(mouseX, ResolutionCache189.get(Minecraft.getMinecraft()));
    }
}
