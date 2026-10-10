package com.thelads.core.v1_20_1.adapter;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.v1_20_1.feature.NativeAutohide1201;
import com.thelads.core.v1_20_1.hud.Raised1201;
import com.thelads.core.v1_20_1.mixin.BossBarAccessor1201;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Lads HUD's drawing surface on 1.20.1. While Auto Hide has the HUD partly faded ({@link NativeAutohide1201#scopeOpacity}) every
 * fill and text is tinted with that opacity, and sprites and items are drawn through the shader colour's alpha.
 */
public class GuiGraphicsLadsAdapter1201 implements LadsGraphics {
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    private static final ResourceLocation[] EMPTY_ARMOR = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
        InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};
    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final GuiGraphics guiGraphics;
    private final Font font;
    private static volatile Object metricsEpoch = new Object();
    private static Font metricsFont;

    public static void invalidateMetrics() {
        metricsEpoch = new Object();
    }

    @Override
    public Object textMetricsKey() {
        if (metricsFont != font) {
            metricsFont = font;
            invalidateMetrics();
        }
        return metricsEpoch;
    }

    public GuiGraphicsLadsAdapter1201(GuiGraphics guiGraphics, Font font) {
        this.guiGraphics = guiGraphics;
        this.font = (font != null) ? font : Minecraft.getInstance().font;
    }

    public GuiGraphicsLadsAdapter1201(GuiGraphics guiGraphics) {
        this(guiGraphics, Minecraft.getInstance().font);
    }

    public GuiGraphics getVanilla() {
        return guiGraphics;
    }

    private static float alpha() {
        return NativeAutohide1201.scopeOpacity;
    }

    private static int tint(int color) {
        float alpha = alpha();
        return alpha >= 1 ? color : com.thelads.core.client.hud.AutohideFade.tint(color, alpha);
    }

    /** Textures and items fade through the shader colour; {@link #endFade} puts it back. */
    private void fade(float extra) {
        float alpha = alpha() * extra;
        guiGraphics.setColor(1, 1, 1, alpha);
        RenderSystem.enableBlend();
    }

    private void endFade() {
        guiGraphics.setColor(1, 1, 1, 1);
    }

    @Override
    public boolean drawKillBanner(String skin, int variant, int x, int y, int width, int height) {
        return com.thelads.core.v1_20_1.feature.NativeKillBanner1201.drawThumb(guiGraphics, skin, variant, x, y, width, height);
    }

    @Override
    public boolean drawKillBannerPreview(String skin, int variant, int x, int y, int width, int height, double clock) {
        return com.thelads.core.v1_20_1.feature.NativeKillBanner1201.drawPreview(guiGraphics, skin, variant, x, y, width, height, clock);
    }

    @Override
    public void drawPlayerModel(int x, int y, int width, int height, boolean editor) {
        com.thelads.core.v1_20_1.hud.overlay.PaperDoll1201.render(guiGraphics, x, y, width, height, editor);
    }

        /**
     * Runs the drawing as one batch: 1.20.1's GuiGraphics ends its vertex buffer after every fill, and the Lads artwork is hundreds
     * of fills per frame. Direct draws (blits) inside must flush() first so they stay in order.
     */
    public void managed(Runnable drawing) {
        if (noBatch) drawing.run();
        else guiGraphics.drawManaged(drawing);
    }

    /** QA switch (MenusQa1201): draw the Lads artwork call by call, to compare with the batched path. */
    public static volatile boolean noBatch;

    @Override
    public void drawModIcon(String id, int x, int y, int size) {
        if (!com.thelads.core.v1_20_1.gui.ModIcons1201.draw(guiGraphics, id, x, y, size)) LadsGraphics.super.drawModIcon(id, x, y, size);
    }

    @Override
    public void drawBossBars(int x, int y, int max, boolean names, boolean preview) {
        try {
            var overlay = (BossBarAccessor1201) Minecraft.getInstance().gui.getBossOverlay();
            var events = new ArrayList<net.minecraft.world.BossEvent>(overlay.ladsEvents().values());
            if (events.isEmpty() && preview) {
                events.add(new net.minecraft.client.gui.components.LerpingBossEvent(
                    UUID.randomUUID(),
                    net.minecraft.network.chat.Component.literal("Boss bar preview"),
                    0.65f,
                    net.minecraft.world.BossEvent.BossBarColor.PURPLE,
                    net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS,
                    false, false, false));
            }
            int row = 0;
            fade(1);
            try {
                for (var event : events) {
                    if (row >= max) break;
                    int yy = y + row++ * 19;
                    overlay.ladsDrawBar(guiGraphics, x, yy + 10, event);
                    if (names) {
                        guiGraphics.drawString(font, event.getName(), x + (182 - font.width(event.getName())) / 2, yy, tint(0xFFFFFFFF), true);
                    }
                }
            } finally {
                endFade();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void drawArmorItem(int index, int x, int y, boolean preview) {
        var stack = armorStack(index, preview);
        if (!stack.isEmpty()) {
            fade(1);
            try {
                guiGraphics.renderItem(stack, x, y);
                guiGraphics.renderItemDecorations(font, stack, x, y);
            } finally {
                endFade();
            }
        }
    }

    static ItemStack armorStack(int index, boolean preview) {
        if (preview) {
            var stack = new ItemStack(index == 0 ? Items.DIAMOND_HELMET : Items.DIAMOND_CHESTPLATE);
            stack.setDamageValue(stack.getMaxDamage() / 4);
            return stack;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return ItemStack.EMPTY;
        int visible = 0;
        EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        for (var slot : slots) {
            var candidate = player.getItemBySlot(slot);
            if (!candidate.isEmpty() && visible++ == index) return candidate;
        }
        return ItemStack.EMPTY;
    }

    /** The left part of vanilla's hotbar frame with this many slots and its right edge. */
    @Override
    public void drawHotbarSlots(int x, int y, int slots) {
        int width = 1 + slots * 20;
        fade(1);
        try {
            guiGraphics.blit(WIDGETS, x, y, 0, 0, width, 22);
            guiGraphics.blit(WIDGETS, x + width, y, 181, 0, 1, 22);
        } finally {
            endFade();
        }
    }

    /** Armour slot 0 head to 3 feet: the worn piece with its durability bar, or a faint empty-slot icon (preview: a sample set). */
    @Override
    public void drawArmorSlot(int slot, int x, int y, boolean preview) {
        var player = Minecraft.getInstance().player;
        ItemStack stack = ItemStack.EMPTY;
        if (preview) {
            // The editor sample: a worn helmet, no chestplate, leggings and boots.
            net.minecraft.world.item.Item[] items = {Items.TURTLE_HELMET, null, Items.IRON_LEGGINGS, Items.GOLDEN_BOOTS};
            if (items[slot] != null) {
                stack = new ItemStack(items[slot]);
                if (slot != 2) stack.setDamageValue(stack.getMaxDamage() * (slot == 0 ? 9 : 2) / 10);
            }
        } else if (player != null) {
            stack = player.getItemBySlot(WORN[slot]);
        }
        if (stack.isEmpty()) {
            var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(EMPTY_ARMOR[slot]);
            fade(0.35f);
            try {
                guiGraphics.blit(x, y, 0, 16, 16, sprite);
            } finally {
                endFade();
            }
        } else {
            fade(1);
            try {
                guiGraphics.renderItem(stack, x, y);
                guiGraphics.renderItemDecorations(font, stack, x, y);
            } finally {
                endFade();
            }
        }
    }

    /** A GUI sprite such as "voicechat:icons/microphone": the 16 px texture textures/icons/microphone.png of that namespace. */
    @Override
    public void drawSprite(String sprite, int x, int y, int size) {
        if (sprite == null) return;
        int colon = sprite.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : sprite.substring(0, colon), path = colon < 0 ? sprite : sprite.substring(colon + 1);
        ResourceLocation texture = new ResourceLocation(namespace, "textures/" + path + ".png");
        int[] dimensions = TextureSizes1201.of(texture);
        if (dimensions == null) return;
        fade(1);
        try {
            guiGraphics.blit(texture, x, y, size, size, 0, 0, dimensions[0], dimensions[1], dimensions[0], dimensions[1]);
        } finally {
            endFade();
        }
    }

    /** A mob effect icon next to the potion effect name (effectId is the effect's registry id). */
    @Override
    public void drawPotionIcon(String effectId, int iconIndex, int x, int y, int size) {
        if (effectId == null || effectId.isEmpty()) return;
        try {
            var effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(effectId));
            if (effect == null) return;
            var sprite = Minecraft.getInstance().getMobEffectTextures().get(effect);
            fade(1);
            try {
                guiGraphics.blit(x, y, 0, size, size, sprite);
            } finally {
                endFade();
            }
        } catch (RuntimeException ignored) {}
    }

    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        guiGraphics.fill(minX, minY, maxX, maxY, tint(color));
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        if (text != null && font != null) {
            guiGraphics.drawString(font, text, x, y, tint(color), shadow);
        }
    }

    @Override
    public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {
        if (text != null && font != null) {
            guiGraphics.drawString(font, text, centerX - font.width(text) / 2, y, tint(color), shadow);
        }
    }

    @Override
    public int textWidth(String text) {
        return (text != null && font != null) ? font.width(text) : 0;
    }

    @Override
    public int fontHeight() {
        return (font != null) ? font.lineHeight : 9;
    }

    @Override
    public void pushPose() {
        guiGraphics.pose().pushPose();
    }

    @Override
    public void popPose() {
        guiGraphics.pose().popPose();
    }

    @Override
    public void translate(float x, float y) {
        guiGraphics.pose().translate(x, y, 0.0f);
    }

    @Override
    public void scale(float sx, float sy) {
        guiGraphics.pose().scale(sx, sy, 1.0f);
    }

    /** Popups sit 400 in front (vanilla's tooltip depth): text and items drawn earlier are in front of a fill at the same depth. */
    @Override
    public void overlay(Runnable draw) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0f, 0.0f, 400.0f);
        try {
            draw.run();
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    @Override
    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        guiGraphics.enableScissor(minX, minY, maxX, maxY);
    }

    @Override
    public void disableScissor() {
        guiGraphics.disableScissor();
    }

    @Override
    public void blit(String texture, int x, int y, int u, int v, int width, int height) {
        ResourceLocation loc = new ResourceLocation(texture != null ? texture : "minecraft:textures/gui/icons.png");
        fade(1);
        try {
            guiGraphics.blit(loc, x, y, u, v, width, height);
        } finally {
            endFade();
        }
    }

    @Override
    public void drawHead(String username, String uuid, int x, int y, int size) {
        fade(1);
        try {
            drawHeadFaded(username, uuid, x, y, size);
        } finally {
            endFade();
        }
    }

    private void drawHeadFaded(String username, String uuid, int x, int y, int size) {
        try {
            Minecraft mc = Minecraft.getInstance();
            UUID parsedUuid = null;
            if (uuid != null && !uuid.isBlank()) {
                String clean = uuid.replace("-", "").trim();
                if (clean.length() == 32) {
                    try {
                        parsedUuid = UUID.fromString(clean.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
                    } catch (Exception ignored) {}
                } else {
                    try {
                        parsedUuid = UUID.fromString(uuid.trim());
                    } catch (Exception ignored) {}
                }
            }
            if (parsedUuid == null) {
                parsedUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + (username != null ? username : "Player")).getBytes(StandardCharsets.UTF_8));
            }
            GameProfile profile = new GameProfile(parsedUuid, username != null ? username : "Player");
            ResourceLocation skinLocation = mc.getSkinManager().getInsecureSkinLocation(profile);
            if (skinLocation == null) {
                skinLocation = DefaultPlayerSkin.getDefaultSkin(parsedUuid);
            }
            if (skinLocation != null) {
                PlayerFaceRenderer.draw(guiGraphics, skinLocation, x, y, size);
                return;
            }
        } catch (Exception ignored) {}
        try {
            ResourceLocation fallbackSkin = DefaultPlayerSkin.getDefaultSkin();
            PlayerFaceRenderer.draw(guiGraphics, fallbackSkin, x, y, size);
        } catch (Exception ignored) {}
    }

    @Override
    public int getScaledWidth() {
        return guiGraphics.guiWidth();
    }

    @Override
    public int getScaledHeight() {
        return guiGraphics.guiHeight();
    }

    @Override
    public int hotbarLift() {
        return Raised1201.hotbar();
    }

    @Override
    public int guiScale() {
        return (int) Minecraft.getInstance().getWindow().getGuiScale();
    }
}
