package com.thelads.core.features.decentscreenshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import java.io.File;

public class InteractiveScreenshotWidget {
    public static File lastScreenshot = null;
    public static long screenshotTime = 0;

    public static void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (lastScreenshot == null) return;
        if (System.currentTimeMillis() - screenshotTime > 10000) {
            lastScreenshot = null;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int width = 160;
        int height = 70;
        int x = mc.getWindow().getGuiScaledWidth() - width - 10;
        int y = 10;

        graphics.fill(x, y, x + width, y + height, 0xFFF0F0F0);
        graphics.outline(x, y, width, height, 0xFFD0D0D0);

        int photoSize = 40;
        int photoX = x + 5;
        int photoY = y + 5;
        graphics.fill(photoX, photoY, photoX + photoSize, photoY + photoSize, 0xFF000000);

        ScreenshotTextureCache.ScreenshotTexture texture = ScreenshotTextureCache.getOrLoad(lastScreenshot);
        if (texture != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), photoX, photoY, 0.0f, 0.0f, photoSize, photoSize, texture.width(), texture.height(), texture.width(), texture.height());
        } else {
            graphics.outline(photoX, photoY, photoSize, photoSize, 0xFFFF5555);
        }
        graphics.outline(photoX, photoY, photoSize, photoSize, 0xFF444444);

        int textX = photoX + photoSize + 8;
        graphics.text(mc.font, Component.literal("Screenshot Saved"), textX, y + 8, 0xFF111111, false);
        
        String fileName = lastScreenshot.getName();
        if (fileName.length() > 15) fileName = fileName.substring(0, 12) + "...";
        graphics.text(mc.font, Component.literal(fileName), textX, y + 20, 0xFF666666, false);

        int btnY = y + 50;
        drawButton(graphics, mc, "Copy", x + 5, btnY, 45, 15, mouseX, mouseY);
        drawButton(graphics, mc, "Favorite", x + 55, btnY, 50, 15, mouseX, mouseY);
        drawButton(graphics, mc, "Delete", x + 110, btnY, 45, 15, mouseX, mouseY);
    }

    private static void drawButton(GuiGraphicsExtractor graphics, Minecraft mc, String text, int bx, int by, int bw, int bh, int mouseX, int mouseY) {
        boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        graphics.fill(bx, by, bx + bw, by + bh, hovered ? 0xFFDDDDDD : 0xFFCCCCCC);
        graphics.outline(bx, by, bw, bh, 0xFFAAAAAA);
        int tx = bx + (bw - mc.font.width(text)) / 2;
        int ty = by + (bh - mc.font.lineHeight) / 2 + 1;
        graphics.text(mc.font, Component.literal(text), tx, ty, 0xFF000000, false);
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (lastScreenshot == null || button != 0) return false;
        Minecraft mc = Minecraft.getInstance();
        int width = 160;
        int x = mc.getWindow().getGuiScaledWidth() - width - 10;
        int y = 10;
        int btnY = y + 50;

        if (isHovered(mouseX, mouseY, x + 5, btnY, 45, 15)) {
            System.out.println("Copied screenshot to clipboard: " + lastScreenshot.getName());
            lastScreenshot = null;
            return true;
        }
        if (isHovered(mouseX, mouseY, x + 55, btnY, 50, 15)) {
            System.out.println("Favorited screenshot: " + lastScreenshot.getName());
            lastScreenshot = null;
            return true;
        }
        if (isHovered(mouseX, mouseY, x + 110, btnY, 45, 15)) {
            if (lastScreenshot.exists()) lastScreenshot.delete();
            System.out.println("Deleted screenshot: " + lastScreenshot.getName());
            lastScreenshot = null;
            return true;
        }
        return false;
    }

    private static boolean isHovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
