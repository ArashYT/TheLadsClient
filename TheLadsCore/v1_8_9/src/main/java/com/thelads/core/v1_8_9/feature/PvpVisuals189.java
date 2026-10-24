package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.LightOverlay;
import com.thelads.core.client.PvpVisuals;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.BlockOverlayModule;
import com.thelads.core.modules.GlintColorizerModule;
import com.thelads.core.modules.HitboxesModule;
import com.thelads.core.modules.ItemDespawnModule;
import com.thelads.core.modules.LootBeamsModule;
import com.thelads.core.modules.MobOverlayModule;
import com.thelads.core.modules.UhcOverlayModule;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
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
 * call they hook (checked against its patched bytecode), require = 0 otherwise. Rows 31-38 (theladscore.v1_8_9.pvp3.mixins.json, mixin.visuals):
 * Oof Mod (WorldClient.playSound), Light Overlay (scanned each tick, drawn in RenderWorldLastEvent), Loot Beams and Item Despawn
 * (RenderEntityItem), Mob Overlay (the spectator outline pass) and Mob Size (RendererLivingEntity), UHC Overlay (RenderItem).
 */
public final class PvpVisuals189 {
    private static final String HEART = "\u2764", MS = "ms";
    /** Mount Opacity: the entity RendererLivingEntity.renderModel draws now is the player's mount; set and cleared around each call. */
    public static boolean mountNow;
    /** Block Overlay changed drawSelectionBox's line width or colour this call; what vanilla set there, to leave the GL state as it does. */
    private static boolean selectionChanged;
    private static float vanillaWidth = 2;
    private static float vanillaRed, vanillaGreen, vanillaBlue, vanillaAlpha = 0.4F;

    /** Light Overlay's scan, one block position reused (the client thread only). */
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    /** Light Overlay looks this many blocks above and below your feet. */
    private static final int UP = 4, DOWN = 8;
    private static Item goldOre, ironOre, diamondOre;

    private PvpVisuals189() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(PvpVisuals.MODULES);
        ModuleSupport.registerUnavailable(PvpVisuals.TOTEM, "Minecraft 1.8.9 has no Totem of Undying; it was added in Minecraft 1.11.");
        MinecraftForge.EVENT_BUS.register(new Ticks());
    }

    public static final class Ticks {
        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            PvpVisuals.tick();
            Minecraft mc = Minecraft.getMinecraft();
            if (LightOverlay.active() && mc.theWorld != null && mc.thePlayer != null) scan(mc.theWorld, mc.thePlayer);
        }

        @SubscribeEvent
        public void world(RenderWorldLastEvent event) {
            if (LightOverlay.active()) drawLightOverlay();
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

    /** Rescans this tick's columns: a mob could stand at y when the block below has a solid top and y and y + 1 are open. */
    private static void scan(World world, Entity player) {
        int r = LightOverlay.radius(), side = 2 * r + 1;
        int px = (int) Math.floor(player.posX), py = (int) Math.floor(player.posY), pz = (int) Math.floor(player.posZ);
        for (int i = LightOverlay.columnsThisTick(); i > 0; i--) {
            int column = LightOverlay.nextColumn(), x = px + column % side - r, z = pz + column / side - r;
            Block above = world.getBlockState(POS.set(x, py + UP + 1, z)).getBlock();
            Block here = world.getBlockState(POS.set(x, py + UP, z)).getBlock();
            for (int y = py + UP; y >= py - DOWN; y--) {
                IBlockState belowState = world.getBlockState(POS.set(x, y - 1, z));
                Block below = belowState.getBlock();
                if (open(here) && !above.isNormalCube() && below != Blocks.bedrock && below != Blocks.barrier
                    && World.doesBlockHaveSolidTopSurface(world, POS)) {
                    POS.set(x, y, z);
                    LightOverlay.add(column, x, y, z, LightOverlay.kind(world.getLightFor(EnumSkyBlock.BLOCK, POS), world.getLightFor(EnumSkyBlock.SKY, POS), 7));
                }
                above = here;
                here = below;
            }
        }
    }

    /** A block a mob's feet can be in: not a full cube, no liquid, nothing solid (slabs and stairs are not). */
    private static boolean open(Block block) {
        return !block.isNormalCube() && !block.getMaterial().isLiquid() && !block.getMaterial().blocksMovement();
    }

    /** Light Overlay's markers, drawn after the world (RenderWorldLastEvent: camera-relative GL, depth test on). */
    private static void drawLightOverlay() {
        if (!LightOverlay.active()) return;
        RenderManager manager = Minecraft.getMinecraft().getRenderManager();
        double cx = manager.viewerPosX, cy = manager.viewerPosY, cz = manager.viewerPosZ;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GL11.glLineWidth(2);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int column = 0, columns = LightOverlay.columns(); column < columns; column++) {
            for (int n = 0, count = LightOverlay.count(column); n < count; n++) {
                int slot = column * LightOverlay.PER_COLUMN + n, color = LightOverlay.color(LightOverlay.KIND[slot]);
                int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
                double x0 = LightOverlay.X[slot] - cx + 0.1, x1 = x0 + 0.8, y = LightOverlay.Y[slot] - cy + 0.02;
                double z0 = LightOverlay.Z[slot] - cz + 0.1, z1 = z0 + 0.8;
                buffer.pos(x0, y, z0).color(r, g, b, 255).endVertex();
                buffer.pos(x1, y, z1).color(r, g, b, 255).endVertex();
                buffer.pos(x1, y, z0).color(r, g, b, 255).endVertex();
                buffer.pos(x0, y, z1).color(r, g, b, 255).endVertex();
            }
        }
        tessellator.draw();
        GL11.glLineWidth(1);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /**
     * A loot beam from a dropped item at render offset (x, y, z): four faces of a thin column, the rarity's color fading out to the
     * top (full bright, blended, no depth writes, no alpha cut-off). Called after RenderEntityItem drew the item.
     */
    public static void beam(double x, double y, double z, int rarity) {
        if (!LootBeamsModule.beams(rarity)) return;
        int color = LootBeamsModule.color(rarity), r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        int a = (int) (LootBeamsModule.alpha() * 255);
        double s = 0.06, top = y + LootBeamsModule.height();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        face(buffer, x - s, z - s, x + s, z - s, y, top, r, g, b, a);
        face(buffer, x + s, z - s, x + s, z + s, y, top, r, g, b, a);
        face(buffer, x + s, z + s, x - s, z + s, y, top, r, g, b, a);
        face(buffer, x - s, z + s, x - s, z - s, y, top, r, g, b, a);
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
    }

    private static void face(WorldRenderer buffer, double x0, double z0, double x1, double z1, double y0, double y1, int r, int g, int b, int a) {
        buffer.pos(x0, y0, z0).color(r, g, b, a).endVertex();
        buffer.pos(x1, y0, z1).color(r, g, b, a).endVertex();
        buffer.pos(x1, y1, z1).color(r, g, b, 0).endVertex();
        buffer.pos(x0, y1, z0).color(r, g, b, 0).endVertex();
    }

    /** Whether a dropped item is on a despawn blink's off frame (Item Despawn). */
    public static boolean despawnHidden(net.minecraft.entity.item.EntityItem item) {
        return ItemDespawnModule.hidden(item.getAge(), item.lifespan);
    }

    /** What Mob Overlay and Mob Size call this entity: a hostile or a passive mob (players, stands and the rest are neither). */
    public static int mobKind(Entity entity) {
        if (!(entity instanceof EntityLiving)) return MobOverlayModule.OTHER;
        return entity instanceof IMob ? MobOverlayModule.HOSTILE : MobOverlayModule.PASSIVE;
    }

    /** UHC Overlay's group of an item: golden apples, heads (skulls), ores with their ingots and diamonds, or none. */
    public static int uhcGroup(ItemStack stack) {
        if (stack == null || !UhcOverlayModule.active()) return UhcOverlayModule.NONE;
        Item item = stack.getItem();
        if (item == Items.golden_apple) return UhcOverlayModule.APPLE;
        if (item == Items.skull) return UhcOverlayModule.HEAD;
        if (goldOre == null) {
            goldOre = Item.getItemFromBlock(Blocks.gold_ore);
            ironOre = Item.getItemFromBlock(Blocks.iron_ore);
            diamondOre = Item.getItemFromBlock(Blocks.diamond_ore);
        }
        if (item == goldOre || item == ironOre || item == diamondOre || item == Items.gold_ingot || item == Items.iron_ingot || item == Items.diamond)
            return UhcOverlayModule.ORE;
        return UhcOverlayModule.NONE;
    }
}
