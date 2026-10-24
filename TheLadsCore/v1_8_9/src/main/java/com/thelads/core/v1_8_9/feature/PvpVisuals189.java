package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.BlockOverlayModule;
import com.thelads.core.modules.GlintColorizerModule;
import com.thelads.core.modules.HitboxesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * The PvP visual modules on 1.8.9 (common PvpVisuals): Block Overlay (RenderGlobal.drawSelectionBox), Hit Color
 * (RendererLivingEntity.setBrightness), Glint Colorizer (RenderItem.renderEffect, LayerArmorBase.renderGlint) with Shiny Potions
 * (ItemPotion.hasEffect: 1.8.9 potions glint; off, they stop), Hide Mob Deaths and Hitboxes (RenderManager) and Mount Opacity
 * (RendererLivingEntity.renderModel). Damage Tint is drawn by the common HUD. Render Tweaks, Overlays, Dark Mode and the 1.7
 * extras are the mixin.pvp mixins named after their target; the Nametags extras (ping, health) are drawn from NametagRenderMixin
 * through {@link #tagExtras}. Mixins: theladscore.v1_8_9.pvp.mixins.json; OptiFine HD U M5 patches these classes but keeps every
 * call they hook (checked against its patched bytecode), require = 0 otherwise.
 */
public final class PvpVisuals189 {
    private static final String HEART = "\u2764", MS = "ms";
    /** Mount Opacity: the entity RendererLivingEntity.renderModel draws now is the player's mount; set and cleared around each call. */
    public static boolean mountNow;
    /** Block Overlay changed drawSelectionBox's line width or colour this call; what vanilla set there, to leave the GL state as it does. */
    private static boolean selectionChanged;
    private static float vanillaWidth = 2;
    private static float vanillaRed, vanillaGreen, vanillaBlue, vanillaAlpha = 0.4F;

    private PvpVisuals189() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(PvpVisuals.MODULES);
        ModuleSupport.registerUnavailable(PvpVisuals.TOTEM, "Minecraft 1.8.9 has no Totem of Undying; it was added in Minecraft 1.11.");
        MinecraftForge.EVENT_BUS.register(new Ticks());
    }

    public static final class Ticks {
        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) PvpVisuals.tick();
        }
    }

    /**
     * Block Overlay in place of drawSelectionBox's drawSelectionBoundingBox (blending on, textures and depth writes off, the box a
     * hair larger than the block and camera-relative): the fill, then the outline in its colour.
     */
    public static void selection(AxisAlignedBB box) {
        BlockOverlayModule overlay = PvpVisuals.BLOCK_OVERLAY;
        if (!overlay.on) {
            RenderGlobal.drawSelectionBoundingBox(box);
            return;
        }
        int fill = overlay.fillArgb, outline = overlay.outlineArgb;
        selectionChanged = true;
        if (fill >>> 24 != 0) {
            color(fill);
            GlStateManager.disableCull();
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer quads = tessellator.getWorldRenderer();
            quads.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
            double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
            quads.pos(x0, y0, z0).endVertex(); quads.pos(x1, y0, z0).endVertex(); quads.pos(x1, y0, z1).endVertex(); quads.pos(x0, y0, z1).endVertex();
            quads.pos(x0, y1, z0).endVertex(); quads.pos(x0, y1, z1).endVertex(); quads.pos(x1, y1, z1).endVertex(); quads.pos(x1, y1, z0).endVertex();
            quads.pos(x0, y0, z0).endVertex(); quads.pos(x0, y1, z0).endVertex(); quads.pos(x1, y1, z0).endVertex(); quads.pos(x1, y0, z0).endVertex();
            quads.pos(x0, y0, z1).endVertex(); quads.pos(x1, y0, z1).endVertex(); quads.pos(x1, y1, z1).endVertex(); quads.pos(x0, y1, z1).endVertex();
            quads.pos(x0, y0, z0).endVertex(); quads.pos(x0, y0, z1).endVertex(); quads.pos(x0, y1, z1).endVertex(); quads.pos(x0, y1, z0).endVertex();
            quads.pos(x1, y0, z0).endVertex(); quads.pos(x1, y1, z0).endVertex(); quads.pos(x1, y1, z1).endVertex(); quads.pos(x1, y0, z1).endVertex();
            tessellator.draw();
            GlStateManager.enableCull();
        }
        if (outline >>> 24 != 0) {
            color(outline);
            RenderGlobal.drawSelectionBoundingBox(box);
        }
    }

    /** Block Overlay: the outline's line width (drawSelectionBox's glLineWidth(2)). */
    public static float lineWidth(float vanilla) {
        BlockOverlayModule overlay = PvpVisuals.BLOCK_OVERLAY;
        if (!overlay.on) return vanilla;
        vanillaWidth = vanilla;
        selectionChanged = true;
        return overlay.lineWidth;
    }

    /** drawSelectionBox's colour (black at 40%, OptiFine's too), kept to restore once the overlay drew in its own colours. */
    public static void vanillaColor(float red, float green, float blue, float alpha) {
        vanillaRed = red;
        vanillaGreen = green;
        vanillaBlue = blue;
        vanillaAlpha = alpha;
        GlStateManager.color(red, green, blue, alpha);
    }

    /** drawSelectionBox returns: the line width and colour back to what vanilla leaves (2 and black at 40%). */
    public static void selectionDone() {
        if (!selectionChanged) return;
        selectionChanged = false;
        GL11.glLineWidth(vanillaWidth);
        GlStateManager.color(vanillaRed, vanillaGreen, vanillaBlue, vanillaAlpha);
    }

    /**
     * Hitboxes in place of RenderManager.renderDebugBoundingBox (x, y, z: the entity's camera-relative position); false while the
     * module is off (vanilla draws, with F3+B).
     */
    public static boolean hitbox(Entity entity, double x, double y, double z, float partial) {
        HitboxesModule module = PvpVisuals.HITBOXES;
        if (!module.on) return false;
        if (!module.shows(kind(entity))) return true;
        GlStateManager.depthMask(false);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        int color = module.color(entity instanceof EntityLivingBase && ((EntityLivingBase) entity).hurtTime > 0);
        boolean blend = color >>> 24 != 0xFF; // vanilla draws its opaque boxes unblended
        if (blend) {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        } else {
            GlStateManager.disableBlend();
        }
        if (module.walls) GlStateManager.disableDepth();
        // F3+B leaves the line width as it found it: a state query, only while the module draws boxes (once per box).
        float width = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        GL11.glLineWidth(module.lineWidth);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer lines = tessellator.getWorldRenderer();
        lines.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double dx = x - entity.posX, dy = y - entity.posY, dz = z - entity.posZ;
        boolean living = entity instanceof EntityLivingBase;
        box(lines, box.minX + dx, box.minY + dy, box.minZ + dz, box.maxX + dx, box.maxY + dy, box.maxZ + dz, color);
        float eye = entity.getEyeHeight(), half = entity.width / 2;
        if (module.eyes && living) box(lines, x - half, y + eye - 0.01, z - half, x + half, y + eye + 0.01, z + half, 0xFFFF0000);
        if (module.look) {
            // Entity.getLook without its Vec3 (living entities look by their head's yaw).
            float yaw = living ? lerp(((EntityLivingBase) entity).prevRotationYawHead, ((EntityLivingBase) entity).rotationYawHead, partial)
                : lerp(entity.prevRotationYaw, entity.rotationYaw, partial);
            float pitch = lerp(entity.prevRotationPitch, entity.rotationPitch, partial);
            double cosYaw = Math.cos(-yaw * 0.017453292F - Math.PI), sinYaw = Math.sin(-yaw * 0.017453292F - Math.PI);
            double cosPitch = -Math.cos(-pitch * 0.017453292F), sinPitch = Math.sin(-pitch * 0.017453292F);
            lines.pos(x, y + eye, z).color(0, 0, 255, 255).endVertex();
            lines.pos(x + sinYaw * cosPitch * 2, y + eye + sinPitch * 2, z + cosYaw * cosPitch * 2).color(0, 0, 255, 255).endVertex();
        }
        tessellator.draw();
        GL11.glLineWidth(width);
        if (module.walls) GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        return true;
    }

    private static float lerp(float before, float now, float partial) {
        return before + (now - before) * partial;
    }

    /** The twelve edges of a box as GL_LINES. */
    private static void box(WorldRenderer lines, double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF, a = argb >>> 24;
        edge(lines, x0, y0, z0, x1, y0, z0, r, g, b, a); edge(lines, x0, y0, z1, x1, y0, z1, r, g, b, a);
        edge(lines, x0, y1, z0, x1, y1, z0, r, g, b, a); edge(lines, x0, y1, z1, x1, y1, z1, r, g, b, a);
        edge(lines, x0, y0, z0, x0, y1, z0, r, g, b, a); edge(lines, x1, y0, z0, x1, y1, z0, r, g, b, a);
        edge(lines, x0, y0, z1, x0, y1, z1, r, g, b, a); edge(lines, x1, y0, z1, x1, y1, z1, r, g, b, a);
        edge(lines, x0, y0, z0, x0, y0, z1, r, g, b, a); edge(lines, x1, y0, z0, x1, y0, z1, r, g, b, a);
        edge(lines, x0, y1, z0, x0, y1, z1, r, g, b, a); edge(lines, x1, y1, z0, x1, y1, z1, r, g, b, a);
    }

    private static void edge(WorldRenderer lines, double x0, double y0, double z0, double x1, double y1, double z1, int r, int g, int b, int a) {
        lines.pos(x0, y0, z0).color(r, g, b, a).endVertex();
        lines.pos(x1, y1, z1).color(r, g, b, a).endVertex();
    }

    private static void color(int argb) {
        GlStateManager.color((argb >> 16 & 0xFF) / 255F, (argb >> 8 & 0xFF) / 255F, (argb & 0xFF) / 255F, (argb >>> 24) / 255F);
    }

    static int kind(Entity entity) {
        if (entity instanceof EntityPlayer) return HitboxesModule.PLAYER;
        if (entity instanceof EntityLivingBase) return HitboxesModule.MOB;
        if (entity instanceof EntityItem) return HitboxesModule.ITEM;
        return entity instanceof IProjectile ? HitboxesModule.PROJECTILE : HitboxesModule.OTHER;
    }

    /** Hide Mob Deaths: a dying mob (not a player) is not drawn. */
    public static boolean hidden(Entity entity) {
        return PvpVisuals.hideMobDeaths && entity instanceof EntityLivingBase && ((EntityLivingBase) entity).deathTime > 0 && !(entity instanceof EntityPlayer);
    }

    /** Mount Opacity: the player's mount is drawn see-through. */
    public static boolean isMount(Entity entity) {
        if (!PvpVisuals.MOUNT_OPACITY.see) return false;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        return player != null && entity == player.ridingEntity;
    }

    /**
     * Nametags' Show Ping and Nametag Health after a player's name (Render.renderLivingLabel, after its normal-pass text), on the
     * name's line (vanilla lifts deadmau5's by 10).
     */
    public static void tagExtras(FontRenderer font, Entity entity, String name) {
        if (!(entity instanceof EntityPlayer) || !PvpVisuals.nametagPing && !PvpVisuals.nametagHealth) return;
        int x = font.getStringWidth(name) / 2 + 3, y = "deadmau5".equals(name) ? -10 : 0;
        if (PvpVisuals.nametagPing) {
            NetHandlerPlayClient net = Minecraft.getMinecraft().getNetHandler();
            NetworkPlayerInfo info = net == null ? null : net.getPlayerInfo(entity.getUniqueID());
            if (info != null) {
                int color = PvpVisuals.alpha(PvpVisuals.pingColor(info.getResponseTime()), PvpVisuals.tagAlpha);
                x = font.drawString(PvpVisuals.number(info.getResponseTime()), x, y, color);
                x = font.drawString(MS, x, y, color) + 3;
            }
        }
        if (PvpVisuals.nametagHealth) {
            int color = PvpVisuals.alpha(0xFFFF5555, PvpVisuals.tagAlpha);
            x = font.drawString(PvpVisuals.number((int) Math.ceil(((EntityPlayer) entity).getHealth())), x, y, color);
            font.drawString(HEART, x, y, color);
        }
    }

    /** Glint Colorizer's speed: the clock the glint scrolls by. */
    public static long glintMillis(long millis) {
        GlintColorizerModule glint = PvpVisuals.GLINT;
        return glint.on ? (long) (millis * (double) glint.speedFactor) : millis;
    }
}
