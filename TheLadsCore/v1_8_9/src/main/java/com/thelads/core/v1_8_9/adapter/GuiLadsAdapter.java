package com.thelads.core.v1_8_9.adapter;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.BossBarColor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * LadsGraphics over 1.8.9's immediate-mode Gui, FontRenderer and GlStateManager, in scaled GUI coordinates. Every call leaves
 * 1.8.9's GUI state contract: alpha test on (text needs it once a fill has turned blending off) and lighting off.
 */
public class GuiLadsAdapter implements LadsGraphics {
    /** QA only (CoreProbe): every text and item drawn while non-null. */
    public static List<String> recording;
    private FontRenderer font;
    private int width, height;
    /** Nested scissors intersect, as GuiGraphics' scissor stack does on the other versions. */
    private final Deque<int[]> scissors = new ArrayDeque<>();
    /** Per frame, not per scissor: the scale factor does not change inside one HUD pass (cleared by reset). */
    private ScaledResolution resolution;
    private static final java.util.Map<String, ResourceLocation> TEXTURES = new java.util.HashMap<>();
    private static final java.util.Map<String, UUID> UUIDS = new java.util.HashMap<>(), NAME_UUIDS = new java.util.HashMap<>();
    /** drawTexturedModalRect only reads zLevel (0) from it. */
    private static final Gui GUI = new Gui();

    public GuiLadsAdapter(FontRenderer font, int width, int height) {
        reset(font, width, height);
    }

    /** The same adapter for the next frame (the Lads HUD keeps one instead of allocating one per frame). */
    public void reset(FontRenderer font, int width, int height) {
        scissors.clear();
        resolution = null;
        this.font = font != null ? font : Minecraft.getMinecraft().fontRendererObj;
        this.width = width;
        this.height = height;
    }

    /** As vanilla's drawGradientRect: the alpha test (GREATER 0.1) would drop faint fills such as the editor's grid. */
    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        GlStateManager.disableAlpha();
        Gui.drawRect(minX, minY, maxX, maxY, color);
        GlStateManager.enableAlpha();
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        if (text == null) return;
        if (recording != null) recording.add(text);
        font.drawString(text, x, y, color, shadow);
    }

    @Override
    public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {
        if (text == null) return;
        if (recording != null) recording.add(text);
        font.drawString(text, centerX - font.getStringWidth(text) / 2, y, color, shadow);
    }

    /** The index-th worn piece (feet first, as the bridge lists them) through RenderItem with GUI item lighting, as the hotbar draws items. */
    @Override
    public void drawArmorItem(int index, int x, int y, boolean preview) {
        ItemStack stack = armorStack(index, preview);
        if (stack == null) return;
        if (recording != null) recording.add("item:" + stack.getDisplayName());
        RenderItem items = Minecraft.getMinecraft().getRenderItem();
        GlStateManager.enableDepth(); // block models (a pumpkin) need it; the Lads HUD draws the rest without
        RenderHelper.enableGUIStandardItemLighting();
        items.renderItemAndEffectIntoGUI(stack, x, y);
        items.renderItemOverlays(font, stack, x, y);
        // RenderItem leaves lighting and depth on and, without a durability bar, the alpha test off.
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public void drawItemIcon(String itemKey, int x, int y) {
        ItemStack stack = resolveItem(itemKey);
        if (stack == null) return;
        RenderItem items = Minecraft.getMinecraft().getRenderItem();
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        items.renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static ItemStack resolveItem(String key) {
        if (key == null) return null;
        switch (key) {
            case "bed": return new ItemStack(Items.bed);
            case "feather": return new ItemStack(Items.feather);
            case "diamond_sword": return new ItemStack(Items.diamond_sword);
            case "iron_sword": return new ItemStack(Items.iron_sword);
            case "stone_sword": return new ItemStack(Items.stone_sword);
            case "golden_sword": return new ItemStack(Items.golden_sword);
            case "bow": return new ItemStack(Items.bow);
            case "tnt": return new ItemStack(net.minecraft.init.Blocks.tnt);
            case "slime_ball": return new ItemStack(Items.slime_ball);
            case "painting": return new ItemStack(Items.painting);
            case "golden_apple": return new ItemStack(Items.golden_apple);
            case "gold_block": return new ItemStack(net.minecraft.init.Blocks.gold_block);
            case "obsidian": return new ItemStack(net.minecraft.init.Blocks.obsidian);
            case "ender_pearl": return new ItemStack(Items.ender_pearl);
            case "magma_cream": return new ItemStack(Items.magma_cream);
            case "potion": return new ItemStack(Items.potionitem);
            case "raw_fish": return new ItemStack(Items.fish);
            case "diamond_chestplate": return new ItemStack(Items.diamond_chestplate);
            case "leather_chestplate": return new ItemStack(Items.leather_chestplate);
            case "leather_boots": return new ItemStack(Items.leather_boots);
            case "clay_ball": return new ItemStack(Items.clay_ball);
            case "banner": return new ItemStack(Items.banner);
            case "water_bucket": return new ItemStack(Items.water_bucket);
            case "skull": return new ItemStack(Items.skull);
            case "rotten_flesh": return new ItemStack(Items.rotten_flesh);
            case "clock": return new ItemStack(Items.clock);
            case "brick": return new ItemStack(net.minecraft.init.Blocks.brick_block);
            case "wheat": return new ItemStack(Items.wheat);
            case "cake": return new ItemStack(Items.cake);
            case "glass": return new ItemStack(net.minecraft.init.Blocks.glass);
            case "paper": return new ItemStack(Items.paper);
            case "wool": return new ItemStack(net.minecraft.init.Blocks.wool);
            case "dragon_egg": return new ItemStack(net.minecraft.init.Blocks.dragon_egg);
            case "iron_door": return new ItemStack(Items.iron_door);
            case "hay_block": return new ItemStack(net.minecraft.init.Blocks.hay_block);
            case "coal": return new ItemStack(Items.coal);
            case "note_block": return new ItemStack(net.minecraft.init.Blocks.noteblock);
            case "diamond_shovel": return new ItemStack(Items.diamond_shovel);
            case "blaze_rod": return new ItemStack(Items.blaze_rod);
            case "blaze_powder": return new ItemStack(Items.blaze_powder);
            case "iron_ingot": return new ItemStack(Items.iron_ingot);
            case "snowball": return new ItemStack(Items.snowball);
            case "wither_skeleton_skull": return new ItemStack(Items.skull, 1, 1);
            case "firework_charge": return new ItemStack(Items.firework_charge);
            case "grass": return new ItemStack(net.minecraft.init.Blocks.grass);
            case "door": return new ItemStack(Items.oak_door);
            case "compass": return new ItemStack(Items.compass);
            case "barrier": return new ItemStack(net.minecraft.init.Blocks.barrier);
            case "nether_star": return new ItemStack(Items.nether_star);
            default: return new ItemStack(Items.compass);
        }
    }

    @Override
    public void drawHotbarSlots(int x, int y, int slots) {
        if (slots == 4) {
            GlStateManager.enableBlend();
            blit("minecraft:textures/gui/widgets.png", x, y, 0, 0, 41, 22);
            blit("minecraft:textures/gui/widgets.png", x + 41, y, 141, 0, 41, 22);
            return;
        }
        int width = 1 + slots * 20;
        GlStateManager.enableBlend();
        blit("minecraft:textures/gui/widgets.png", x, y, 0, 0, width, 22);
        blit("minecraft:textures/gui/widgets.png", x + width, y, 181, 0, 1, 22);
    }

    /** Armour slot 0 head to 3 feet as the hotbar draws items, or the inventory's empty-slot icon, faint. */
    @Override
    public void drawArmorSlot(int slot, int x, int y, boolean preview) {
        Minecraft mc = Minecraft.getMinecraft();
        ItemStack stack = null;
        if (preview) {
            // The editor sample: a worn helmet, no chestplate, leggings and boots.
            net.minecraft.item.Item[] items = {Items.chainmail_helmet, null, Items.iron_leggings, Items.golden_boots};
            if (items[slot] != null) {
                stack = new ItemStack(items[slot]);
                if (slot != 2) stack.setItemDamage(stack.getMaxDamage() * (slot == 0 ? 9 : 2) / 10);
            }
        } else if (mc.thePlayer != null) stack = mc.thePlayer.inventory.armorInventory[3 - slot];
        if (stack != null) {
            if (recording != null) recording.add("item:" + stack.getDisplayName());
            RenderItem items = mc.getRenderItem();
            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            items.renderItemAndEffectIntoGUI(stack, x, y);
            items.renderItemOverlays(font, stack, x, y);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            return;
        }
        mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.locationBlocksTexture);
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 0.3f);
        // 1.8.9's silhouettes are near-black (made for the light inventory slot): GL_BLEND draws 1 - texel, a light silhouette, as on 26.x.
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_BLEND);
        GUI.drawTexturedModalRect(x, y, mc.getTextureMapBlocks().getAtlasSprite(net.minecraft.item.ItemArmor.EMPTY_SLOT_NAMES[slot]), 16, 16);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public void drawInventorySlot(int slot, int x, int y, boolean preview) {
        Minecraft mc = Minecraft.getMinecraft();
        ItemStack stack = null;
        if (preview) {
            if (slot == 0) stack = new ItemStack(net.minecraft.init.Items.diamond_sword);
            else if (slot == 1) stack = new ItemStack(net.minecraft.init.Items.golden_apple, 16);
            else if (slot == 2) stack = new ItemStack(net.minecraft.init.Items.ender_pearl, 8);
            else if (slot == 3) stack = new ItemStack(net.minecraft.init.Items.diamond_pickaxe);
            else if (slot == 4) stack = new ItemStack(net.minecraft.init.Blocks.cobblestone, 64);
            else if (slot == 5) stack = new ItemStack(net.minecraft.init.Items.water_bucket);
            else if (slot == 6) stack = new ItemStack(net.minecraft.init.Items.bow);
            else if (slot == 7) stack = new ItemStack(net.minecraft.init.Items.arrow, 32);
        } else if (mc.thePlayer != null && mc.thePlayer.inventory != null) {
            int idx = slot + 9;
            if (idx >= 0 && idx < mc.thePlayer.inventory.mainInventory.length) {
                stack = mc.thePlayer.inventory.mainInventory[idx];
            }
        }
        if (stack != null) {
            RenderItem items = mc.getRenderItem();
            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            items.renderItemAndEffectIntoGUI(stack, x, y);
            items.renderItemOverlays(font, stack, x, y);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    /** A damaged diamond sample in the editor when nothing is worn (as on 1.21.11). */
    static ItemStack armorStack(int index, boolean preview) {
        if (preview) {
            ItemStack stack = new ItemStack(index == 0 ? Items.diamond_helmet : Items.diamond_chestplate);
            stack.setItemDamage(stack.getMaxDamage() / 4);
            return stack;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return null;
        int visible = 0;
        for (ItemStack stack : mc.thePlayer.inventory.armorInventory)
            if (stack != null && visible++ == index) return stack;
        return null;
    }

    @Override
    public int textWidth(String text) {
        return text != null ? font.getStringWidth(text) : 0;
    }

    /** Changes on a resource reload or a Unicode/bidi switch (FontCache189), so the HUD keeps measured text widths until then. */
    @Override
    public Object textMetricsKey() {
        return com.thelads.core.v1_8_9.feature.FontCache189.metricsKey(font);
    }

    @Override
    public int fontHeight() {
        return font.FONT_HEIGHT;
    }

    @Override
    public void pushPose() {
        GlStateManager.pushMatrix();
    }

    @Override
    public void popPose() {
        GlStateManager.popMatrix();
    }

    @Override
    public void translate(float x, float y) {
        GlStateManager.translate(x, y, 0.0f);
    }

    @Override
    public void scale(float sx, float sy) {
        GlStateManager.scale(sx, sy, 1.0f);
    }

    @Override
    public boolean drawCrosshairPreview(int cx, int cy) {
        return com.thelads.core.v1_8_9.feature.Crosshair189.preview(cx, cy);
    }

    @Override
    public boolean drawImage(String source, int x, int y, int width, int height) {
        return com.thelads.core.v1_8_9.gui.CrosshairBackdrop189.draw(source, x, y, width, height);
    }

    @Override
    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        int[] box = {minX, minY, maxX, maxY};
        int[] outer = scissors.peek();
        if (outer != null) box = new int[] {Math.max(minX, outer[0]), Math.max(minY, outer[1]), Math.min(maxX, outer[2]), Math.min(maxY, outer[3])};
        scissors.push(box);
        apply(box);
    }

    @Override
    public void disableScissor() {
        scissors.poll();
        int[] outer = scissors.peek();
        if (outer != null) apply(outer);
        else GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private ScaledResolution resolution() {
        if (resolution == null) resolution = new ScaledResolution(Minecraft.getMinecraft());
        return resolution;
    }

    /** The skin owner's id: the uuid text parsed once, or the offline id of the name. */
    private static UUID parseId(String username, String uuid) throws java.io.UnsupportedEncodingException {
        boolean real = uuid != null && !uuid.trim().isEmpty();
        String key = real ? uuid : String.valueOf(username);
        java.util.Map<String, UUID> cache = real ? UUIDS : NAME_UUIDS;
        UUID id = cache.get(key);
        if (id == null) {
            if (cache.size() > 256) cache.clear();
            id = real ? UUID.fromString(uuid) : UUID.nameUUIDFromBytes(key.getBytes("UTF-8"));
            cache.put(key, id);
        }
        return id;
    }

    /** GUI rectangle to window pixels; OpenGL's scissor origin is the bottom-left corner. */
    private void apply(int[] box) {
        Minecraft mc = Minecraft.getMinecraft();
        double scale = com.thelads.core.v1_8_9.qol.Qol189.scale(resolution()); // GUI Scale may be fractional
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (box[0] * scale), mc.displayHeight - (int) (box[3] * scale),
            (int) (Math.max(0, box[2] - box[0]) * scale), (int) (Math.max(0, box[3] - box[1]) * scale));
    }

    @Override
    public void blit(String texture, int x, int y, int u, int v, int width, int height) {
        String path = texture != null ? texture : "minecraft:textures/gui/icons.png";
        ResourceLocation location = TEXTURES.get(path);
        if (location == null) TEXTURES.put(path, location = new ResourceLocation(path));
        Minecraft.getMinecraft().getTextureManager().bindTexture(location);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, 256.0f, 256.0f);
    }

    @Override
    public void drawPotionIcon(String effectId, int iconIndex, int x, int y, int size) {
        if (iconIndex >= 0) {
            int iconX = (iconIndex % 8) * 18;
            int iconY = 198 + (iconIndex / 8) * 18;
            ResourceLocation location = TEXTURES.get("minecraft:textures/gui/container/inventory.png");
            if (location == null) TEXTURES.put("minecraft:textures/gui/container/inventory.png", location = new ResourceLocation("textures/gui/container/inventory.png"));
            Minecraft.getMinecraft().getTextureManager().bindTexture(location);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.enableBlend();
            Gui.drawScaledCustomSizeModalRect(x, y, iconX, iconY, 18, 18, size, size, 256, 256);
        }
    }

    /** The default pack's pack.png, uploaded on first use. */
    private static ResourceLocation defaultPackIcon;

    /**
     * A selected pack's icon from its repository entry, which is what the pack screen shows (the entry read its pack.png and fell back to
     * the default pack's, and keeps the uploaded texture for as long as the pack list holds it). "Default", or a pack no longer selected,
     * gets the default pack's own icon.
     */
    @Override
    public void drawPackIcon(String pack, int x, int y, int size) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            ResourcePackRepository.Entry found = null;
            for (ResourcePackRepository.Entry entry : mc.getResourcePackRepository().getRepositoryEntries())
                if (entry.getResourcePackName().equals(pack)) found = entry;
            if (found != null) found.bindTexturePackIcon(mc.getTextureManager());
            else {
                if (defaultPackIcon == null)
                    defaultPackIcon = mc.getTextureManager().getDynamicTextureLocation("lads_default_pack", new DynamicTexture(mc.mcDefaultResourcePack.getPackImage()));
                mc.getTextureManager().bindTexture(defaultPackIcon);
            }
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.enableBlend();
            Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, 1, 1, size, size, 1, 1);
        } catch (Exception e) {
            LadsGraphics.super.drawPackIcon(pack, x, y, size);
        }
    }

    /** Face and hat layer of the local player's skin, or the default skin for anyone else. */
    @Override
    public void drawHead(String username, String uuid, int x, int y, int size) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            UUID id = parseId(username, uuid);
            ResourceLocation skin = mc.thePlayer != null && id.equals(mc.thePlayer.getUniqueID())
                ? mc.thePlayer.getLocationSkin() : DefaultPlayerSkin.getDefaultSkin(id);
            mc.getTextureManager().bindTexture(skin);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.enableBlend();
            Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
            Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, size, size, 64, 64);
        } catch (Exception e) {
            fill(x, y, x + size, y + size, 0xFF6C63FF);
        }
    }

    /** Kill Banner picker art, as 26.x NativeKillBanner.drawThumb. */
    @Override
    public boolean drawKillBanner(String skin, int variant, int x, int y, int width, int height) {
        return com.thelads.core.v1_8_9.feature.KillBanner189.drawThumb(skin, variant, x, y, width, height);
    }

    @Override
    public boolean drawKillBannerPreview(String skin, int variant, int x, int y, int width, int height, double clock) {
        return com.thelads.core.v1_8_9.feature.KillBanner189.drawPreview(skin, variant, x, y, width, height, clock);
    }

    @Override
    public void drawPlayerModel(int x, int y, int width, int height, boolean editor) {
        com.thelads.core.v1_8_9.feature.PaperDoll189.render(x, y, width, height, editor, this.width);
    }

    @Override
    public int hotbarLift() {
        return com.thelads.core.v1_8_9.feature.Raised189.hotbar();
    }

    @Override
    public int guiScale() {
        return resolution().getScaleFactor();
    }

    @Override
    public int[] bossBarsBounds(int x, int y, int max, boolean names, boolean preview) {
        boolean hasBoss = net.minecraft.entity.boss.BossStatus.bossName != null && net.minecraft.entity.boss.BossStatus.statusBarTime > 0;
        if (!hasBoss && !preview) return new int[0];
        int nameX = x, nameW = 0;
        if (names) {
            nameW = font.getStringWidth(hasBoss ? net.minecraft.entity.boss.BossStatus.bossName : "Ender Dragon") + 1; // + the shadow
            nameX = x + (182 - nameW + 1) / 2;
        }
        return new int[]{Math.min(x, nameX), y, Math.max(x + 183, nameX + nameW), y + 15};
    }

    private static final ResourceLocation LADS_BARS = new ResourceLocation("theladscore", "textures/gui/bars.png");
    private static Boolean hasLadsBars = null;

    private static boolean hasBarsTexture(Minecraft mc) {
        if (hasLadsBars == null) {
            try {
                hasLadsBars = mc.getResourceManager().getResource(LADS_BARS) != null;
            } catch (Exception e) {
                hasLadsBars = false;
            }
        }
        return hasLadsBars;
    }

    @Override
    public void drawBossBars(int x, int y, int max, boolean names, boolean preview) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean hasBoss = net.minecraft.entity.boss.BossStatus.bossName != null && net.minecraft.entity.boss.BossStatus.statusBarTime > 0;
        if (!hasBoss && !preview) return;

        String name = hasBoss ? net.minecraft.entity.boss.BossStatus.bossName : "Ender Dragon";
        float health = hasBoss ? net.minecraft.entity.boss.BossStatus.healthScale : 0.85f;

        boolean colored = com.thelads.core.v1_8_9.feature.Options189.bool("BossBar", "Colored boss bar", true);
        BossBarColor barColor = colored ? BossBarColor.fromFormattedName(name) : null;

        int barW = 182;
        int barH = 5;

        if (barColor != null && hasBarsTexture(mc)) {
            mc.getTextureManager().bindTexture(LADS_BARS);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            int vEmpty = barColor.ordinal() * 10;
            int vFill = vEmpty + 5;
            Gui.drawModalRectWithCustomSizedTexture(x, y + 10, 0, vEmpty, barW, barH, 256.0f, 256.0f);
            int filled = (int)(health * (barW + 1));
            if (filled > 0) {
                Gui.drawModalRectWithCustomSizedTexture(x, y + 10, 0, vFill, filled, barH, 256.0f, 256.0f);
            }
        } else if (barColor != null) {
            mc.getTextureManager().bindTexture(Gui.icons);
            GlStateManager.color(barColor.r(), barColor.g(), barColor.b(), 1.0f);
            Gui.drawModalRectWithCustomSizedTexture(x, y + 10, 0, 74, barW, barH, 256.0f, 256.0f);
            int filled = (int)(health * (barW + 1));
            if (filled > 0) {
                Gui.drawModalRectWithCustomSizedTexture(x, y + 10, 0, 79, filled, barH, 256.0f, 256.0f);
            }
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            mc.getTextureManager().bindTexture(Gui.icons);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            Gui.drawModalRectWithCustomSizedTexture(x, y + 10, 0, 74, barW, barH, 256.0f, 256.0f);
            int filled = (int)(health * (barW + 1));
            if (filled > 0) {
                Gui.drawModalRectWithCustomSizedTexture(x, y + 10, 0, 79, filled, barH, 256.0f, 256.0f);
            }
        }
        if (names && name != null) {
            int textX = x + (barW - font.getStringWidth(name)) / 2;
            font.drawString(name, textX, y, 0xFFFFFFFF, com.thelads.core.config.HudSettings.getInstance().isTextShadow());
        }
    }
    @Override
    public int getScaledWidth() {
        return width;
    }

    @Override
    public int getScaledHeight() {
        return height;
    }
}
