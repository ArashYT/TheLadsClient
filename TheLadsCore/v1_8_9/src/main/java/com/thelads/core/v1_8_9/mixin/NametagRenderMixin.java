package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.NametagDepth;
import com.thelads.core.client.PvpVisuals;
import com.thelads.core.client.ladsapi.LadsBadge;
import com.thelads.core.v1_8_9.feature.FontShadow189;
import com.thelads.core.v1_8_9.feature.Nametags189;
import com.thelads.core.v1_8_9.feature.PvpVisuals189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nametags background and Text Shadow on the standing name tag (Render.renderLivingLabel). There is no Forge event inside
 * the label, and cancelling RenderLivingEvent.Specials would also drop Essential's icon, which Essential injects here.
 * OptiFine patches this class: require = 0 leaves vanilla's tag if an injection point ever moves.
 */
@Mixin(Render.class)
public abstract class NametagRenderMixin {
    private static final String DRAW = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I";
    @Shadow public abstract FontRenderer getFontRendererFromRenderManager();
    @Shadow @Final protected RenderManager renderManager;

    /** Nametags' Scale and Distance Fade apply to players' tags only (PvpVisuals.tagStart). */
    @Inject(method = "renderLivingLabel", at = @At("HEAD"), require = 0)
    private void ladsTagStart(Entity entity, String str, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        PvpVisuals.tagStart(entity instanceof EntityPlayer, entity.getDistanceSqToEntity(renderManager.livingPlayer));
    }

    @Inject(method = "renderLivingLabel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;scale(FFF)V",
        ordinal = 0, shift = At.Shift.AFTER), require = 0)
    private void ladsTagScale(CallbackInfo ci) {
        float scale = PvpVisuals.tagScale;
        if (scale != 1) GlStateManager.scale(scale, scale, scale);
    }

    /** Show Ping and Nametag Health after the name, drawn with the normal pass (hidden behind walls like it). */
    @Inject(method = "renderLivingLabel", at = @At(value = "INVOKE", target = DRAW, ordinal = 1, shift = At.Shift.AFTER), require = 0)
    private void ladsTagExtras(Entity entity, String str, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        int shift = ladsBadgeActive(entity) ? (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP) / 2 : 0; // follow the badge-shifted name
        if (shift != 0) GlStateManager.translate(shift, 0, 0);
        PvpVisuals189.tagExtras(getFontRendererFromRenderManager(), entity, str);
        if (shift != 0) GlStateManager.translate(-shift, 0, 0);
    }

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

    @Unique private static final ResourceLocation LADS_BADGE = new ResourceLocation(LadsBadge.TEXTURE_NAMESPACE, LadsBadge.TEXTURE_PATH);
    @Unique private Entity ladsLabelEntity;

    @Unique
    private static boolean ladsBadgeActive(Entity entity) {
        return entity != null && entity.getUniqueID() != null && LadsBadge.showOnNametag(entity.getUniqueID());
    }

    @Inject(method = "renderLivingLabel", at = @At("HEAD"), require = 0)
    private void ladsCaptureLabelEntity(Entity entity, String str, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        ladsLabelEntity = entity;
    }

    @Inject(method = "renderLivingLabel", at = @At("RETURN"), require = 0)
    private void ladsReleaseLabelEntity(Entity entity, String str, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        ladsLabelEntity = null;
    }

    @Redirect(method = "renderLivingLabel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I", ordinal = 0), require = 0)
    private int ladsBackgroundWidth(FontRenderer font, String str) {
        int w = font.getStringWidth(str);
        if (ladsBadgeActive(ladsLabelEntity)) {
            w += LadsBadge.NAMETAG_SIZE + LadsBadge.GAP;
        }
        return w;
    }

    @ModifyArg(method = "renderLivingLabel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/WorldRenderer;color(FFFF)Lnet/minecraft/client/renderer/WorldRenderer;"), index = 3, require = 0)
    private float ladsBackground(float alpha) {
        return Nametags189.background() ? PvpVisuals.background(alpha) : 0;
    }

    @Unique
    private static void ladsDrawBadgeQuad(int x, int y, float alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(LADS_BADGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        double x0 = x, y0 = y, x1 = x + LadsBadge.NAMETAG_SIZE, y1 = y + LadsBadge.NAMETAG_SIZE;
        wr.pos(x0, y0, 0.0D).tex(0.0D, 0.0D).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        wr.pos(x0, y1, 0.0D).tex(0.0D, 1.0D).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        wr.pos(x1, y1, 0.0D).tex(1.0D, 1.0D).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        wr.pos(x1, y0, 0.0D).tex(1.0D, 0.0D).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        tessellator.draw();
    }

    @Redirect(method = "renderLivingLabel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 0), require = 0)
    private int ladsSeeThroughPass(FontRenderer font, String text, int x, int y, int color) {
        color = PvpVisuals.alpha(color, PvpVisuals.tagAlpha); // Distance Fade, both passes
        if (ladsBadgeActive(ladsLabelEntity)) {
            int shift = (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP) / 2;
            int textX = x + shift;
            int badgeX = textX - (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP);
            float alpha = ((color >> 24) & 0xFF) / 255.0F;
            if (alpha <= 0.0F) alpha = 0.2F;
            ladsDrawBadgeQuad(badgeX, y, alpha);
            return font.drawString(text, textX, y, color);
        }
        return font.drawString(text, x, y, color);
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
        int shift = ladsBadgeActive(entity) ? (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP) / 2 : 0;
        int xPos = -font.getStringWidth(str) / 2 + shift;
        int yPos = "deadmau5".equals(str) ? -10 : 0;
        int color = PvpVisuals.alpha(-1, PvpVisuals.tagAlpha);
        if (font instanceof FontShadow189) {
            ((FontShadow189) font).ladsDrawShadow(str, xPos, yPos, color);
        } else {
            font.drawString(str, xPos, yPos, color, true);
        }
        GlStateManager.depthMask(true); // vanilla's normal pass writes depth
    }

    @Redirect(method = "renderLivingLabel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 1), require = 0)
    private int ladsNormalPass(FontRenderer font, String text, int x, int y, int color) {
        color = PvpVisuals.alpha(color, PvpVisuals.tagAlpha); // Distance Fade, both passes
        if (ladsBadgeActive(ladsLabelEntity)) {
            int shift = (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP) / 2;
            int textX = x + shift;
            int badgeX = textX - (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP);
            float alpha = ((color >> 24) & 0xFF) / 255.0F;
            if (alpha <= 0.0F) alpha = 1.0F;
            ladsDrawBadgeQuad(badgeX, y, alpha);
            return font.drawString(text, textX, y, color);
        }
        return font.drawString(text, x, y, color);
    }
}
