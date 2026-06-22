package com.thelads.core.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
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

/** In-game screenshot gallery (opened from the pause menu). Lists shots with their
 *  captured metadata; clicking a row opens the image in the OS viewer. */
public class GalleryScreen extends Screen {
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private final Screen parent;
    private final List<File> shots = new ArrayList<>();
    private final Map<String, Identifier> thumbs = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private double scrollOffset = 0;
    private int maxScroll = 0;

    private static final int ROW_H = 34;
    private static final int GAP = 4;
    private static final int LIST_TOP = 48;

    public GalleryScreen(Screen parent) {
        super(Component.literal("Gallery"));
        this.parent = parent;
    }

    public static File getFavoritesFile() {
        try {
            return FabricLoader.getInstance().getGameDir().resolve("config/gallery_favorites.json").toFile();
        } catch (Throwable t) {
            return new File("config/gallery_favorites.json");
        }
    }

    public static Set<String> loadFavorites() {
        Set<String> favorites = new HashSet<>();
        File file = getFavoritesFile();
        if (!file.exists()) {
            return favorites;
        }
        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            String[] arr = gson.fromJson(reader, String[].class);
            if (arr != null) {
                favorites.addAll(Arrays.asList(arr));
            }
        } catch (Exception e) {
            // Malformed/empty file, return empty
        }
        return favorites;
    }

    public static void saveFavorites(Set<String> favorites) {
        File file = getFavoritesFile();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (java.io.FileWriter writer = new java.io.FileWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                new Gson().toJson(new ArrayList<>(favorites), writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public File screenshotsDir() {
        try {
            return FabricLoader.getInstance().getGameDir().resolve("screenshots").toFile();
        } catch (Throwable t) {
            return new File("screenshots");
        }
    }

    @Override
    protected void init() {
        scrollOffset = 0;
        shots.clear();
        File dir = screenshotsDir();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles((d, n) -> {
                String l = n.toLowerCase();
                return l.endsWith(".png") || l.endsWith(".jpg");
            });
            if (files != null) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
                shots.addAll(Arrays.asList(files));
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xEE101020);
        g.text(this.font, "§l§5Gallery", 20, 14, 0xFFFFFFFF, true);
        g.text(this.font, "§7" + shots.size() + " screenshots · click to open", 20, 28, 0xFFAAAAAA, false);

        // Open folder + close buttons (top-right)
        boolean ofHov = mouseX >= this.width - 130 && mouseX < this.width - 46 && mouseY >= 12 && mouseY < 32;
        g.fill(this.width - 130, 12, this.width - 46, 32, ofHov ? 0xFF555577 : 0xFF444466);
        g.text(this.font, "§fOpen Folder", this.width - 124, 18, 0xFFFFFFFF, false);
        boolean clHov = mouseX >= this.width - 40 && mouseX < this.width - 16 && mouseY >= 12 && mouseY < 32;
        g.fill(this.width - 40, 12, this.width - 16, 32, clHov ? 0xFF7D2E2E : 0xFF552222);
        g.text(this.font, "§f✕", this.width - 31, 18, 0xFFFFFFFF, false);

        int rowStride = ROW_H + GAP;
        maxScroll = Math.max(0, shots.size() * rowStride - (this.height - LIST_TOP - 12));
        int y = LIST_TOP - (int) scrollOffset;
        Set<String> favs = loadFavorites();
        for (File f : shots) {
            if (y + ROW_H >= LIST_TOP && y < this.height - 10) {
                boolean hov = mouseX >= 16 && mouseX < this.width - 16 && mouseY >= y && mouseY < y + ROW_H;
                g.fill(16, y, this.width - 16, y + ROW_H, hov ? 0xCC252540 : 0xCC1A1A2E);
                g.fill(16, y + 4, 19, y + ROW_H - 4, 0xFF6C63FF);
                int thW = 50, thH = ROW_H - 8;
                Identifier thumb = loadThumb(f);
                if (thumb != null) {
                    g.blit(RenderPipelines.GUI_TEXTURED, thumb, 24, y + 4, 0f, 0f, thW, thH, thW, thH);
                }
                int textX = 24 + thW + 8;
                boolean isFav = favs.contains(f.getName());
                String nameStr = (isFav ? "§e★ §f" : "§f") + f.getName();
                g.text(this.font, nameStr, textX, y + 5, 0xFFFFFFFF, true);
                String meta = readMeta(f);
                String when = FMT.format(new Date(f.lastModified()));
                String line = meta.isEmpty() ? when : (when + "  ·  " + meta);
                g.text(this.font, "§7" + line, textX, y + 19, 0xFF888888, false);

                // Row action buttons: Copy, Fav, Del
                boolean rowCopyHov = mouseX >= this.width - 125 && mouseX < this.width - 85 && mouseY >= y + 6 && mouseY < y + ROW_H - 6;
                g.fill(this.width - 125, y + 6, this.width - 85, y + ROW_H - 6, rowCopyHov ? 0xFF4466AA : 0xFF224488);
                g.text(this.font, "Copy", this.width - 116, y + 13, 0xFFFFFFFF, false);

                boolean rowFavHov = mouseX >= this.width - 80 && mouseX < this.width - 50 && mouseY >= y + 6 && mouseY < y + ROW_H - 6;
                g.fill(this.width - 80, y + 6, this.width - 50, y + ROW_H - 6, rowFavHov ? 0xFF888833 : (isFav ? 0xFF666622 : 0xFF444444));
                g.text(this.font, isFav ? "★" : "☆", this.width - 69, y + 13, 0xFFFFFFFF, false);

                boolean rowDelHov = mouseX >= this.width - 45 && mouseX < this.width - 15 && mouseY >= y + 6 && mouseY < y + ROW_H - 6;
                g.fill(this.width - 45, y + 6, this.width - 15, y + ROW_H - 6, rowDelHov ? 0xFF993333 : 0xFF552222);
                g.text(this.font, "✕", this.width - 34, y + 13, 0xFFFFFFFF, false);
            }
            y += rowStride;
        }
        if (shots.isEmpty()) {
            g.text(this.font, "§8No screenshots yet — press F2 in game.", 20, LIST_TOP + 6, 0xFF666666, false);
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private String readMeta(File png) {
        File meta = new File(png.getParentFile(), png.getName() + ".json");
        if (!meta.isFile()) {
            return "";
        }
        try (FileReader r = new FileReader(meta)) {
            JsonObject j = new Gson().fromJson(r, JsonObject.class);
            if (j == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (j.has("server")) {
                sb.append(j.get("server").getAsString());
            } else if (j.has("world")) {
                sb.append(j.get("world").getAsString());
            }
            if (j.has("x") && j.has("z")) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(j.get("x").getAsInt()).append(", ").append(j.get("z").getAsInt());
            }
            if (j.has("biome")) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(j.get("biome").getAsString().replace("minecraft:", ""));
            }
            if (j.has("seed")) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append("seed ").append(j.get("seed").getAsLong());
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Lazily loads a screenshot as a GUI texture (capped, cached, failures skipped). */
    private Identifier loadThumb(File f) {
        String key = f.getAbsolutePath();
        Identifier existing = thumbs.get(key);
        if (existing != null) {
            return existing;
        }
        if (failed.contains(key) || thumbs.size() >= 60) {
            return null;
        }
        try (InputStream in = new FileInputStream(f)) {
            NativeImage img = NativeImage.read(in);
            DynamicTexture tex = new DynamicTexture(() -> "lads_gallery", img);
            Identifier id = Identifier.fromNamespaceAndPath("thelads",
                "gallery/" + Integer.toHexString(key.hashCode() & 0x7fffffff) + "_" + (f.lastModified() & 0xffffffL));
            Minecraft.getInstance().getTextureManager().register(id, tex);
            thumbs.put(key, id);
            return id;
        } catch (Throwable t) {
            failed.add(key);
            return null;
        }
    }

    @Override
    public void removed() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (Identifier id : thumbs.values()) {
            try {
                tm.release(id);
            } catch (Throwable ignored) {
            }
        }
        thumbs.clear();
        failed.clear();
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Mth.clamp(scrollOffset - verticalAmount * 24, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0) {
            if (mx >= this.width - 40 && mx < this.width - 16 && my >= 12 && my < 32) {
                this.minecraft.setScreenAndShow(parent);
                return true;
            }
            if (mx >= this.width - 130 && mx < this.width - 46 && my >= 12 && my < 32) {
                Util.getPlatform().openPath(screenshotsDir().toPath());
                return true;
            }
            int rowStride = ROW_H + GAP;
            int y = LIST_TOP - (int) scrollOffset;
            for (File f : new ArrayList<>(shots)) {
                if (my >= y && my < y + ROW_H && mx >= 16 && mx < this.width - 16) {
                    // Check if clicked the Copy button
                    if (mx >= this.width - 125 && mx < this.width - 85 && my >= y + 6 && my < y + ROW_H - 6) {
                        try {
                            String safe = f.getAbsolutePath().replace("'", "''");
                            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; [System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Image]::FromFile('" + safe + "'))");
                            pb.start();
                        } catch (Exception ignored) {}
                        return true;
                    }
                    // Check if clicked the Favorite button
                    if (mx >= this.width - 80 && mx < this.width - 50 && my >= y + 6 && my < y + ROW_H - 6) {
                        Set<String> favorites = loadFavorites();
                        if (favorites.contains(f.getName())) {
                            favorites.remove(f.getName());
                        } else {
                            favorites.add(f.getName());
                        }
                        saveFavorites(favorites);
                        return true;
                    }
                    // Check if clicked the Delete button
                    if (mx >= this.width - 45 && mx < this.width - 15 && my >= y + 6 && my < y + ROW_H - 6) {
                        if (f.exists()) {
                            f.delete();
                        }
                        File jsonFile = new File(f.getParentFile(), f.getName() + ".json");
                        if (jsonFile.exists()) {
                            jsonFile.delete();
                        }
                        Set<String> favorites = loadFavorites();
                        if (favorites.remove(f.getName())) {
                            saveFavorites(favorites);
                        }
                        shots.remove(f);
                        return true;
                    }
                    // Otherwise open the image viewer screen
                    this.minecraft.setScreenAndShow(new ImageViewerScreen(this, shots, shots.indexOf(f)));
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
            this.minecraft.setScreenAndShow(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
