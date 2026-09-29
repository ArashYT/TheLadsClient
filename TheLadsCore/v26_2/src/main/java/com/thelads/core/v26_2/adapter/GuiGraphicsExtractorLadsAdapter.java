package com.thelads.core.v26_2.adapter;

import com.thelads.core.client.bridge.LadsGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

public class GuiGraphicsExtractorLadsAdapter implements LadsGraphics {
    private final GuiGraphicsExtractor g;
    private final Font font;

    public GuiGraphicsExtractorLadsAdapter(GuiGraphicsExtractor g, Font font) {
        this.g = g;
        this.font = (font != null) ? font : Minecraft.getInstance().font;
    }

    public GuiGraphicsExtractorLadsAdapter(GuiGraphicsExtractor g) {
        this(g, Minecraft.getInstance().font);
    }

    public GuiGraphicsExtractor getVanilla() {
        return g;
    }

    @Override public void drawPlayerModel(int x, int y, int width, int height, boolean editor) {
        com.thelads.core.v26_2.feature.paperdoll.NativePaperDoll.render(g, x, y, width, height, editor);
    }

    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        g.fill(minX, minY, maxX, maxY, color);
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        if (text != null && font != null) {
            g.text(font, text, x, y, color, shadow);
        }
    }

    @Override
    public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {
        if (text != null && font != null) {
            int tw = font.width(text);
            g.text(font, text, centerX - tw / 2, y, color, shadow);
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
        g.pose().pushMatrix();
    }

    @Override
    public void popPose() {
        g.pose().popMatrix();
    }

    @Override
    public void translate(float x, float y) {
        g.pose().translate(x, y);
    }

    @Override
    public void scale(float sx, float sy) {
        g.pose().scale(sx, sy);
    }

    @Override
    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        g.enableScissor(minX, minY, maxX, maxY);
    }

    @Override
    public void disableScissor() {
        g.disableScissor();
    }

    @Override
    public void blit(String texture, int x, int y, int u, int v, int width, int height) {
        Identifier loc = Identifier.tryParse(texture != null ? texture : "minecraft:textures/gui/icons.png");
        if (loc != null) {
            var gpuTexture = Minecraft.getInstance().getTextureManager().getTexture(loc).getTexture();
            g.blit(RenderPipelines.GUI_TEXTURED, loc, x, y, u, v, width, height,
                gpuTexture.getWidth(0), gpuTexture.getHeight(0));
        }
    }

    @Override
    public void drawHead(String username, String uuid, int x, int y, int size) {
        try {
            Minecraft mc = Minecraft.getInstance();
            PlayerSkin skin = mc.getSkinManager().createLookup(
                new com.mojang.authlib.GameProfile(
                    (uuid != null && !uuid.isBlank()) ? java.util.UUID.fromString(uuid) : java.util.UUID.randomUUID(),
                    username != null ? username : "Player"
                ),
                false
            ).get();
            if (skin != null) {
                PlayerFaceExtractor.extractRenderState(g, skin, x, y, size);
                return;
            }
        } catch (Exception ignored) {}
        fill(x, y, x + size, y + size, 0xFF6C63FF);
    }

    @Override
    public int getScaledWidth() {
        return g.guiWidth();
    }

    @Override
    public int getScaledHeight() {
        return g.guiHeight();
    }
}
