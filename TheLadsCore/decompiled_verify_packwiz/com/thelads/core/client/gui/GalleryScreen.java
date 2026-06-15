/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonObject
 *  com.mojang.blaze3d.platform.NativeImage
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.client.renderer.texture.AbstractTexture
 *  net.minecraft.client.renderer.texture.DynamicTexture
 *  net.minecraft.client.renderer.texture.TextureManager
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.Mth
 *  net.minecraft.util.Util
 */
package com.thelads.core.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.gui.ImageViewerScreen;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

public class GalleryScreen
extends Screen {
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private final Screen parent;
    private final List<File> shots = new ArrayList<File>();
    private final Map<String, Identifier> thumbs = new HashMap<String, Identifier>();
    private final Set<String> failed = new HashSet<String>();
    private double scrollOffset = 0.0;
    private int maxScroll = 0;
    private static final int ROW_H = 34;
    private static final int GAP = 4;
    private static final int LIST_TOP = 48;

    public GalleryScreen(Screen parent) {
        super((Component)Component.literal((String)"Gallery"));
        this.parent = parent;
    }

    public static File getFavoritesFile() {
        try {
            return FabricLoader.getInstance().getGameDir().resolve("config/gallery_favorites.json").toFile();
        }
        catch (Throwable t) {
            return new File("config/gallery_favorites.json");
        }
    }

    public static Set<String> loadFavorites() {
        HashSet<String> favorites = new HashSet<String>();
        File file = GalleryScreen.getFavoritesFile();
        if (!file.exists()) {
            return favorites;
        }
        try (FileReader reader = new FileReader(file);){
            Gson gson = new Gson();
            String[] arr = (String[])gson.fromJson((Reader)reader, String[].class);
            if (arr != null) {
                favorites.addAll(Arrays.asList(arr));
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return favorites;
    }

    public static void saveFavorites(Set<String> favorites) {
        File file = GalleryScreen.getFavoritesFile();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8);){
                new Gson().toJson(new ArrayList<String>(favorites), (Appendable)writer);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public File screenshotsDir() {
        try {
            return FabricLoader.getInstance().getGameDir().resolve("screenshots").toFile();
        }
        catch (Throwable t) {
            return new File("screenshots");
        }
    }

    @Override
    protected void init() {
        File[] files;
        this.scrollOffset = 0.0;
        this.shots.clear();
        File dir = this.screenshotsDir();
        if (dir.isDirectory() && (files = dir.listFiles((d, n) -> {
            String l = n.toLowerCase();
            return l.endsWith(".png") || l.endsWith(".jpg");
        })) != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            this.shots.addAll(Arrays.asList(files));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, -300937184);
        g.text(this.font, "\u00a7l\u00a75Gallery", 20, 14, -1, true);
        g.text(this.font, "\u00a77" + this.shots.size() + " screenshots \u00b7 click to open", 20, 28, -5592406, false);
        boolean ofHov = mouseX >= this.width - 130 && mouseX < this.width - 46 && mouseY >= 12 && mouseY < 32;
        g.fill(this.width - 130, 12, this.width - 46, 32, ofHov ? -11184777 : -12303258);
        g.text(this.font, "\u00a7fOpen Folder", this.width - 124, 18, -1, false);
        boolean clHov = mouseX >= this.width - 40 && mouseX < this.width - 16 && mouseY >= 12 && mouseY < 32;
        g.fill(this.width - 40, 12, this.width - 16, 32, clHov ? -8573394 : -11197918);
        g.text(this.font, "\u00a7f\u2715", this.width - 31, 18, -1, false);
        int rowStride = 38;
        this.maxScroll = Math.max(0, this.shots.size() * rowStride - (this.height - 48 - 12));
        int y = 48 - (int)this.scrollOffset;
        Set<String> favs = GalleryScreen.loadFavorites();
        for (File f : this.shots) {
            if (y + 34 >= 48 && y < this.height - 10) {
                boolean rowFavHov;
                boolean hov = mouseX >= 16 && mouseX < this.width - 16 && mouseY >= y && mouseY < y + 34;
                g.fill(16, y, this.width - 16, y + 34, hov ? -869980864 : -870704594);
                g.fill(16, y + 4, 19, y + 34 - 4, -9673729);
                int thW = 50;
                int thH = 26;
                Identifier thumb = this.loadThumb(f);
                if (thumb != null) {
                    g.blit(RenderPipelines.GUI_TEXTURED, thumb, 24, y + 4, 0.0f, 0.0f, thW, thH, thW, thH);
                }
                int textX = 24 + thW + 8;
                boolean isFav = favs.contains(f.getName());
                String nameStr = (isFav ? "\u00a7e\u2605 \u00a7f" : "\u00a7f") + f.getName();
                g.text(this.font, nameStr, textX, y + 5, -1, true);
                String meta = this.readMeta(f);
                String when = FMT.format(new Date(f.lastModified()));
                String line = meta.isEmpty() ? when : when + "  \u00b7  " + meta;
                g.text(this.font, "\u00a77" + line, textX, y + 19, -7829368, false);
                boolean rowCopyHov = mouseX >= this.width - 125 && mouseX < this.width - 85 && mouseY >= y + 6 && mouseY < y + 34 - 6;
                g.fill(this.width - 125, y + 6, this.width - 85, y + 34 - 6, rowCopyHov ? -12294486 : -14531448);
                g.text(this.font, "Copy", this.width - 116, y + 13, -1, false);
                boolean bl = rowFavHov = mouseX >= this.width - 80 && mouseX < this.width - 50 && mouseY >= y + 6 && mouseY < y + 34 - 6;
                g.fill(this.width - 80, y + 6, this.width - 50, y + 34 - 6, rowFavHov ? -7829453 : (isFav ? -10066398 : -12303292));
                g.text(this.font, isFav ? "\u2605" : "\u2606", this.width - 69, y + 13, -1, false);
                boolean rowDelHov = mouseX >= this.width - 45 && mouseX < this.width - 15 && mouseY >= y + 6 && mouseY < y + 34 - 6;
                g.fill(this.width - 45, y + 6, this.width - 15, y + 34 - 6, rowDelHov ? -6737101 : -11197918);
                g.text(this.font, "\u2715", this.width - 34, y + 13, -1, false);
            }
            y += rowStride;
        }
        if (this.shots.isEmpty()) {
            g.text(this.font, "\u00a78No screenshots yet \u2014 press F2 in game.", 20, 54, -10066330, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private String readMeta(File png) {
        File meta = new File(png.getParentFile(), png.getName() + ".json");
        if (!meta.isFile()) {
            return "";
        }
        try (FileReader r = new FileReader(meta);){
            JsonObject j = (JsonObject)new Gson().fromJson((Reader)r, JsonObject.class);
            if (j == null) {
                String string = "";
                return string;
            }
            StringBuilder sb = new StringBuilder();
            if (j.has("server")) {
                sb.append(j.get("server").getAsString());
            } else if (j.has("world")) {
                sb.append(j.get("world").getAsString());
            }
            if (j.has("x") && j.has("z")) {
                if (sb.length() > 0) {
                    sb.append(" \u00b7 ");
                }
                sb.append(j.get("x").getAsInt()).append(", ").append(j.get("z").getAsInt());
            }
            if (j.has("biome")) {
                if (sb.length() > 0) {
                    sb.append(" \u00b7 ");
                }
                sb.append(j.get("biome").getAsString().replace("minecraft:", ""));
            }
            if (j.has("seed")) {
                if (sb.length() > 0) {
                    sb.append(" \u00b7 ");
                }
                sb.append("seed ").append(j.get("seed").getAsLong());
            }
            String string = sb.toString();
            return string;
        }
        catch (Exception e) {
            return "";
        }
    }

    private Identifier loadThumb(File f) {
        Identifier identifier;
        String key = f.getAbsolutePath();
        Identifier existing = this.thumbs.get(key);
        if (existing != null) {
            return existing;
        }
        if (this.failed.contains(key) || this.thumbs.size() >= 60) {
            return null;
        }
        FileInputStream in = new FileInputStream(f);
        try {
            NativeImage img = NativeImage.read((InputStream)in);
            DynamicTexture tex = new DynamicTexture(() -> "lads_gallery", img);
            Identifier id = Identifier.fromNamespaceAndPath("thelads", "gallery/" + Integer.toHexString(key.hashCode() & Integer.MAX_VALUE) + "_" + (f.lastModified() & 0xFFFFFFL));
            Minecraft.getInstance().getTextureManager().register(id, (AbstractTexture)tex);
            this.thumbs.put(key, id);
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
                this.failed.add(key);
                return null;
            }
        }
        ((InputStream)in).close();
        return identifier;
    }

    @Override
    public void removed() {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        for (Identifier id : this.thumbs.values()) {
            try {
                tm.release(id);
            }
            catch (Throwable throwable) {}
        }
        this.thumbs.clear();
        this.failed.clear();
        super.removed();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollOffset = Mth.clamp((double)(this.scrollOffset - verticalAmount * 24.0), (double)0.0, (double)this.maxScroll);
        return true;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0) {
            if (mx >= (double)(this.width - 40) && mx < (double)(this.width - 16) && my >= 12.0 && my < 32.0) {
                this.minecraft.setScreen(this.parent);
                return true;
            }
            if (mx >= (double)(this.width - 130) && mx < (double)(this.width - 46) && my >= 12.0 && my < 32.0) {
                Util.getPlatform().openPath(this.screenshotsDir().toPath());
                return true;
            }
            int rowStride = 38;
            int y = 48 - (int)this.scrollOffset;
            for (File f : new ArrayList<File>(this.shots)) {
                if (my >= (double)y && my < (double)(y + 34) && mx >= 16.0 && mx < (double)(this.width - 16)) {
                    if (mx >= (double)(this.width - 125) && mx < (double)(this.width - 85) && my >= (double)(y + 6) && my < (double)(y + 34 - 6)) {
                        try {
                            String safe = f.getAbsolutePath().replace("'", "''");
                            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; [System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Image]::FromFile('" + safe + "'))");
                            pb.start();
                        }
                        catch (Exception safe) {
                            // empty catch block
                        }
                        return true;
                    }
                    if (mx >= (double)(this.width - 80) && mx < (double)(this.width - 50) && my >= (double)(y + 6) && my < (double)(y + 34 - 6)) {
                        Set<String> favorites = GalleryScreen.loadFavorites();
                        if (favorites.contains(f.getName())) {
                            favorites.remove(f.getName());
                        } else {
                            favorites.add(f.getName());
                        }
                        GalleryScreen.saveFavorites(favorites);
                        return true;
                    }
                    if (mx >= (double)(this.width - 45) && mx < (double)(this.width - 15) && my >= (double)(y + 6) && my < (double)(y + 34 - 6)) {
                        Set<String> favorites;
                        File jsonFile;
                        if (f.exists()) {
                            f.delete();
                        }
                        if ((jsonFile = new File(f.getParentFile(), f.getName() + ".json")).exists()) {
                            jsonFile.delete();
                        }
                        if ((favorites = GalleryScreen.loadFavorites()).remove(f.getName())) {
                            GalleryScreen.saveFavorites(favorites);
                        }
                        this.shots.remove(f);
                        return true;
                    }
                    this.minecraft.setScreen((Screen)new ImageViewerScreen(this, this.shots, this.shots.indexOf(f)));
                    return true;
                }
                y += rowStride;
            }
        }
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            this.minecraft.setScreen(this.parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

