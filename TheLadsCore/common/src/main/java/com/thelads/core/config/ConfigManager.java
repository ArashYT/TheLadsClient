package com.thelads.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.thelads.core.modules.BetterResolutionModule;
import com.thelads.core.modules.HudModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.client.util.ClientPaths;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File testConfigFile = null;
    private static final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();
    private static final Object LATER = new Object();
    private static String latestJson;
    private static long latestOrder, writtenOrder;
    private static ScheduledExecutorService writer;

    public static void setTestConfigFile(File file) {
        testConfigFile = file;
    }

    private static File getConfigFile() {
        if (testConfigFile != null) {
            return testConfigFile;
        }
        return ClientPaths.getConfigFile();
    }

    /** Serialize the current module + HUD state to a JSON snapshot (also used by profiles). */
    public static JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonObject modulesJson = new JsonObject();
        for (Module module : ModuleManager.getInstance().getModules()) {
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("enabled", module.isEnabled());
            if (module instanceof HudModule) {
                HudModule hm = (HudModule) module;
                moduleJson.addProperty("useGlobalColor", hm.isUseGlobalColor());
                moduleJson.addProperty("customColor", hm.getCustomColor());
            }
            if (!module.getOptions().isEmpty()) {
                JsonObject opts = new JsonObject();
                for (Option o : module.getOptions()) {
                    opts.add(o.getName(), o.save());
                }
                moduleJson.add("options", opts);
            }
            moduleJson.addProperty("favorite", module.isFavorite());
            moduleJson.addProperty("lastModified", module.getLastModified());
            modulesJson.add(module.getName(), moduleJson);
        }
        json.add("modules", modulesJson);

        JsonObject hud = new JsonObject();
        hud.addProperty("globalColor", HudSettings.getInstance().getGlobalColor());
        hud.addProperty("globalBackground", HudSettings.getInstance().getGlobalBackground());
        hud.addProperty("textShadow", HudSettings.getInstance().isTextShadow());
        hud.addProperty("softShadow", HudSettings.getInstance().isSoftShadow());
        hud.addProperty("backgrounds", HudSettings.getInstance().isBackgrounds());
        hud.addProperty("hudFpsCapOptIn", HudSettings.getInstance().isHudFpsCapEnabled());
        hud.addProperty("hudFpsCapEnabled", HudSettings.getInstance().isHudFpsCapEnabled()); // read by 1.4.4 and older
        hud.addProperty("hudFpsLimit", HudSettings.getInstance().getHudFpsLimit());
        JsonArray favorites = new JsonArray();
        HudSettings.getInstance().getFavoriteColors().forEach(favorites::add);
        hud.add("favoriteColors", favorites);
        JsonObject positions = new JsonObject();
        for (Map.Entry<String, int[]> e : HudSettings.getInstance().getPositions().entrySet()) {
            JsonArray xy = new JsonArray();
            xy.add(e.getValue()[0]);
            xy.add(e.getValue()[1]);
            positions.add(e.getKey(), xy);
        }
        hud.add("positions", positions);
        JsonArray fade = new JsonArray();
        for (int c : HudSettings.getInstance().getFadePlaylist()) {
            fade.add(c);
        }
        hud.add("fadePlaylist", fade);

        JsonArray lockedArr = new JsonArray();
        for (String n : HudSettings.getInstance().getLocked()) lockedArr.add(n);
        hud.add("locked", lockedArr);

        JsonArray groupsArr = new JsonArray();
        for (Set<String> g : HudSettings.getInstance().getGroups()) {
            JsonArray ga = new JsonArray();
            for (String n : g) ga.add(n);
            groupsArr.add(ga);
        }
        hud.add("groups", groupsArr);

        json.add("hud", hud);
        return json;
    }

    /** Apply a JSON snapshot to the live module + HUD state. */
    public static void applyJson(JsonObject json) {
        if (json == null) {
            return;
        }
        migrateToggles(json);
        if (json.has("modules")) {
            JsonObject modulesJson = json.getAsJsonObject("modules");
            migrateChat(modulesJson);
            migrateDynamicLights(modulesJson);
            migrateDiscord(modulesJson);
            for (Module module : ModuleManager.getInstance().getModules()) {
                try {
                    if (module.getName().equals("Nametags") && !modulesJson.has("Nametags") && modulesJson.has("ToggleNametags"))
                        modulesJson.add("Nametags", modulesJson.get("ToggleNametags"));
                    // 1.7.0 renamed RenderScale to BetterResolution (same options): what only the old entry has carries over,
                    // e.g. its options when the launcher has already switched BetterResolution on or off.
                    if (module.getName().equals(BetterResolutionModule.NAME) && modulesJson.get("RenderScale") instanceof JsonObject old) {
                        if (!(modulesJson.get(BetterResolutionModule.NAME) instanceof JsonObject)) modulesJson.add(BetterResolutionModule.NAME, new JsonObject());
                        JsonObject renamed = modulesJson.getAsJsonObject(BetterResolutionModule.NAME);
                        for (var entry : old.entrySet()) if (!renamed.has(entry.getKey())) renamed.add(entry.getKey(), entry.getValue());
                    }
                    if (!modulesJson.has(module.getName())) {
                        continue;
                    }
                    JsonObject moduleJson = modulesJson.getAsJsonObject(module.getName());
                    if (module instanceof HudModule) {
                        HudModule hm = (HudModule) module;
                        if (moduleJson.has("useGlobalColor")) {
                            hm.setUseGlobalColor(moduleJson.get("useGlobalColor").getAsBoolean());
                        }
                        if (moduleJson.has("customColor")) {
                            hm.setCustomColor(moduleJson.get("customColor").getAsInt());
                        }
                    }
                    if (moduleJson.has("options")) {
                        JsonObject opts = moduleJson.getAsJsonObject("options");
                        for (Option o : module.getOptions()) {
                            if (opts.has(o.getName())) {
                                o.load(opts.get(o.getName()));
                            }
                        }
                    }
                    // 1.6.0 and older could not switch BetterF3 (an external mod or unavailable there), so the "enabled" they saved is
                    // only that module's old default: the native Better F3 keeps its own default until a save that has its 1.7.0 options.
                    boolean legacyBetterF3 = module.getName().equals("BetterF3")
                        && !(moduleJson.get("options") instanceof JsonObject saved && saved.has("Text Shadow"));
                    if (moduleJson.has("enabled") && !legacyBetterF3) {
                        module.setEnabled(moduleJson.get("enabled").getAsBoolean());
                    }
                    if (moduleJson.has("favorite")) {
                        module.setFavorite(moduleJson.get("favorite").getAsBoolean());
                    }
                    if (moduleJson.has("lastModified")) {
                        module.setLastModified(moduleJson.get("lastModified").getAsLong());
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to apply config for module: " + module.getName());
                    ex.printStackTrace();
                }
            }
        }
        if (json.has("hud")) {
            JsonObject hud = json.getAsJsonObject("hud");
            if (hud.has("globalColor")) {
                HudSettings.getInstance().setGlobalColor(hud.get("globalColor").getAsInt());
            }
            if (hud.has("globalBackground")) {
                HudSettings.getInstance().setGlobalBackground(hud.get("globalBackground").getAsInt());
            }
            if (hud.has("textShadow")) {
                HudSettings.getInstance().setTextShadow(hud.get("textShadow").getAsBoolean());
            }
            if (hud.has("softShadow")) {
                HudSettings.getInstance().setSoftShadow(hud.get("softShadow").getAsBoolean());
            }
            if (hud.has("hudFpsLimit")) {
                HudSettings.getInstance().setHudFpsLimit(hud.get("hudFpsLimit").getAsInt());
            }
            if (hud.has("hudFpsCapOptIn")) {
                HudSettings.getInstance().setHudFpsCapEnabled(hud.get("hudFpsCapOptIn").getAsBoolean());
            } else if (hud.has("hudFpsCapEnabled")) {
                // 1.4.1-1.4.4 saved their default (on, 60) in every config: only a changed limit was the player's choice.
                HudSettings.getInstance().setHudFpsCapEnabled(hud.get("hudFpsCapEnabled").getAsBoolean() && HudSettings.getInstance().getHudFpsLimit() != 60);
            }
            if (hud.has("backgrounds")) HudSettings.getInstance().setBackgrounds(hud.get("backgrounds").getAsBoolean());
            if (hud.has("favoriteColors")) {
                var favorites = HudSettings.getInstance().getFavoriteColors(); favorites.clear();
                for (var color : hud.getAsJsonArray("favoriteColors")) {
                    if (favorites.size() >= 24) break;
                    try { favorites.add(color.getAsInt()); } catch (RuntimeException ignored) {}
                }
            }
            if (hud.has("positions")) {
                JsonObject pos = hud.getAsJsonObject("positions");
                for (String key : pos.keySet()) {
                    try {
                        JsonArray xy = pos.getAsJsonArray(key);
                        HudSettings.getInstance().setPosition(key, xy.get(0).getAsInt(), xy.get(1).getAsInt());
                    } catch (Exception ignored) {
                    }
                }
            }
            if (hud.has("fadePlaylist")) {
                HudSettings.getInstance().getFadePlaylist().clear();
                JsonArray fade = hud.getAsJsonArray("fadePlaylist");
                for (int i = 0; i < fade.size(); i++) {
                    HudSettings.getInstance().getFadePlaylist().add(fade.get(i).getAsInt());
                }
            }
            if (hud.has("locked")) {
                HudSettings.getInstance().replaceLocked(readHudNames(hud.get("locked")));
            }
            if (hud.has("groups")) {
                java.util.List<Set<String>> groups = new java.util.ArrayList<>();
                if(hud.get("groups").isJsonArray())for(var group:hud.getAsJsonArray("groups")){
                    if(groups.size()>=256)break;
                    groups.add(readHudNames(group));
                }
                HudSettings.getInstance().replaceGroups(groups);
            }
        }
    }

    /** Before 1.7.1 Discord presence was "Coming soon", so nothing saved for it was the player's choice: it starts from the new defaults. */
    public static void migrateDiscord(JsonObject modules) {
        if (modules.get("DiscordRPC") instanceof JsonObject discord && discord.get("options") instanceof JsonObject options
                && options.has("Share activity")) modules.remove("DiscordRPC");
    }

    /** 1.4.5 moved ClientTools "Chat timestamps" and the HideChatIndicators module into Chat options; keeps whatever the user had. */
    static void migrateChat(JsonObject modules) {
        JsonElement stamps = legacy(modules, "ClientTools", "options", "Chat timestamps");
        JsonElement indicators = legacy(modules, "HideChatIndicators", "enabled");
        if (stamps == null && indicators == null) return;
        if (!(modules.get("Chat") instanceof JsonObject)) modules.add("Chat", new JsonObject());
        JsonObject chat = modules.getAsJsonObject("Chat");
        if (!(chat.get("options") instanceof JsonObject)) chat.add("options", new JsonObject());
        JsonObject options = chat.getAsJsonObject("options");
        if (stamps != null && !options.has("Timestamps")) options.add("Timestamps", stamps);
        if (indicators != null && !options.has("Hide Signing Indicators")) options.add("Hide Signing Indicators", indicators);
    }

    /**
     * 1.7.0 merged ToggleSprint and ToggleSneak into one module with one HUD element. It is on if either was; each keeps its mode
     * (Sprint becomes Vanilla only when Toggle Sneak alone was on; Sneak is Toggle only when Toggle Sneak was on in Toggle mode).
     * The HUD element takes the position, lock and group of the one that was on, Toggle Sprint's when both or neither were.
     */
    static void migrateToggles(JsonObject json) {
        if (!(json.get("modules") instanceof JsonObject modules) || modules.has(ToggleSprintModule.NAME)) return;
        JsonElement sprint = modules.remove("ToggleSprint"), sneak = modules.remove("ToggleSneak");
        if (sprint == null && sneak == null) return;
        boolean sprintOn = bool(legacy(modules(sprint), "enabled")), sneakOn = bool(legacy(modules(sneak), "enabled"));
        JsonObject merged = new JsonObject(), options = new JsonObject();
        merged.addProperty("enabled", sprintOn || sneakOn);
        options.addProperty("Sprint", sneakOn && !sprintOn ? ToggleSprintModule.VANILLA : number(legacy(modules(sprint), "options", "Mode")));
        options.addProperty("Sneak", sneakOn && number(legacy(modules(sneak), "options", "Mode")) == 0 ? ToggleSprintModule.TOGGLE : 1);
        JsonElement pause = legacy(modules(sprint), "options", "Disable on sneak");
        if (pause != null) options.add("Pause sprint while sneaking", pause);
        merged.add("options", options);
        merged.addProperty("favorite", bool(legacy(modules(sprint), "favorite")) || bool(legacy(modules(sneak), "favorite")));
        modules.add(ToggleSprintModule.NAME, merged);
        if (!(json.get("hud") instanceof JsonObject hud)) return;
        String kept = sneakOn && !sprintOn ? "ToggleSneak" : "ToggleSprint", dropped = kept.equals("ToggleSprint") ? "ToggleSneak" : "ToggleSprint";
        if (hud.get("positions") instanceof JsonObject positions) {
            JsonElement position = positions.remove(kept);
            positions.remove(dropped);
            if (position != null) positions.add(ToggleSprintModule.NAME, position);
        }
        java.util.List<JsonArray> lists = new java.util.ArrayList<>();
        if (hud.get("locked") instanceof JsonArray locked) lists.add(locked);
        if (hud.get("groups") instanceof JsonArray groups) for (JsonElement group : groups) if (group instanceof JsonArray names) lists.add(names);
        for (JsonArray names : lists) for (int i = names.size() - 1; i >= 0; i--) {
            String name = names.get(i).isJsonPrimitive() ? names.get(i).getAsString() : "";
            if (name.equals(kept)) names.set(i, new com.google.gson.JsonPrimitive(ToggleSprintModule.NAME));
            else if (name.equals(dropped)) names.remove(i);
        }
    }

    private static JsonObject modules(JsonElement module) { return module instanceof JsonObject object ? object : null; }
    private static boolean bool(JsonElement value) { try { return value != null && value.getAsBoolean(); } catch (RuntimeException e) { return false; } }
    private static int number(JsonElement value) { try { return value == null ? 0 : value.getAsInt(); } catch (RuntimeException e) { return 0; } }

    /**
     * 1.7.0 builds Dynamic Lights in, on as the LambDynamicLights it replaces was. Until then nobody could change the module
     * (its card showed that mod's own settings), so a state saved without a change holds only the old defaults: drop it.
     */
    static void migrateDynamicLights(JsonObject modules) {
        JsonElement changed = legacy(modules, "DynamicLights", "lastModified");
        if (modules.get("DynamicLights") instanceof JsonObject
            && (changed == null || changed.isJsonPrimitive() && changed.getAsJsonPrimitive().isNumber() && changed.getAsLong() == 0))
            modules.remove("DynamicLights");
    }

    private static JsonElement legacy(JsonObject root, String... path) {
        JsonElement value = root;
        for (String key : path) {
            if (!(value instanceof JsonObject) || !((JsonObject) value).has(key)) return null;
            value = ((JsonObject) value).get(key);
        }
        return value;
    }

    private static Set<String> readHudNames(com.google.gson.JsonElement value) {
        Set<String> names=new java.util.LinkedHashSet<>();
        if(value!=null&&value.isJsonArray())for(var name:value.getAsJsonArray()){
            if(names.size()>=256)break;
            if(name.isJsonPrimitive()&&name.getAsJsonPrimitive().isString())names.add(name.getAsString());
        }
        return names;
    }

    public static void load() {
        File configFile = getConfigFile();
        if (!configFile.exists()) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            applyJson(GSON.fromJson(reader, JsonObject.class));
        } catch (Exception e) {
            System.err.println("Failed to load config");
            e.printStackTrace();
        }
    }

    /** A module's saved on/off, read before modules exist: for features set up once at startup (Jasione). */
    public static boolean savedEnabled(String module, boolean fallback) {
        File configFile = getConfigFile();
        if (!configFile.exists()) return fallback;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            JsonElement enabled = legacy(GSON.fromJson(reader, JsonObject.class), "modules", module, "enabled");
            return enabled == null ? fallback : enabled.getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Writes the config now. Anything {@link #saveLater} still holds is older than this snapshot and is dropped. */
    public static void save() {
        String json = GSON.toJson(toJson());
        write(json, sequence.incrementAndGet());
    }

    /**
     * For changes made while playing (Toggle Sprint/Sneak): the state is serialised now, on the calling thread, and written by a
     * background thread half a second later, so a burst of toggles costs one disk write and none of it on the render thread.
     * {@link #flush} (also run at JVM exit) writes whatever is still waiting.
     */
    public static void saveLater() {
        String json = GSON.toJson(toJson());
        long order = sequence.incrementAndGet();
        synchronized (LATER) {
            boolean waiting = latestJson != null;
            latestJson = json;
            latestOrder = order;
            if (waiting) return;
            if (writer == null) {
                writer = Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "Lads config writer");
                    thread.setDaemon(true);
                    return thread;
                });
                Runtime.getRuntime().addShutdownHook(new Thread(ConfigManager::flush, "Lads config flush"));
            }
            writer.schedule(ConfigManager::flush, 500, TimeUnit.MILLISECONDS);
        }
    }

    /** Writes the config {@link #saveLater} is holding, if any. */
    public static void flush() {
        String json;
        long order;
        synchronized (LATER) {
            json = latestJson;
            order = latestOrder;
            latestJson = null;
        }
        if (json != null) write(json, order);
    }

    /** Atomic replace; a snapshot older than one already written is skipped, so a late background write never undoes a newer save. */
    private static synchronized void write(String json, long order) {
        if (order < writtenOrder) return;
        writtenOrder = order;
        File configFile = getConfigFile();
        if (configFile.getParentFile() != null) {
            configFile.getParentFile().mkdirs();
        }
        Path temporary = null;
        try {
            Path destination = configFile.toPath().toAbsolutePath();
            temporary = Files.createTempFile(destination.getParent(), ".lads-config-", ".tmp");
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
        }
    }
}
