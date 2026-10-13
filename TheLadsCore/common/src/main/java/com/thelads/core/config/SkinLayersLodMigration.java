package com.thelads.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 1.9.2: 3D Skin Layers LOD distance defaults to 8 blocks (down from upstream mod default 14).
 * Existing players whose 3D Skin Layers config has the mod default 14 are migrated to 8 once,
 * tracked by the {@link #FLAG} in thelads_config.json. Custom player choices (!= 14) are strictly preserved.
 */
public final class SkinLayersLodMigration {
    public static final String FLAG = "skinLayersLodOnDefault";
    public static final int DEFAULT_LOD = 8;
    public static final int MOD_DEFAULT_LOD = 14;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path testGameDir = null;

    private SkinLayersLodMigration() {}

    public static void setTestGameDir(Path dir) {
        testGameDir = dir;
    }

    public static Path resolveConfigFile(Path explicitGameDir) {
        if (explicitGameDir != null) {
            return explicitGameDir.resolve("config").resolve("skinlayers.json");
        }
        if (testGameDir != null) {
            return testGameDir.resolve("config").resolve("skinlayers.json");
        }
        // 1. Check FabricLoader
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Path configDir = (Path) loaderClass.getMethod("getConfigDir").invoke(loader);
            if (configDir != null) {
                return configDir.resolve("skinlayers.json");
            }
        } catch (Throwable ignored) {}

        // 2. Check 1.8.9 Minecraft mcDataDir
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("func_71410_x").invoke(null);
            if (mc != null) {
                File dataDir = (File) mcClass.getField("field_71412_D").get(mc);
                if (dataDir != null) {
                    return dataDir.toPath().resolve("config").resolve("skinlayers.json");
                }
            }
        } catch (Throwable ignored) {
            try {
                Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
                Object mc = mcClass.getMethod("getMinecraft").invoke(null);
                if (mc != null) {
                    File dataDir = (File) mcClass.getField("mcDataDir").get(mc);
                    if (dataDir != null) {
                        return dataDir.toPath().resolve("config").resolve("skinlayers.json");
                    }
                }
            } catch (Throwable ignored2) {}
        }

        // 3. Fallback: current working directory if config directory exists
        if (Files.isDirectory(Path.of("config")) || Files.exists(Path.of("config", "skinlayers.json"))) {
            return Path.of("config", "skinlayers.json");
        }
        return null;
    }

    public static boolean migrate(Path explicitGameDir) {
        Path target = resolveConfigFile(explicitGameDir);
        if (target == null) return false;
        boolean modified = false;
        try {
            if (!Files.exists(target)) {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                JsonObject json = new JsonObject();
                json.addProperty("renderDistanceLOD", DEFAULT_LOD);
                writeAtomic(target, GSON.toJson(json));
                modified = true;
            } else {
                String content = Files.readString(target, StandardCharsets.UTF_8);
                JsonObject json;
                try {
                    json = JsonParser.parseString(content).getAsJsonObject();
                } catch (Exception e) {
                    return modified; // not ours to repair: rewriting it would drop the player's other 3D Skin Layers settings
                }
                if (json.has("renderDistanceLOD")) {
                    int current = json.get("renderDistanceLOD").getAsInt();
                    if (current == MOD_DEFAULT_LOD) {
                        json.addProperty("renderDistanceLOD", DEFAULT_LOD);
                        writeAtomic(target, GSON.toJson(json));
                        modified = true;
                    }
                } else {
                    json.addProperty("renderDistanceLOD", DEFAULT_LOD);
                    writeAtomic(target, GSON.toJson(json));
                    modified = true;
                }
            }
        } catch (Exception e) {
            System.err.println("SkinLayersLodMigration: failed to migrate " + target + ": " + e.getMessage());
        }

        syncInMemoryLod(DEFAULT_LOD);
        return modified;
    }

    public static void syncInMemoryLod(int targetLod) {
        try {
            Class<?> modBase = Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase");
            Object cfg = modBase.getField("config").get(null);
            if (cfg != null) {
                int current = cfg.getClass().getField("renderDistanceLOD").getInt(cfg);
                if (current == MOD_DEFAULT_LOD) {
                    cfg.getClass().getField("renderDistanceLOD").setInt(cfg, targetLod);
                }
            }
        } catch (Throwable ignored) {
            try {
                Class<?> modBase = Class.forName("dev.tr7zw.skinlayers.versionless.ModBase");
                Object cfg = modBase.getField("config").get(null);
                if (cfg != null) {
                    int current = cfg.getClass().getField("renderDistanceLOD").getInt(cfg);
                    if (current == MOD_DEFAULT_LOD) {
                        cfg.getClass().getField("renderDistanceLOD").setInt(cfg, targetLod);
                    }
                }
            } catch (Throwable ignored2) {}
        }
    }

    private static void writeAtomic(Path destination, String content) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".skinlayers-", ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
        }
    }
}
