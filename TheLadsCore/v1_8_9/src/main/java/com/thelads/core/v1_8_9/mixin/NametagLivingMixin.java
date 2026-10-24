package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ladsapi.LadsBadge;
import com.thelads.core.v1_8_9.feature.Nametags189;
import com.thelads.core.v1_8_9.feature.RenderTweaks189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nametags on living entities (players, armour-stand holograms): renames, your own tag in third person and the sneaking
 * tag's background, which renderName draws itself. OptiFine patches this class: require = 0 leaves vanilla if one moves.
 * The tag's text comes from RenderTweaks189 (the formatted name without vanilla's throwaway hover event), still renamed after.
 */
@Mixin(RendererLivingEntity.class)
public abstract class NametagLivingMixin {
    @Unique private static final ResourceLocation LADS_BADGE = new ResourceLocation(LadsBadge.TEXTURE_NAMESPACE, LadsBadge.TEXTURE_PATH);
    @Unique private EntityLivingBase ladsSneakEntity;

    @Unique
    private static boolean ladsBadgeActive(EntityLivingBase entity) {
        return entity != null && entity.getUniqueID() != null && LadsBadge.showOnNametag(entity.getUniqueID());
    }

    @Inject(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("HEAD"), require = 0)
    private void ladsCaptureSneakEntity(EntityLivingBase entity, double x, double y, double z, CallbackInfo ci) {
        ladsSneakEntity = entity;
    }

    @Inject(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("RETURN"), require = 0)
    private void ladsReleaseSneakEntity(EntityLivingBase entity, double x, double y, double z, CallbackInfo ci) {
        ladsSneakEntity = null;
    }

    @Redirect(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/EntityLivingBase;getDisplayName()Lnet/minecraft/util/IChatComponent;"), require = 0)
    private IChatComponent ladsDisplayName(EntityLivingBase entity) {
        return RenderTweaks189.displayName(entity);
    }

    @Redirect(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/util/IChatComponent;getFormattedText()Ljava/lang/String;"), require = 0)
    private String ladsFormattedName(IChatComponent name) {
        return RenderTweaks189.formattedName(name);
    }

    /** The tag text, before either the sneaking or the standing tag (and Essential's icon) measures it. */
    @ModifyVariable(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("STORE"), ordinal = 0, require = 0)
    private String ladsRename(String name) {
        return Nametags189.rename(name);
    }

    /**
     * Fixes MC-5404 for sneaking nametags drawn directly in RendererLivingEntity.renderName.
     */
    @ModifyArg(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V", ordinal = 1), index = 0, require = 0)
    private float ladsSneakingFrontViewPitch(float angle) {
        Minecraft mc = Minecraft.getMinecraft();
        return (mc.gameSettings.thirdPersonView == 2 ? -1 : 1) * angle;
    }

    @Redirect(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I", ordinal = 0), require = 0)
    private int ladsSneakBackgroundWidth(FontRenderer font, String str) {
        int w = font.getStringWidth(str);
        if (ladsBadgeActive(ladsSneakEntity)) {
            w += LadsBadge.NAMETAG_SIZE + LadsBadge.GAP;
        }
        return w;
    }

    @ModifyArg(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/WorldRenderer;color(FFFF)Lnet/minecraft/client/renderer/WorldRenderer;"), index = 3, require = 0)
    private float ladsSneakingBackground(float alpha) {
        return Nametags189.background() ? alpha : 0;
    }

    /** Nametags' Show with Hidden HUD: tags stay while F1 hides the HUD (vanilla's only F1 check for living entities). */
    @Redirect(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Minecraft;isGuiEnabled()Z"), require = 0)
    private boolean ladsNamesInF1() {
        return Minecraft.isGuiEnabled() || com.thelads.core.client.PvpVisuals.nametagsInF1;
    }

    @Redirect(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 0), require = 0)
    private int ladsSneakDrawString(FontRenderer font, String text, int x, int y, int color) {
        if (ladsBadgeActive(ladsSneakEntity)) {
            int shift = (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP) / 2;
            int textX = x + shift;
            int badgeX = textX - (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP);
            float alpha = ((color >> 24) & 0xFF) / 255.0F;
            if (alpha <= 0.0F) alpha = 0.2F;

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
            double x0 = badgeX, y0 = y, x1 = badgeX + LadsBadge.NAMETAG_SIZE, y1 = y + LadsBadge.NAMETAG_SIZE;
            wr.pos(x0, y0, 0.0D).tex(0.0D, 0.0D).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
            wr.pos(x0, y1, 0.0D).tex(0.0D, 1.0D).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
            wr.pos(x1, y1, 0.0D).tex(1.0D, 1.0D).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
            wr.pos(x1, y0, 0.0D).tex(1.0D, 0.0D).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
            tessellator.draw();

            return font.drawString(text, textX, y, color);
        }
        return font.drawString(text, x, y, color);
    }

    /** As 26.x OwnNametagMixin: only vanilla's own-camera exclusion is lifted; GUI hidden, invisibility and riding still hide it. */
    @Inject(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsOwnName(EntityLivingBase entity, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getMinecraft();
        if (entity == mc.thePlayer && mc.getRenderViewEntity() == entity && mc.gameSettings.thirdPersonView != 0 && Nametags189.ownNametag()
            && !com.thelads.core.v1_8_9.feature.PaperDoll189.drawing())
            cir.setReturnValue(Minecraft.isGuiEnabled() && !entity.isInvisibleToPlayer(mc.thePlayer) && entity.riddenByEntity == null);
    }
}
