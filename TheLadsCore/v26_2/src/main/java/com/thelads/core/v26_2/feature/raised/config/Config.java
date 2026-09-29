// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.v26_2.feature.raised.Raised;
import com.thelads.core.v26_2.feature.raised.client.gui.group.Group;
import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layer;
import com.thelads.core.v26_2.feature.raised.option.AdditionalSettings.HotbarSelectionFix;
import com.thelads.core.v26_2.feature.raised.option.Options;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

public class Config {

    public static File file = new File(FabricLoader.getInstance().getConfigDir().toFile(), Raised.MOD_ID + ".json");
    public static Gson gson = new GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().disableHtmlEscaping().create();
    public static Options options = new Options();

    public static Options getOptions() {
        return options;
    }

    public static void setOptions(Options options) {
        Config.options = options;
    }

    public static void save() {
        java.nio.file.Path temporary = null;
        try {
            var destination = file.toPath().toAbsolutePath();
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), "lads-layout-", ".tmp");
            Files.writeString(temporary, gson.toJson(getOptions()), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) { throw new RuntimeException("Could not save Lads HUD layout", e); }
        finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (java.io.IOException ignored) {}
        }
    }

    public static void load() {
        if (file.exists()) {
            try {
                if (Files.size(file.toPath()) > 1_048_576) throw new IllegalArgumentException("Layout file is too large");
                var document = JsonParser.parseString(Files.readString(file.toPath())).getAsJsonObject();
                boolean legacy = isLegacy(document);
                Options candidate = legacy ? migrateLegacy(document) : gson.fromJson(document, Options.class);
                validate(candidate);
                if (legacy) {
                    // NativeRaised imports a copy; also retain the exact pre-migration native copy.
                    var backup = file.toPath().resolveSibling(file.getName() + ".pre-migration-" + System.currentTimeMillis());
                    Files.copy(file.toPath(), backup);
                }
                setOptions(candidate);
                if (legacy) {
                    save();
                    Raised.LOGGER.info("Migrated legacy Raised HUD layout into {} linked groups", candidate.groups.size());
                }
                return;
            } catch (Exception failure) {
                try {
                    // Keep the exact original before recovering to usable defaults.
                    var backup = file.toPath().resolveSibling(file.getName() + ".invalid-" + System.currentTimeMillis());
                    Files.copy(file.toPath(), backup);
                    Raised.LOGGER.warn("Recovered malformed HUD layout; original retained at {}", backup, failure);
                } catch (Exception backupFailure) { throw new RuntimeException("Could not preserve invalid HUD layout", backupFailure); }
            }
        }
        setOptions(new Options());
        save();
    }

    private static boolean isLegacy(JsonObject document) {
        if (document.has("groups") || !document.has("layers") || !document.get("layers").isJsonObject()) return false;
        return document.has("resource") || document.getAsJsonObject("layers").entrySet().stream().anyMatch(entry ->
                entry.getValue().isJsonObject() && (entry.getValue().getAsJsonObject().has("displacement")
                        || entry.getValue().getAsJsonObject().has("direction") || entry.getValue().getAsJsonObject().has("sync")));
    }

    /** Raised 5.1.2 Translate.getX/Y uses a direct sync target's raw displacement and the caller's direction. */
    private static Options migrateLegacy(JsonObject document) {
        LegacyLayout legacy = gson.fromJson(document, LegacyLayout.class);
        if (legacy.layers == null || legacy.layers.size() > 2048) throw new IllegalArgumentException("Invalid legacy HUD layers");
        Options migrated = new Options();
        migrated.groups.clear();
        for (var entry : legacy.layers.entrySet()) {
            var layer = entry.getValue();
            if (layer == null || layer.displacement == null || layer.direction == null
                    || layer.direction.x == null || layer.direction.y == null || layer.sync == null)
                throw new IllegalArgumentException("Incomplete legacy HUD layer");
            int x = switch (layer.direction.x) { case "LEFT" -> -1; case "NONE" -> 0; case "RIGHT" -> 1;
                default -> throw new IllegalArgumentException("Unknown legacy horizontal direction"); };
            int y = switch (layer.direction.y) { case "UP" -> -1; case "NONE" -> 0; case "DOWN" -> 1;
                default -> throw new IllegalArgumentException("Unknown legacy vertical direction"); };
            Layer.Anchor anchor = java.util.Arrays.stream(Layer.Anchor.values())
                    .filter(value -> value.getX() == x && value.getY() == y).findFirst().orElseThrow();
            // Deliberately do not recursively follow chains: that would change existing layouts and cycles.
            String sourceName = legacy.layers.containsKey(layer.sync) ? layer.sync : entry.getKey();
            var source = legacy.layers.get(sourceName);
            if (source == null || source.displacement == null) throw new IllegalArgumentException("Incomplete legacy HUD sync target");
            Group group = migrated.groups.computeIfAbsent("Imported: " + sourceName, ignored ->
                    new Group(new Group.Offset(source.displacement.x, source.displacement.y), new TreeSet<>()));
            group.layers.add(entry.getKey());
            migrated.layers.put(entry.getKey(), new Layer(anchor));
            String alias = switch (entry.getKey()) {
                case "minecraft:bossbar" -> "minecraft:boss_bar";
                case "minecraft:players" -> "minecraft:player_list";
                case "minecraft:sidebar" -> "minecraft:scoreboard";
                case "minecraft:other" -> "minecraft:unknown";
                // Overlay messages used the hotbar transform before becoming a separate 6.0 layer.
                case "minecraft:hotbar" -> "minecraft:action_bar";
                default -> entry.getKey();
            };
            if (!legacy.layers.containsKey(alias)) {
                group.layers.add(alias);
                migrated.layers.put(alias, new Layer(anchor));
            }
        }
        if (legacy.resource != null && legacy.resource.texture != null)
            migrated.additionalSettings.hotbarSelectionFix = HotbarSelectionFix.valueOf(legacy.resource.texture);
        return migrated;
    }

    private static final class LegacyLayout { TreeMap<String, LegacyLayer> layers; LegacyResource resource; }
    private static final class LegacyLayer { LegacyDisplacement displacement; LegacyDirection direction; String sync; }
    private static final class LegacyDisplacement { int x; int y; }
    private static final class LegacyDirection { String x; String y; }
    private static final class LegacyResource { String texture; }

    public static void validate(Options candidate) {
        if (candidate == null || candidate.groups == null || candidate.layers == null || candidate.additionalSettings == null
                || candidate.additionalSettings.hotbarSelectionFix == null) throw new IllegalArgumentException("Incomplete HUD layout");
        if (candidate.groups.size() > 512 || candidate.layers.size() > 2048) throw new IllegalArgumentException("Too many HUD layout entries");
        candidate.groups.forEach((name, group) -> {
            if (name == null || group == null || group.offset == null || group.layers == null)
                throw new IllegalArgumentException("Incomplete HUD group");
        });
        candidate.layers.forEach((name, layer) -> {
            if (name == null || layer == null || layer.anchor == null) throw new IllegalArgumentException("Incomplete HUD layer");
        });
    }
    public static void update(Consumer<Options> updater) {
        if (options == null) {
            setOptions(new Options());
        }

        updater.accept(options);
        save();
    }

}
