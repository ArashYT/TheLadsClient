package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.v1_8_9.gui.SmoothScroll189;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lists (worlds, servers, packs, controls) on Lads panels instead of the dirt texture, as GuiScreenMixin does behind menus; and
 * their wheel steps glide (SmoothScroll189) instead of jumping half a row.
 */
@Mixin(GuiSlot.class)
public abstract class GuiSlotMixin implements SmoothScroll189.Target {
    @Shadow public int width, top, bottom, right, left;
    @Shadow @Final public int slotHeight;
    @Shadow protected int mouseY;
    @Shadow protected float amountScrolled;
    @Shadow public abstract int func_148135_f();
    @Shadow public abstract boolean isMouseYWithinSlotBounds(int y);
    /** Where the glide goes, and the scroll it last set (anything else moving the list stops the glide). */
    @Unique private float ladsTarget, ladsSet;

    @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void ladsListBackground(Tessellator tessellator, CallbackInfo ci) {
        if ((Object) this instanceof net.minecraft.client.gui.ServerSelectionList) {
            ci.cancel();
            return;
        }
        if ((Object) this instanceof net.minecraft.client.gui.GuiResourcePackList && com.thelads.core.client.pack.PackMenuSettings.isTransparent()) {
            ci.cancel();
            return;
        }
        Gui.drawRect(left, top, right, bottom, LadsPalette.PANEL);
        ci.cancel();
    }

    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsBand(int startY, int endY, int startAlpha, int endAlpha, CallbackInfo ci) {
        if ((Object) this instanceof net.minecraft.client.gui.GuiResourcePackList && com.thelads.core.client.pack.PackMenuSettings.isTransparent()) {
            ci.cancel();
            return;
        }
        Gui.drawRect(left, startY, left + width, endY, (startAlpha << 24) | (LadsPalette.BACKGROUND & 0xFFFFFF));
        Gui.drawRect(left, startY == 0 ? endY - 1 : startY, left + width, startY == 0 ? endY : startY + 1, LadsPalette.BORDER);
        ci.cancel();
    }

    @ModifyConstant(method = "drawScreen", constant = @Constant(intValue = 4), require = 0)
    private int ladsNoShadowsIfTransparent(int shadowHeight) {
        if ((Object) this instanceof net.minecraft.client.gui.GuiResourcePackList && com.thelads.core.client.pack.PackMenuSettings.isTransparent()) {
            return 0;
        }
        return shadowHeight;
    }

    @Inject(method = "drawSelectionBox", at = @At("HEAD"), require = 0)
    private void ladsScissorStart(int p_148120_1_, int p_148120_2_, int mouseX, int mouseY, CallbackInfo ci) {
        if ((Object) this instanceof net.minecraft.client.gui.GuiResourcePackList && com.thelads.core.client.pack.PackMenuSettings.isTransparent()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            double scale = com.thelads.core.v1_8_9.qol.Qol189.scale(new net.minecraft.client.gui.ScaledResolution(mc)); // GUI Scale may be fractional
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL11.glScissor((int) (left * scale), (mc.displayHeight - (int) (bottom * scale)), (int) ((right - left) * scale), (int) ((bottom - top) * scale));
        }
    }

    @Inject(method = "drawSelectionBox", at = @At("RETURN"), require = 0)
    private void ladsScissorEnd(int p_148120_1_, int p_148120_2_, int mouseX, int mouseY, CallbackInfo ci) {
        if ((Object) this instanceof net.minecraft.client.gui.GuiResourcePackList && com.thelads.core.client.pack.PackMenuSettings.isTransparent()) {
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        }
    }

    /** The wheel step handleMouseInput just added (half a row, inside the list) becomes the glide's target instead. */
    @Inject(method = "handleMouseInput", at = @At("RETURN"), require = 1)
    private void ladsWheel(CallbackInfo ci) {
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || !isMouseYWithinSlotBounds(mouseY)) return;
        if (!SmoothScroll189.isEnabled()) return;
        float step = (wheel > 0 ? -1 : 1) * slotHeight / 2;
        amountScrolled -= step;
        if (!SmoothScroll189.gliding(this) || amountScrolled != ladsSet) ladsTarget = amountScrolled;
        ladsTarget = Math.max(0, Math.min(func_148135_f(), ladsTarget + step * (float) com.thelads.core.client.gui.SmoothScroll.FACTOR));
        ladsSet = amountScrolled;
        SmoothScroll189.start(this);
    }

    @Override
    public boolean ladsAdvanceScroll(double seconds) {
        if (amountScrolled != ladsSet) return false; // dragged, scroll buttons, a reset or a shorter list
        ladsTarget = Math.max(0, Math.min(func_148135_f(), ladsTarget));
        amountScrolled = ladsSet = SmoothScroll189.step(amountScrolled, ladsTarget, seconds);
        return amountScrolled != ladsTarget;
    }
}
