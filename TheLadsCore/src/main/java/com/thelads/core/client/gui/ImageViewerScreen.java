package com.thelads.core.client.gui;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Mth;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Interactive full-screen image viewer for screenshots.
 * Supports zoom (scroll wheel), pan (mouse drag), navigation (Left/Right arrow keys or UI buttons),
 * delete screenshot (remove from disk/sidecar/list/favorites), and favorite toggling.
 */
public class ImageViewerScreen extends Screen {
    private final Screen parent;
    private final List<File> shots;
    private int currentIndex;

    private float zoom = 1.0f;
    private float panX = 0.0f;
    private float panY = 0.0f;

    private boolean isDragging = false;
    private double lastDragX = 0.0;
    private double lastDragY = 0.0;

    private Identifier currentTexture = null;
    private File currentFile = null;
    private int imgWidth = 1920;
    private int imgHeight = 1080;

    public ImageViewerScreen(Screen parent, List<File> shots, int currentIndex) {
        super(Component.literal("Image Viewer"));
        this.parent = parent;
        this.shots = shots;
        this.currentIndex = currentIndex;
    }

    private Identifier loadCurrentImage() {
        if (currentIndex < 0 || currentIndex >= shots.size()) {
            return null;
        }
        File f = shots.get(currentIndex);
        if (f.equals(currentFile) && currentTexture != null) {
            return currentTexture;
        }
        releaseCurrentTexture();

        currentFile = f;
        String key = f.getAbsolutePath();
        try (InputStream in = new FileInputStream(f)) {
            NativeImage img = NativeImage.read(in);
            imgWidth = img.getWidth();
            imgHeight = img.getHeight();
            DynamicTexture tex = new DynamicTexture(() -> "lads_viewer", img);
            Identifier id = Identifier.fromNamespaceAndPath("thelads",
                "viewer/" + Integer.toHexString(key.hashCode() & 0x7fffffff) + "_" + (f.lastModified() & 0xffffffL));
            Minecraft.getInstance().getTextureManager().register(id, tex);
            currentTexture = id;
            return id;
        } catch (Throwable t) {
            currentTexture = null;
            return null;
        }
    }

    private void releaseCurrentTexture() {
        if (currentTexture != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(currentTexture);
            } catch (Throwable ignored) {}
            currentTexture = null;
            currentFile = null;
        }
    }

    @Override
    public void removed() {
        releaseCurrentTexture();
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Draw solid dark background
        g.fill(0, 0, this.width, this.height, 0xFF0D0D16);

        Identifier texture = loadCurrentImage();
        if (texture != null) {
            float scale = Math.min((float) this.width / imgWidth, (float) this.height / imgHeight) * zoom;
            float w = imgWidth * scale;
            float h = imgHeight * scale;
            float x = (this.width - w) / 2.0f + panX;
            float y = (this.height - h) / 2.0f + panY;
            g.blit(RenderPipelines.GUI_TEXTURED, texture, (int) x, (int) y, 0f, 0f, (int) w, (int) h, (int) w, (int) h);
        } else {
            g.text(this.font, "§cFailed to load image", this.width / 2 - 50, this.height / 2 - 5, 0xFFFFAAAA, true);
        }

        // Draw top control panel bar background
        g.fill(0, 0, this.width, 40, 0xCC11111E);

        // Header info
        if (currentIndex >= 0 && currentIndex < shots.size()) {
            File f = shots.get(currentIndex);
            String titleStr = "§l§5Viewer · §f" + f.getName() + " §7(" + (currentIndex + 1) + "/" + shots.size() + ")";
            g.text(this.font, titleStr, 12, 14, 0xFFFFFFFF, true);
        }

        // UI Buttons: Back, Delete, Favorite
        // Close (✕) button
        boolean clHov = mouseX >= this.width - 40 && mouseX < this.width - 16 && mouseY >= 10 && mouseY < 30;
        g.fill(this.width - 40, 10, this.width - 16, 30, clHov ? 0xFF993333 : 0xFF552222);
        g.text(this.font, "§f✕", this.width - 31, 15, 0xFFFFFFFF, false);

        // Delete button
        boolean delHov = mouseX >= this.width - 95 && mouseX < this.width - 46 && mouseY >= 10 && mouseY < 30;
        g.fill(this.width - 95, 10, this.width - 46, 30, delHov ? 0xFF883333 : 0xFF442222);
        g.text(this.font, "§fDelete", this.width - 89, 15, 0xFFFFFFFF, false);

        // Favorite button
        boolean isFav = false;
        if (currentIndex >= 0 && currentIndex < shots.size()) {
            isFav = GalleryScreen.loadFavorites().contains(shots.get(currentIndex).getName());
        }
        boolean favHov = mouseX >= this.width - 165 && mouseX < this.width - 101 && mouseY >= 10 && mouseY < 30;
        g.fill(this.width - 165, 10, this.width - 101, 30, favHov ? 0xFF888833 : (isFav ? 0xFF666622 : 0xFF444444));
        g.text(this.font, isFav ? "§e★ Fav" : "§7☆ Fav", this.width - 157, 15, 0xFFFFFFFF, false);

        // Copy button
        boolean copyHov = mouseX >= this.width - 235 && mouseX < this.width - 171 && mouseY >= 10 && mouseY < 30;
        g.fill(this.width - 235, 10, this.width - 171, 30, copyHov ? 0xFF4466AA : 0xFF224488);
        g.text(this.font, "§fCopy", this.width - 215, 15, 0xFFFFFFFF, false);

        // Zoom / Pan status info at bottom center
        String infoText = String.format("Zoom: %.1fx · Pan: (%.0f, %.0f)", zoom, panX, panY);
        g.fill(this.width / 2 - 80, this.height - 24, this.width / 2 + 80, this.height - 4, 0xAA000000);
        g.text(this.font, infoText, this.width / 2 - 74, this.height - 18, 0xFFAAAAAA, false);

        // Left / Right Navigation overlays (drawn as small floating buttons at screen edges)
        if (currentIndex > 0) {
            boolean prevHov = mouseX >= 10 && mouseX < 34 && mouseY >= this.height / 2 - 12 && mouseY < this.height / 2 + 12;
            g.fill(10, this.height / 2 - 12, 34, this.height / 2 + 12, prevHov ? 0x88444455 : 0x44222233);
            g.text(this.font, "§f◀", 17, this.height / 2 - 5, 0xFFFFFFFF, false);
        }
        if (currentIndex < shots.size() - 1) {
            boolean nextHov = mouseX >= this.width - 34 && mouseX < this.width - 10 && mouseY >= this.height / 2 - 12 && mouseY < this.height / 2 + 12;
            g.fill(this.width - 34, this.height / 2 - 12, this.width - 10, this.height / 2 + 12, nextHov ? 0x88444455 : 0x44222233);
            g.text(this.font, "§f▶", this.width - 26, this.height / 2 - 5, 0xFFFFFFFF, false);
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        zoom = Mth.clamp(zoom + (float) verticalAmount * 0.1f, 0.5f, 5.0f);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        double mx = event.x();
        double my = event.y();

        if (event.button() == 0) {
            // Close button click
            if (mx >= this.width - 40 && mx < this.width - 16 && my >= 10 && my < 30) {
                this.minecraft.setScreenAndShow(parent);
                return true;
            }
            // Delete button click
            if (mx >= this.width - 95 && mx < this.width - 46 && my >= 10 && my < 30) {
                deleteCurrent();
                return true;
            }
            // Favorite button click
            if (mx >= this.width - 165 && mx < this.width - 101 && my >= 10 && my < 30) {
                toggleCurrentFavorite();
                return true;
            }
            // Copy button click
            if (mx >= this.width - 235 && mx < this.width - 171 && my >= 10 && my < 30) {
                if (currentIndex >= 0 && currentIndex < shots.size()) {
                    File f = shots.get(currentIndex);
                    try {
                        String safe = f.getAbsolutePath().replace("'", "''");
                        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; [System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Image]::FromFile('" + safe + "'))");
                        pb.start();
                    } catch (Exception ignored) {}
                }
                return true;
            }
            // Left arrow UI click
            if (currentIndex > 0 && mx >= 10 && mx < 34 && my >= this.height / 2 - 12 && my < this.height / 2 + 12) {
                navigatePrev();
                return true;
            }
            // Right arrow UI click
            if (currentIndex < shots.size() - 1 && mx >= this.width - 34 && mx < this.width - 10 && my >= this.height / 2 - 12 && my < this.height / 2 + 12) {
                navigateNext();
                return true;
            }

            // Otherwise start dragging for pan
            isDragging = true;
            lastDragX = mx;
            lastDragY = my;
            return true;
        }

        return super.mouseClicked(event, isDouble);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (isDragging && event.button() == 0) {
            double dx = event.x() - lastDragX;
            double dy = event.y() - lastDragY;
            panX += (float) dx;
            panY += (float) dy;

            // Clamping pan offsets to prevent screen panning loss
            float limitX = this.width * zoom;
            float limitY = this.height * zoom;
            panX = Mth.clamp(panX, -limitX, limitX);
            panY = Mth.clamp(panY, -limitY, limitY);

            lastDragX = event.x();
            lastDragY = event.y();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            isDragging = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 256) { // Escape key
            this.minecraft.setScreenAndShow(parent);
            return true;
        }
        if (key == 262) { // Right Arrow key
            navigateNext();
            return true;
        }
        if (key == 263) { // Left Arrow key
            navigatePrev();
            return true;
        }
        return super.keyPressed(event);
    }

    private void navigateNext() {
        if (currentIndex < shots.size() - 1) {
            currentIndex++;
            zoom = 1.0f;
            panX = 0.0f;
            panY = 0.0f;
            releaseCurrentTexture();
            loadCurrentImage();
        }
    }

    private void navigatePrev() {
        if (currentIndex > 0) {
            currentIndex--;
            zoom = 1.0f;
            panX = 0.0f;
            panY = 0.0f;
            releaseCurrentTexture();
            loadCurrentImage();
        }
    }

    private void deleteCurrent() {
        if (currentIndex < 0 || currentIndex >= shots.size()) {
            return;
        }
        File f = shots.get(currentIndex);
        if (f.exists()) {
            f.delete();
        }
        // Delete sidecar json
        File jsonFile = new File(f.getParentFile(), f.getName() + ".json");
        if (jsonFile.exists()) {
            jsonFile.delete();
        }
        // Remove from favorites
        String name = f.getName();
        Set<String> favorites = GalleryScreen.loadFavorites();
        if (favorites.remove(name)) {
            GalleryScreen.saveFavorites(favorites);
        }
        // Remove from list
        shots.remove(currentIndex);

        if (shots.isEmpty()) {
            releaseCurrentTexture();
            this.minecraft.setScreenAndShow(parent);
        } else {
            if (currentIndex >= shots.size()) {
                currentIndex = shots.size() - 1;
            }
            zoom = 1.0f;
            panX = 0.0f;
            panY = 0.0f;
            releaseCurrentTexture();
            loadCurrentImage();
        }
    }

    private void toggleCurrentFavorite() {
        if (currentIndex < 0 || currentIndex >= shots.size()) {
            return;
        }
        String name = shots.get(currentIndex).getName();
        Set<String> favorites = GalleryScreen.loadFavorites();
        if (favorites.contains(name)) {
            favorites.remove(name);
        } else {
            favorites.add(name);
        }
        GalleryScreen.saveFavorites(favorites);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
