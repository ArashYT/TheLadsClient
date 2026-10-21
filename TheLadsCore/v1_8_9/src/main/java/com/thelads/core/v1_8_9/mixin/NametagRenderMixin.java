package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.NametagDepth;
import com.thelads.core.v1_8_9.feature.FontShadow189;
import com.thelads.core.v1_8_9.feature.Nametags189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nametags background and Text Shadow on the standing name tag (Render.renderLivingLabel). There is no Forge event inside
 * the label, and cancelling RenderLivingEvent.Specials would also drop Essential's icon, which Essential injects here.
 * OptiFine patches this class: require = 0 leaves vanilla's tag if an injection point ever moves.
 */
@Mixin(Render.class)
public abstract class NametagRenderMixin {
    @Shadow public abstract FontRenderer getFontRendererFromRenderManager();

    /**
     * Fixes MC-5404: In vanilla 1.8.9, Render.renderLivingLabel rotates playerViewY by +180 in third-person
     * front view, but leaves playerViewX untouched. Because the Y rotation inverts the local X axis,
     * nametags tilt away from the camera instead of facing it. Inverting pitch when thirdPersonView == 2
     * keeps nametags facing the camera squarely.
     */
    @ModifyArg(method = "renderLivingLabel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V", ordinal = 1), index = 0, require = 0)
    private float ladsFrontViewPitch(float angle) {
        Minecraft mc = Minecraft.getMinecraft();
        return (mc.gameSettings.thirdPersonView == 2 ? -1 : 1) * angle;
    }

    @ModifyArg(method = "renderLivingLabel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/WorldRenderer;color(FFFF)Lnet/minecraft/client/renderer/WorldRenderer;"), index = 3, require = 0)
    private float ladsBackground(float alpha) {
        return Nametags189.background() ? alpha : 0;
    }

    /**
     * A shadow-only draw just before vanilla's normal pass, which draws the opaque text on top; the see-through pass stays flat.
     * The shadow lies in the text's plane, so it writes no depth: with depthMask(false), the shadow never occludes the foreground
     * text, eliminating z-fighting black lines while skipping the redundant foreground text pass of drawString(..., true).
     */
    @Inject(method = "renderLivingLabel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 1), require = 0)
    private void ladsShadow(Entity entity, String str, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        if (!Nametags189.shadow()) return;
        FontRenderer font = getFontRendererFromRenderManager();
        if (!NametagDepth.qaVanilla) GlStateManager.depthMask(false);
        int xPos = -font.getStringWidth(str) / 2;
        int yPos = "deadmau5".equals(str) ? -10 : 0;
        if (font instanceof FontShadow189) {
            ((FontShadow189) font).ladsDrawShadow(str, xPos, yPos, -1);
        } else {
            font.drawString(str, xPos, yPos, -1, true);
        }
        GlStateManager.depthMask(true); // vanilla's normal pass writes depth
    }
}
