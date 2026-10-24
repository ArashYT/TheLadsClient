package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.LightOverlay;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.ItemDespawnModule;
import com.thelads.core.modules.LootBeamsModule;
import com.thelads.core.modules.MobOverlayModule;
import com.thelads.core.modules.MobSizeModule;
import com.thelads.core.modules.OofModule;
import com.thelads.core.modules.UhcOverlayModule;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.IMob;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * The PvP visuals of PARITY-1023 rows 31-38 on 1.8.9: Oof Mod (WorldClientOofMixin189), Light Overlay (scanned here each tick,
 * drawn in RenderWorldLastEvent), Loot Beams and Item Despawn (RenderEntityItemVisualsMixin189), Mob Overlay (the spectator
 * outline pass, RenderGlobalOutlineMixin189 and RendererLivingEntityVisualsMixin189), Mob Size (RendererLivingEntityVisualsMixin189)
 * and UHC Overlay (RenderItemUhcMixin189). 1.8.9 has no shulkers: Mob Overlay's shulker bullets are 1.9+ only.
 */
public final class PvpVisuals189 {
    /** Light Overlay's scan, one block position reused (the client thread only). */
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    /** Light Overlay looks this many blocks above and below your feet. */
    private static final int UP = 4, DOWN = 8;
    private static Item goldOre, ironOre, diamondOre;

    private PvpVisuals189() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(OofModule.NAME, LightOverlay.NAME, LootBeamsModule.NAME, MobOverlayModule.NAME, MobSizeModule.NAME,
            ItemDespawnModule.NAME, UhcOverlayModule.NAME);
        MinecraftForge.EVENT_BUS.register(new PvpVisuals189());
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LightOverlay.refresh();
        LootBeamsModule.refresh();
        MobOverlayModule.refresh();
        MobSizeModule.refresh();
        ItemDespawnModule.refresh();
        UhcOverlayModule.refresh();
        Minecraft mc = Minecraft.getMinecraft();
        if (LightOverlay.active() && mc.theWorld != null && mc.thePlayer != null) scan(mc.theWorld, mc.thePlayer);
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

    @SubscribeEvent
    public void world(RenderWorldLastEvent event) {
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
    public static int kind(Entity entity) {
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
