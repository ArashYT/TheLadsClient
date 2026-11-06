package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.gui.LadsButtonDraw189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every vanilla and mod GuiButton draws the Lads title-screen surface instead of the vanilla texture (GlobalButtonMixin on the
 * other versions). Buttons that override drawButton (the language globe) keep their own look; Forge's GuiButtonExt (and its
 * GuiSlider) is sent here by GuiButtonExtMixin.
 */
@Mixin(GuiButton.class)
public abstract class GuiButtonMixin extends Gui implements LadsButtonDraw189 {
    @Unique private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    @Shadow public int width, height, xPosition, yPosition;
    @Shadow(remap = false) public int packedFGColour; // Forge's own field
    @Shadow public String displayString;
    @Shadow public boolean enabled, visible;
    @Shadow protected boolean hovered;
    @Shadow protected abstract int getHoverState(boolean mouseOver);
    @Shadow protected abstract void mouseDragged(Minecraft mc, int mouseX, int mouseY);
    /** The text the icon below was looked up for (by reference: a changed label, a slider's value, is a new string). */
    @Unique private String ladsIconText;
    @Unique private String ladsTextIcon;

    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsDrawButton(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        ci.cancel();
        ladsDraw(mc, mouseX, mouseY);
    }

    @Override
    public void ladsDraw(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        FontRenderer font = mc.fontRendererObj;
        hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
        int state = getHoverState(hovered); // 0 disabled (and sliders' track), 1 normal, 2 hovered
        // The pause menu's buttons grow a little about their centre while hovered, label included.
        boolean lifting = ButtonLift.enabled(this);
        float lift = 0;
        if (lifting) {
            ButtonLift.update(this, enabled && hovered);
            lift = ButtonLift.eased(this);
            float cx = xPosition + width / 2f, cy = yPosition + height / 2f, scale = 1 + TitleScreenTheme.LIFT * lift;
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy, 0);
            GlStateManager.scale(scale, scale, 1);
            GlStateManager.translate(-cx, -cy, 0);
        }
        TitleScreenTheme.renderButtonSurface(new GuiLadsAdapter(font, 0, 0), xPosition, yPosition, width, height, state == 2, false, state != 0, 1, lift);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(WIDGETS); // some knobs (the sound sliders') rely on the texture vanilla's drawButton bound
        mouseDragged(mc, mouseX, mouseY); // sliders draw their knob here
        int color = packedFGColour != 0 ? packedFGColour : !enabled ? 10526880 : hovered ? 16777120 : 14737632;
        String icon = ButtonLift.icon(this);
        if (icon == null) {
            // The label's own icon (ButtonIcons: the whole text, else the option's caption before the colon), when it fits beside it
            if (displayString != ladsIconText) {
                ladsIconText = displayString;
                ladsTextIcon = com.thelads.core.v1_8_9.gui.ButtonIcons189.captioned(displayString);
            }
            icon = ladsTextIcon;
            if (icon != null && !TitleScreenTheme.iconFits(width, font.getStringWidth(displayString))) icon = null;
        }
        if (icon == null) drawCenteredString(font, displayString, xPosition + width / 2, yPosition + (height - 8) / 2, color);
        else { // an icon before the label, the two centred together
            int textWidth = font.getStringWidth(displayString);
            TitleScreenTheme.renderLabelIcon(new GuiLadsAdapter(font, 0, 0), icon, xPosition, yPosition, width, height, textWidth, 0xFF000000 | color);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            drawString(font, displayString, TitleScreenTheme.iconLabelX(xPosition, width, textWidth), yPosition + (height - 8) / 2, color);
        }
        if (lifting) GlStateManager.popMatrix();
    }
}
