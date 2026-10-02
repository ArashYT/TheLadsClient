package com.thelads.core.v1_21_11.adapter;

import com.thelads.core.client.bridge.LadsGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

public class GuiGraphicsLadsAdapter implements LadsGraphics {
    private final GuiGraphics guiGraphics;
    private final Font font;
    private static volatile Object metricsEpoch = new Object();
    private static Font metricsFont;
    /** FontMetricsMixin: a font or resource-pack reload changes text widths. */
    public static void invalidateMetrics() { metricsEpoch = new Object(); }
    @Override public Object textMetricsKey() {
        if (metricsFont != font) { metricsFont = font; invalidateMetrics(); }
        return metricsEpoch;
    }

    public GuiGraphicsLadsAdapter(GuiGraphics guiGraphics, Font font) {
        this.guiGraphics = guiGraphics;
        this.font = (font != null) ? font : Minecraft.getInstance().font;
    }

    public GuiGraphicsLadsAdapter(GuiGraphics guiGraphics) {
        this(guiGraphics, Minecraft.getInstance().font);
    }

    public GuiGraphics getVanilla() {
        return guiGraphics;
    }

    @Override public boolean drawKillBanner(String skin, int variant, int x, int y, int width, int height) {
        return com.thelads.core.v1_21_11.feature.NativeKillBanner.drawThumb(guiGraphics, skin, variant, x, y, width, height);
    }
    @Override public void drawModIcon(String id, int x, int y, int size) {
        if (!com.thelads.core.v1_21_11.gui.ModIcons.draw(guiGraphics, id, x, y, size)) LadsGraphics.super.drawModIcon(id, x, y, size);
    }

    @Override public void drawBossBars(int x, int y, int max, boolean names, boolean preview) {
        var overlay = (com.thelads.core.v1_21_11.mixin.hud.BossBarAccessor) Minecraft.getInstance().gui.getBossOverlay();
        var events = new java.util.ArrayList<net.minecraft.world.BossEvent>(overlay.ladsEvents().values());
        if (events.isEmpty() && preview) events.add(new net.minecraft.client.gui.components.LerpingBossEvent(java.util.UUID.randomUUID(),
            net.minecraft.network.chat.Component.literal("Boss bar preview"), .65f, net.minecraft.world.BossEvent.BossBarColor.PURPLE,
            net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS, false, false, false));
        int row = 0;
        for (var event : events) {
            if (row >= max) break;
            int yy = y + row++ * 19;
            overlay.ladsDrawBar(guiGraphics, x, yy + 10, event);
            if (names) guiGraphics.drawString(font, event.getName(), x + (182 - font.width(event.getName())) / 2, yy, 0xFFFFFFFF);
        }
    }

    @Override public void drawArmorItem(int index, int x, int y, boolean preview) {
        var stack = armorStack(index, preview);
        if (!stack.isEmpty()) { guiGraphics.renderItem(stack, x, y); guiGraphics.renderItemDecorations(font, stack, x, y); }
    }

    /** The index-th equipped armor piece (feet first, as the bridge lists them), or a damaged diamond sample for previews. */
    static net.minecraft.world.item.ItemStack armorStack(int index, boolean preview) {
        if (preview) {
            var stack = new net.minecraft.world.item.ItemStack(index == 0 ? net.minecraft.world.item.Items.DIAMOND_HELMET : net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
            stack.setDamageValue(stack.getMaxDamage() / 4);
            return stack;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return net.minecraft.world.item.ItemStack.EMPTY;
        int visible = 0;
        for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.LEGS,
            net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.HEAD}) {
            var candidate = player.getItemBySlot(slot);
            if (!candidate.isEmpty() && visible++ == index) return candidate;
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
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
            // drawCenteredString always draws a shadow; the HUD's Shadow OFF must reach centered text too.
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
        guiGraphics.pose().pushMatrix();
    }

    @Override
    public void popPose() {
        guiGraphics.pose().popMatrix();
    }

    @Override
    public void translate(float x, float y) {
        guiGraphics.pose().translate(x, y);
    }

    @Override
    public void scale(float sx, float sy) {
        guiGraphics.pose().scale(sx, sy);
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
        Identifier loc = Identifier.tryParse(texture != null ? texture : "minecraft:textures/gui/icons.png");
        if (loc != null) {
            var gpuTexture = Minecraft.getInstance().getTextureManager().getTexture(loc).getTexture();
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, loc, x, y, u, v, width, height,
                gpuTexture.getWidth(0), gpuTexture.getHeight(0));
        }
    }

    @Override
    public void drawHead(String username, String uuid, int x, int y, int size) {
        try {
            Minecraft mc = Minecraft.getInstance();
            PlayerSkin skin = mc.getSkinManager().createLookup(new com.mojang.authlib.GameProfile(
                (uuid != null && !uuid.isBlank()) ? java.util.UUID.fromString(uuid) : java.util.UUID.randomUUID(),
                username != null ? username : "Player"
            ), false).get();
            if (skin != null) {
                PlayerFaceRenderer.draw(guiGraphics, skin, x, y, size);
            } else {
                fill(x, y, x + size, y + size, 0xFF6C63FF);
            }
        } catch (Exception e) {
            // Fallback square if skin lookup fails
            fill(x, y, x + size, y + size, 0xFF6C63FF);
        }
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
        return com.thelads.core.v1_21_11.embedded.hoveringhotbar.HoveringHotbar.hotbarLift();
    }
}
