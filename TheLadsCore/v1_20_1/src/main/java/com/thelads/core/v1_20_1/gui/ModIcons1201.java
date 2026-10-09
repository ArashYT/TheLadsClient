package com.thelads.core.v1_20_1.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Mod icons for the Installed mods list: a Fabric mod's own icon, a Forge mod's logo file (Zero SMP), or the icon found by scanning
 * the mods folder. Decoded off the render thread, uploaded on it; nothing is drawn (the caller shows a placeholder) until then.
 */
public final class ModIcons1201 {
    private record Icon(ResourceLocation id, int width, int height) {}

    private static final Map<String, Icon> ICONS = new ConcurrentHashMap<>();
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
    private static CompletableFuture<Map<String, DiskIcon>> diskIndex;

    private ModIcons1201() {}

    public static boolean draw(GuiGraphics g, String id, int x, int y, int size) {
        Icon icon = ICONS.get(id);
        if (icon == null) {
            if (REQUESTED.add(id)) load(id);
            return false;
        }
        RenderSystem.enableBlend();
        g.blit(icon.id, x, y, size, size, 0, 0, icon.width, icon.height, icon.width, icon.height);
        return true;
    }

    private record DiskIcon(Path jar, String entry) {}

    private static synchronized CompletableFuture<Map<String, DiskIcon>> diskIcons() {
        if (diskIndex != null) return diskIndex;
        return diskIndex = CompletableFuture.supplyAsync(() -> {
            var found = new HashMap<String, DiskIcon>();
            var dir = Minecraft.getInstance().gameDirectory.toPath().resolve("mods");
            try (var files = Files.list(dir)) {
                for (var file : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jar") || p.toString().endsWith(".jar.disabled")).limit(512).toList()) {
                    try (var zip = new java.util.zip.ZipFile(file.toFile())) {
                        var entry = zip.getEntry("fabric.mod.json");
                        if (entry == null) continue;
                        byte[] bytes;
                        try (var in = zip.getInputStream(entry)) {
                            bytes = in.readNBytes(1024 * 1024 + 1);
                        }
                        if (bytes.length > 1024 * 1024) continue;
                        var json = com.google.gson.JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                        if (!json.has("id") || !json.has("icon")) continue;
                        var icon = json.get("icon");
                        String name;
                        if (icon.isJsonPrimitive()) name = icon.getAsString();
                        else {
                            name = icon.getAsJsonObject().entrySet().stream().min(Comparator.comparingInt(e -> {
                                try {
                                    return Math.abs(Integer.parseInt(e.getKey()) - 64);
                                } catch (NumberFormatException ex) {
                                    return Integer.MAX_VALUE;
                                }
                            })).map(e -> e.getValue().getAsString()).orElse("");
                        }
                        if (zip.getEntry(name) != null) found.putIfAbsent(json.get("id").getAsString(), new DiskIcon(file, name));
                    } catch (Exception ignored) {}
                }
            } catch (IOException ignored) {}
            return found;
        });
    }

    /** A Forge mod's logo file (mods.toml logoFile), through Forge's own mod list. */
    private static Path forgeLogo(String id) {
        if (!ModPresence1201.forge()) return null;
        try {
            Class<?> modList = Class.forName("net.minecraftforge.fml.ModList");
            Object list = modList.getMethod("get").invoke(null);
            Optional<?> container = (Optional<?>) modList.getMethod("getModContainerById", String.class).invoke(list, id);
            if (container == null || container.isEmpty()) return null;
            Object info = container.get().getClass().getMethod("getModInfo").invoke(container.get());
            Optional<?> logo = (Optional<?>) info.getClass().getMethod("getLogoFile").invoke(info);
            if (logo == null || logo.isEmpty()) return null;
            Object file = info.getClass().getMethod("getOwningFile").invoke(info);
            Object modFile = file.getClass().getMethod("getFile").invoke(file);
            return (Path) modFile.getClass().getMethod("findResource", String[].class).invoke(modFile, (Object) new String[] {(String) logo.get()});
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            return null;
        }
    }

    private static void load(String id) {
        CompletableFuture.runAsync(() -> {
            try {
                var mod = FabricLoader.getInstance().getModContainer(id).orElse(null);
                Path path = mod == null ? null : mod.getMetadata().getIconPath(64).flatMap(mod::findPath).orElse(null);
                if (path == null) path = forgeLogo(id);
                byte[] bytes;
                if (path != null) {
                    try (var stream = Files.newInputStream(path)) {
                        bytes = stream.readNBytes(1024 * 1024 + 1);
                    }
                } else {
                    var icon = diskIcons().join().get(id);
                    if (icon == null) return;
                    try (var zip = new java.util.zip.ZipFile(icon.jar().toFile()); var stream = zip.getInputStream(zip.getEntry(icon.entry()))) {
                        bytes = stream.readNBytes(1024 * 1024 + 1);
                    }
                }
                if (bytes.length > 1024 * 1024 || bytes.length < 24) return;
                var header = ByteBuffer.wrap(bytes);
                if (header.getLong() != 0x89504E470D0A1A0AL) return;
                int w = header.getInt(16), h = header.getInt(20);
                if (w <= 0 || h <= 0 || w > 1024 || h > 1024) return;
                NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                Minecraft.getInstance().execute(() -> {
                    var texture = new ResourceLocation("theladscore", "mod_icons/" + id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_"));
                    var dynamic = new DynamicTexture(image);
                    Minecraft.getInstance().getTextureManager().register(texture, dynamic);
                    dynamic.setFilter(true, false);
                    ICONS.put(id, new Icon(texture, w, h));
                });
            } catch (Exception ignored) {}
        });
    }
}
