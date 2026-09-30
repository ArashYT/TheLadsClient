package com.thelads.core.v1_21_11.gui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Real mod icons for the Lads menu (26.x ModIcons): the loaded mod's icon, else the icon inside a disabled jar in mods/. */
public final class ModIcons {
    private record Icon(Identifier id, int width, int height) {}
    private record DiskIcon(java.nio.file.Path jar, String entry) {}
    private static final Map<String, Icon> ICONS = new ConcurrentHashMap<>();
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
    private static CompletableFuture<Map<String, DiskIcon>> diskIndex;
    private ModIcons() {}

    /** False until the icon is loaded (then the caller draws its letter placeholder). */
    public static boolean draw(GuiGraphics g, String id, int x, int y, int size) {
        Icon icon = ICONS.get(id);
        if (icon == null) { if (REQUESTED.add(id)) load(id); return false; }
        g.blit(RenderPipelines.GUI_TEXTURED, icon.id, x, y, 0, 0, size, size, icon.width, icon.height, icon.width, icon.height);
        return true;
    }
    private static synchronized CompletableFuture<Map<String, DiskIcon>> diskIcons() {
        if (diskIndex != null) return diskIndex;
        return diskIndex = CompletableFuture.supplyAsync(() -> {
            var found = new HashMap<String, DiskIcon>();
            var dir = Minecraft.getInstance().gameDirectory.toPath().resolve("mods");
            try (var files = java.nio.file.Files.list(dir)) {
                for (var file : files.filter(java.nio.file.Files::isRegularFile).filter(p -> p.toString().endsWith(".jar") || p.toString().endsWith(".jar.disabled")).limit(512).toList()) {
                    try (var zip = new java.util.zip.ZipFile(file.toFile())) {
                        var entry = zip.getEntry("fabric.mod.json"); if (entry == null) continue;
                        byte[] bytes; try (var in = zip.getInputStream(entry)) { bytes = in.readNBytes(1024 * 1024 + 1); } if (bytes.length > 1024 * 1024) continue;
                        var json = com.google.gson.JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                        if (!json.has("id") || !json.has("icon")) continue;
                        var icon = json.get("icon"); String name;
                        if (icon.isJsonPrimitive()) name = icon.getAsString();
                        else name = icon.getAsJsonObject().entrySet().stream().min(Comparator.comparingInt(e -> { try { return Math.abs(Integer.parseInt(e.getKey()) - 64); } catch (NumberFormatException ex) { return Integer.MAX_VALUE; } })).map(e -> e.getValue().getAsString()).orElse("");
                        if (zip.getEntry(name) != null) found.putIfAbsent(json.get("id").getAsString(), new DiskIcon(file, name));
                    } catch (Exception ignored) {}
                }
            } catch (java.io.IOException ignored) {}
            return found;
        });
    }
    private static void load(String id) {
        CompletableFuture.runAsync(() -> {
            try {
                var mod = FabricLoader.getInstance().getModContainer(id).orElse(null);
                var path = mod == null ? null : mod.getMetadata().getIconPath(64).flatMap(mod::findPath).orElse(null);
                byte[] bytes;
                if (path != null) { try (var stream = java.nio.file.Files.newInputStream(path)) { bytes = stream.readNBytes(1024 * 1024 + 1); } }
                else {
                    var icon = diskIcons().join().get(id); if (icon == null) return;
                    try (var zip = new java.util.zip.ZipFile(icon.jar().toFile()); var stream = zip.getInputStream(zip.getEntry(icon.entry()))) { bytes = stream.readNBytes(1024 * 1024 + 1); }
                }
                if (bytes.length > 1024 * 1024 || bytes.length < 24) return;
                var header = java.nio.ByteBuffer.wrap(bytes); if (header.getLong() != 0x89504E470D0A1A0AL) return;
                int w = header.getInt(16), h = header.getInt(20); if (w <= 0 || h <= 0 || w > 1024 || h > 1024) return;
                NativeImage image = NativeImage.read(bytes);
                Minecraft.getInstance().execute(() -> {
                    var texture = Identifier.fromNamespaceAndPath("theladscore", "mod_icons/" + id.toLowerCase(Locale.ROOT));
                    Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "Icon for " + id, image));
                    ICONS.put(id, new Icon(texture, w, h));
                });
            } catch (Exception ignored) {}
        });
    }
}
