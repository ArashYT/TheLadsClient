package com.thelads.core.v1_20_1.adapter;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.v1_20_1.mixin.BossBarAccessor1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GuiGraphicsLadsAdapter1201 implements LadsGraphics {
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

    /**
     * Runs the drawing as one batch: 1.20.1's GuiGraphics ends its vertex buffer after every fill, and the Lads artwork is hundreds
     * of fills per frame. Direct draws (blits) inside must flush() first so they stay in order.
     */
    public void managed(Runnable drawing) {
        guiGraphics.drawManaged(drawing);
    }

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
            for (var event : events) {
                if (row >= max) break;
                int yy = y + row++ * 19;
                overlay.ladsDrawBar(guiGraphics, x, yy + 10, event);
                if (names) {
                    guiGraphics.drawString(font, event.getName(), x + (182 - font.width(event.getName())) / 2, yy, 0xFFFFFFFF, true);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void drawArmorItem(int index, int x, int y, boolean preview) {
        var stack = armorStack(index, preview);
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x, y);
            guiGraphics.renderItemDecorations(font, stack, x, y);
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

    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        guiGraphics.fill(minX, minY, maxX, maxY, color);
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        if (text != null && font != null) {
            guiGraphics.drawString(font, text, x, y, color, shadow);
        }
    }

    @Override
    public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {
        if (text != null && font != null) {
            guiGraphics.drawString(font, text, centerX - font.width(text) / 2, y, color, shadow);
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
        guiGraphics.blit(loc, x, y, u, v, width, height);
    }

    @Override
    public void drawHead(String username, String uuid, int x, int y, int size) {
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
        return 0;
    }
}
