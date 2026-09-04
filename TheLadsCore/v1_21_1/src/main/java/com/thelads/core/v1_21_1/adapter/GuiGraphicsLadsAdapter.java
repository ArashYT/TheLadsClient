package com.thelads.core.v1_21_1.adapter;

import com.thelads.core.client.bridge.LadsGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

public class GuiGraphicsLadsAdapter implements LadsGraphics {
    private final GuiGraphics guiGraphics;
    private final Font font;

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
            guiGraphics.drawCenteredString(font, text, centerX, y, color);
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
        ResourceLocation loc = ResourceLocation.tryParse(texture != null ? texture : "minecraft:textures/gui/icons.png");
        if (loc != null) {
            guiGraphics.blit(loc, x, y, u, v, width, height);
        }
    }

    @Override
    public void drawHead(String username, String uuid, int x, int y, int size) {
        try {
            Minecraft mc = Minecraft.getInstance();
            PlayerSkin skin = mc.getSkinManager().getInsecureSkin(new com.mojang.authlib.GameProfile(
                (uuid != null && !uuid.isBlank()) ? java.util.UUID.fromString(uuid) : java.util.UUID.randomUUID(),
                username != null ? username : "Player"
            ));
            PlayerFaceRenderer.draw(guiGraphics, skin.texture(), x, y, size);
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
}
