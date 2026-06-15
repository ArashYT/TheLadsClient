/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.NativeImage
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.client.renderer.texture.AbstractTexture
 *  net.minecraft.client.renderer.texture.DynamicTexture
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.Mth
 */
package com.thelads.core.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.gui.GalleryScreen;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class ImageViewerScreen
extends Screen {
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
        super((Component)Component.literal((String)"Image Viewer"));
        this.parent = parent;
        this.shots = shots;
        this.currentIndex = currentIndex;
    }

    private Identifier loadCurrentImage() {
        Identifier identifier;
        if (this.currentIndex < 0 || this.currentIndex >= this.shots.size()) {
            return null;
        }
        File f = this.shots.get(this.currentIndex);
        if (f.equals(this.currentFile) && this.currentTexture != null) {
            return this.currentTexture;
        }
        this.releaseCurrentTexture();
        this.currentFile = f;
        String key = f.getAbsolutePath();
        FileInputStream in = new FileInputStream(f);
        try {
            NativeImage img = NativeImage.read((InputStream)in);
            this.imgWidth = img.getWidth();
            this.imgHeight = img.getHeight();
            DynamicTexture tex = new DynamicTexture(() -> "lads_viewer", img);
            Identifier id = Identifier.fromNamespaceAndPath("thelads", "viewer/" + Integer.toHexString(key.hashCode() & Integer.MAX_VALUE) + "_" + (f.lastModified() & 0xFFFFFFL));
            Minecraft.getInstance().getTextureManager().register(id, (AbstractTexture)tex);
            this.currentTexture = id;
            identifier = id;
        }
        catch (Throwable throwable) {
            try {
                try {
                    ((InputStream)in).close();
                }
                catch (Throwable throwable2) {
                    throwable.addSuppressed(throwable2);
                }
                throw throwable;
            }
            catch (Throwable t) {
                this.currentTexture = null;
                return null;
            }
        }
        ((InputStream)in).close();
        return identifier;
    }

    private void releaseCurrentTexture() {
        if (this.currentTexture != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(this.currentTexture);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            this.currentTexture = null;
            this.currentFile = null;
        }
    }

    @Override
    public void removed() {
        this.releaseCurrentTexture();
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        boolean favHov;
        g.fill(0, 0, this.width, this.height, -15921898);
        Identifier texture = this.loadCurrentImage();
        if (texture != null) {
            float scale = Math.min((float)this.width / (float)this.imgWidth, (float)this.height / (float)this.imgHeight) * this.zoom;
            float w = (float)this.imgWidth * scale;
            float h = (float)this.imgHeight * scale;
            float x = ((float)this.width - w) / 2.0f + this.panX;
            float y = ((float)this.height - h) / 2.0f + this.panY;
            g.blit(RenderPipelines.GUI_TEXTURED, texture, (int)x, (int)y, 0.0f, 0.0f, (int)w, (int)h, (int)w, (int)h);
        } else {
            g.text(this.font, "\u00a7cFailed to load image", this.width / 2 - 50, this.height / 2 - 5, -21846, true);
        }
        g.fill(0, 0, this.width, 40, -871296738);
        if (this.currentIndex >= 0 && this.currentIndex < this.shots.size()) {
            File f = this.shots.get(this.currentIndex);
            String titleStr = "\u00a7l\u00a75Viewer \u00b7 \u00a7f" + f.getName() + " \u00a77(" + (this.currentIndex + 1) + "/" + this.shots.size() + ")";
            g.text(this.font, titleStr, 12, 14, -1, true);
        }
        boolean clHov = mouseX >= this.width - 40 && mouseX < this.width - 16 && mouseY >= 10 && mouseY < 30;
        g.fill(this.width - 40, 10, this.width - 16, 30, clHov ? -6737101 : -11197918);
        g.text(this.font, "\u00a7f\u2715", this.width - 31, 15, -1, false);
        boolean delHov = mouseX >= this.width - 95 && mouseX < this.width - 46 && mouseY >= 10 && mouseY < 30;
        g.fill(this.width - 95, 10, this.width - 46, 30, delHov ? -7851213 : -12312030);
        g.text(this.font, "\u00a7fDelete", this.width - 89, 15, -1, false);
        boolean isFav = false;
        if (this.currentIndex >= 0 && this.currentIndex < this.shots.size()) {
            isFav = GalleryScreen.loadFavorites().contains(this.shots.get(this.currentIndex).getName());
        }
        boolean bl = favHov = mouseX >= this.width - 165 && mouseX < this.width - 101 && mouseY >= 10 && mouseY < 30;
        g.fill(this.width - 165, 10, this.width - 101, 30, favHov ? -7829453 : (isFav ? -10066398 : -12303292));
        g.text(this.font, isFav ? "\u00a7e\u2605 Fav" : "\u00a77\u2606 Fav", this.width - 157, 15, -1, false);
        boolean copyHov = mouseX >= this.width - 235 && mouseX < this.width - 171 && mouseY >= 10 && mouseY < 30;
        g.fill(this.width - 235, 10, this.width - 171, 30, copyHov ? -12294486 : -14531448);
        g.text(this.font, "\u00a7fCopy", this.width - 215, 15, -1, false);
        String infoText = String.format("Zoom: %.1fx \u00b7 Pan: (%.0f, %.0f)", Float.valueOf(this.zoom), Float.valueOf(this.panX), Float.valueOf(this.panY));
        g.fill(this.width / 2 - 80, this.height - 24, this.width / 2 + 80, this.height - 4, -1442840576);
        g.text(this.font, infoText, this.width / 2 - 74, this.height - 18, -5592406, false);
        if (this.currentIndex > 0) {
            boolean prevHov = mouseX >= 10 && mouseX < 34 && mouseY >= this.height / 2 - 12 && mouseY < this.height / 2 + 12;
            g.fill(10, this.height / 2 - 12, 34, this.height / 2 + 12, prevHov ? -2008791979 : 0x44222233);
            g.text(this.font, "\u00a7f\u25c0", 17, this.height / 2 - 5, -1, false);
        }
        if (this.currentIndex < this.shots.size() - 1) {
            boolean nextHov = mouseX >= this.width - 34 && mouseX < this.width - 10 && mouseY >= this.height / 2 - 12 && mouseY < this.height / 2 + 12;
            g.fill(this.width - 34, this.height / 2 - 12, this.width - 10, this.height / 2 + 12, nextHov ? -2008791979 : 0x44222233);
            g.text(this.font, "\u00a7f\u25b6", this.width - 26, this.height / 2 - 5, -1, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.zoom = Mth.clamp((float)(this.zoom + (float)verticalAmount * 0.1f), (float)0.5f, (float)5.0f);
        return true;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0) {
            if (mx >= (double)(this.width - 40) && mx < (double)(this.width - 16) && my >= 10.0 && my < 30.0) {
                this.minecraft.setScreen(this.parent);
                return true;
            }
            if (mx >= (double)(this.width - 95) && mx < (double)(this.width - 46) && my >= 10.0 && my < 30.0) {
                this.deleteCurrent();
                return true;
            }
            if (mx >= (double)(this.width - 165) && mx < (double)(this.width - 101) && my >= 10.0 && my < 30.0) {
                this.toggleCurrentFavorite();
                return true;
            }
            if (mx >= (double)(this.width - 235) && mx < (double)(this.width - 171) && my >= 10.0 && my < 30.0) {
                if (this.currentIndex >= 0 && this.currentIndex < this.shots.size()) {
                    File f = this.shots.get(this.currentIndex);
                    try {
                        String safe = f.getAbsolutePath().replace("'", "''");
                        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; [System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Image]::FromFile('" + safe + "'))");
                        pb.start();
                    }
                    catch (Exception exception) {
                        // empty catch block
                    }
                }
                return true;
            }
            if (this.currentIndex > 0 && mx >= 10.0 && mx < 34.0 && my >= (double)(this.height / 2 - 12) && my < (double)(this.height / 2 + 12)) {
                this.navigatePrev();
                return true;
            }
            if (this.currentIndex < this.shots.size() - 1 && mx >= (double)(this.width - 34) && mx < (double)(this.width - 10) && my >= (double)(this.height / 2 - 12) && my < (double)(this.height / 2 + 12)) {
                this.navigateNext();
                return true;
            }
            this.isDragging = true;
            this.lastDragX = mx;
            this.lastDragY = my;
            return true;
        }
        return super.mouseClicked(event, isDouble);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.isDragging && event.button() == 0) {
            double dx = event.x() - this.lastDragX;
            double dy = event.y() - this.lastDragY;
            this.panX += (float)dx;
            this.panY += (float)dy;
            float limitX = (float)this.width * this.zoom;
            float limitY = (float)this.height * this.zoom;
            this.panX = Mth.clamp((float)this.panX, (float)(-limitX), (float)limitX);
            this.panY = Mth.clamp((float)this.panY, (float)(-limitY), (float)limitY);
            this.lastDragX = event.x();
            this.lastDragY = event.y();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            this.isDragging = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 256) {
            this.minecraft.setScreen(this.parent);
            return true;
        }
        if (key == 262) {
            this.navigateNext();
            return true;
        }
        if (key == 263) {
            this.navigatePrev();
            return true;
        }
        return super.keyPressed(event);
    }

    private void navigateNext() {
        if (this.currentIndex < this.shots.size() - 1) {
            ++this.currentIndex;
            this.zoom = 1.0f;
            this.panX = 0.0f;
            this.panY = 0.0f;
            this.releaseCurrentTexture();
            this.loadCurrentImage();
        }
    }

    private void navigatePrev() {
        if (this.currentIndex > 0) {
            --this.currentIndex;
            this.zoom = 1.0f;
            this.panX = 0.0f;
            this.panY = 0.0f;
            this.releaseCurrentTexture();
            this.loadCurrentImage();
        }
    }

    private void deleteCurrent() {
        File jsonFile;
        if (this.currentIndex < 0 || this.currentIndex >= this.shots.size()) {
            return;
        }
        File f = this.shots.get(this.currentIndex);
        if (f.exists()) {
            f.delete();
        }
        if ((jsonFile = new File(f.getParentFile(), f.getName() + ".json")).exists()) {
            jsonFile.delete();
        }
        String name = f.getName();
        Set<String> favorites = GalleryScreen.loadFavorites();
        if (favorites.remove(name)) {
            GalleryScreen.saveFavorites(favorites);
        }
        this.shots.remove(this.currentIndex);
        if (this.shots.isEmpty()) {
            this.releaseCurrentTexture();
            this.minecraft.setScreen(this.parent);
        } else {
            if (this.currentIndex >= this.shots.size()) {
                this.currentIndex = this.shots.size() - 1;
            }
            this.zoom = 1.0f;
            this.panX = 0.0f;
            this.panY = 0.0f;
            this.releaseCurrentTexture();
            this.loadCurrentImage();
        }
    }

    private void toggleCurrentFavorite() {
        if (this.currentIndex < 0 || this.currentIndex >= this.shots.size()) {
            return;
        }
        String name = this.shots.get(this.currentIndex).getName();
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

