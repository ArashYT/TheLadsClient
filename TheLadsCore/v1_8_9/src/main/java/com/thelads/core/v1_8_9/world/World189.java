package com.thelads.core.v1_8_9.world;

import com.thelads.core.client.WavyCape;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.TntTimerModule;
import com.thelads.core.modules.WavyCapesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Wavy Capes and TNT Timer on 1.8.9. The cape layer's own checks run first (player info, invisible, the skin option, a cape texture:
 * OptiFine's cape where it has one, as its getLocationCape answers), then the cape mesh (common WavyCape) is drawn instead of
 * ModelPlayer.renderCape (LayerCapeWavyMixin). The timer is drawn after the TNT and minecart renderers like a name tag
 * (TntTimerMixin, MinecartTntTimerMixin). 1.8.9 has no elytra; armour leaves the cape where vanilla has it.
 */
public final class World189 {
    private World189() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(WavyCapesModule.NAME, TntTimerModule.NAME);
    }

    /** Instead of the cape layer: false leaves it to vanilla. */
    public static boolean cape(AbstractClientPlayer player, float partial, RenderPlayer renderer) {
        WavyCapesModule settings = WavyCapesModule.active(player == Minecraft.getMinecraft().thePlayer);
        if (settings == null || !player.hasPlayerInfo() || player.isInvisible() || !player.isWearing(EnumPlayerModelParts.CAPE)) return false;
        ResourceLocation texture = player.getLocationCape();
        if (texture == null) return false;
        boolean sneaking = player.isSneaking();
        WavyCape cape = WavyCape.of(player);
        cape.update(settings, player.ticksExisted, player.posX - player.prevPosX, player.posY - player.prevPosY, player.posZ - player.prevPosZ,
            player.prevRenderYawOffset, sneaking ? 25 : 0);
        int quads = cape.build(settings, partial);
        GlStateManager.color(1, 1, 1, 1);
        renderer.bindTexture(texture);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 0.125F);
        if (sneaking) GlStateManager.translate(0, 0.125F, 0); // ModelPlayer hangs the cape 2 pixels lower while sneaking
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL); // as ModelBox draws the vanilla cape
        float[] mesh = cape.mesh;
        for (int at = 0, end = quads * 4 * WavyCape.STRIDE; at < end; at += WavyCape.STRIDE)
            buffer.pos(mesh[at], mesh[at + 1], mesh[at + 2]).tex(mesh[at + 3], mesh[at + 4]).normal(mesh[at + 5], mesh[at + 6], mesh[at + 7]).endVertex();
        tessellator.draw();
        GlStateManager.popMatrix();
        return true;
    }

    /** The time left above a primed TNT or lit TNT minecart at render offset (x, y, z), as Render.renderLivingLabel places a name tag. */
    public static void tnt(Entity entity, double x, double y, double z, float ticksLeft, boolean minecart) {
        TntTimerModule module = TntTimerModule.active();
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager manager = mc.getRenderManager();
        if (module == null || minecart && !module.minecarts.get() || mc.gameSettings.hideGUI || manager.livingPlayer == null
            || !module.shows(entity.getDistanceSqToEntity(manager.livingPlayer))) return;
        String text = TntTimerModule.label(ticksLeft);
        FontRenderer font = manager.getFontRenderer();
        float scale = module.scale() * (0.016666668F * 1.6F / 0.025F); // 1.8.9's name tags are a little larger than 26.x's
        boolean through = module.seeThrough();
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + entity.height + 0.5F, (float) z);
        GL11.glNormal3f(0, 1, 0);
        GlStateManager.rotate(-manager.playerViewY, 0, 1, 0);
        GlStateManager.rotate(manager.playerViewX, 1, 0, 0);
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240); // full bright, like a lit name tag
        GlStateManager.depthMask(false);
        if (through) GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        int half = font.getStringWidth(text) / 2;
        if (module.background.get()) {
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer buffer = tessellator.getWorldRenderer();
            GlStateManager.disableTexture2D();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(-half - 1, -1, 0).color(0, 0, 0, 0.25F).endVertex();
            buffer.pos(-half - 1, 8, 0).color(0, 0, 0, 0.25F).endVertex();
            buffer.pos(half + 1, 8, 0).color(0, 0, 0, 0.25F).endVertex();
            buffer.pos(half + 1, -1, 0).color(0, 0, 0, 0.25F).endVertex();
            tessellator.draw();
            GlStateManager.enableTexture2D();
        }
        font.drawString(text, -half, 0, TntTimerModule.color(ticksLeft), module.shadow.get());
        if (through) GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }
}
