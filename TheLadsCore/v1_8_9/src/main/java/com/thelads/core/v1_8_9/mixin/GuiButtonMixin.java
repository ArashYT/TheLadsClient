package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every vanilla and mod GuiButton draws the Lads title-screen surface instead of the vanilla texture (GlobalButtonMixin on the
 * other versions). Buttons that override drawButton (the language globe, Forge's GuiButtonExt) keep their own look.
 */
@Mixin(GuiButton.class)
public abstract class GuiButtonMixin extends Gui {
    @Shadow public int width, height, xPosition, yPosition;
    @Shadow(remap = false) public int packedFGColour; // Forge's own field
    @Shadow public String displayString;
    @Shadow public boolean enabled, visible;
    @Shadow protected boolean hovered;
    @Shadow protected abstract int getHoverState(boolean mouseOver);
    @Shadow protected abstract void mouseDragged(Minecraft mc, int mouseX, int mouseY);

    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsDrawButton(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        ci.cancel();
        if (!visible) return;
        FontRenderer font = mc.fontRendererObj;
        hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
        int state = getHoverState(hovered); // 0 disabled (and sliders' track), 1 normal, 2 hovered
        TitleScreenTheme.renderButtonSurface(new GuiLadsAdapter(font, 0, 0), xPosition, yPosition, width, height, state == 2, false, state != 0, 1);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mouseDragged(mc, mouseX, mouseY); // sliders draw their knob here
        int color = packedFGColour != 0 ? packedFGColour : !enabled ? 10526880 : hovered ? 16777120 : 14737632;
        drawCenteredString(font, displayString, xPosition + width / 2, yPosition + (height - 8) / 2, color);
    }
}
