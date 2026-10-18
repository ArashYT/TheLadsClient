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
    public static final String PRESET_197_FLAG = "lads197PresetApplied";
    static final String HUD_1910_FLAG = "hud1910OverrideApplied";
    /** Marks the 1.9.11 one-time Better F3 override (apply1911BetterF3Override). */
    static final String F3_1911_FLAG = "betterF31911Applied";
    /** 1.9.11: the game versions whose Emotes key clash (another mod's default on B) was resolved once (EmoteKeys, each version). */
    static final String EMOTE_KEYS_FLAG = "emoteKeyClashResolved";
    private static final java.util.Set<String> emoteKeysResolved = new java.util.concurrent.CopyOnWriteArraySet<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File testConfigFile = null;
    private static final java.util.Map<String, JsonObject> unmanagedModules = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();
    private static final Object LATER = new Object();
    private static String latestJson;
    private static File latestTargetFile;
    private static long latestOrder, writtenOrder;
    private static ScheduledExecutorService writer;

    public static void setTestConfigFile(File file) {
        synchronized (LATER) {
            testConfigFile = file;
            latestJson = null;
            latestTargetFile = null;
        }
        unmanagedModules.clear();
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
        for (java.util.Map.Entry<String, JsonObject> entry : unmanagedModules.entrySet()) {
            modulesJson.add(entry.getKey(), entry.getValue().deepCopy());
        }
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
        hud.addProperty("shadowOnDefault", true); // marks the 1.9.1 migration of the old Soft default (applyJson)
        hud.addProperty("hudFpsCapOnDefault", true); // marks the 1.9.2 migration of the old uncapped default (applyJson)
        hud.addProperty(HUD_1910_FLAG, true); // marks the 1.9.10 one-time HUD override (apply1910HudOverride)
        hud.addProperty(F3_1911_FLAG, true); // marks the 1.9.11 one-time Better F3 override (apply1911BetterF3Override)
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
        // Anchors (1.9.0): {"to": "#hotbar" or a HUD, "at": [x, y], "x": [ref, pin, offset, room], "y": [...]}; older builds read positions only.
        JsonObject anchors = new JsonObject();
        for (Map.Entry<String, HudSettings.Anchor> e : HudSettings.getInstance().getAnchors().entrySet()) {
            HudSettings.Anchor a = e.getValue();
            JsonObject anchor = new JsonObject();
            if (a.to() != null) anchor.addProperty("to", a.to());
            anchor.add("at", ints(a.x(), a.y()));
            anchor.add("x", ints(a.h().ref(), a.h().pin(), a.h().off(), a.h().room()));
            anchor.add("y", ints(a.v().ref(), a.v().pin(), a.v().off(), a.v().room()));
            anchors.add(e.getKey(), anchor);
        }
        hud.add("anchors", anchors);
        hud.addProperty("hudScale", HudSettings.getInstance().getHudScale());
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

        JsonArray excMods = new JsonArray();
        for (String m : HudSettings.getInstance().getExcludedModules()) excMods.add(m);
        hud.add("excludedModules", excMods);

        JsonArray excExt = new JsonArray();
        for (String m : HudSettings.getInstance().getExcludedExternalMods()) excExt.add(m);
        hud.add("excludedExternalMods", excExt);

        json.add("hud", hud);
        json.addProperty(SkinLayersLodMigration.FLAG, true); // marks the 1.9.2 migration of 3D Skin Layers LOD 14 -> 8
        json.addProperty(PRESET_197_FLAG, true); // marks the 1.9.7 one-time preset override
        JsonArray emoteKeys = new JsonArray();
        emoteKeysResolved.forEach(emoteKeys::add);
        json.add(EMOTE_KEYS_FLAG, emoteKeys);
        return json;
    }

    /** Apply a JSON snapshot to the live module + HUD state. */
    public static void applyJson(JsonObject json) {
        if (json == null) {
            return;
        }
        migrateToggles(json);
        if (json.get(EMOTE_KEYS_FLAG) instanceof JsonArray emoteKeys) emoteKeys.forEach(version -> emoteKeysResolved.add(version.getAsString()));
        if (!json.has(SkinLayersLodMigration.FLAG) || !json.get(SkinLayersLodMigration.FLAG).getAsBoolean()) {
            SkinLayersLodMigration.migrate(null);
        }
        if (json.has("modules")) {
            JsonObject modulesJson = json.getAsJsonObject("modules");
            migrateChat(modulesJson);
            migrateChatHeads(modulesJson);
            migrateDynamicLights(modulesJson);
            migrateDiscord(modulesJson);
            migrateDamageTilt(modulesJson);
            migrateParticleBudget(modulesJson);
            migrateAutohide(modulesJson);
            migrateHypixelMods(modulesJson);
            migrateUnfocusedCap(modulesJson);
            java.util.Set<String> known = new java.util.HashSet<>();
            for (Module m : ModuleManager.getInstance().getModules()) {
                known.add(m.getName());
            }
            for (java.util.Map.Entry<String, JsonElement> entry : modulesJson.entrySet()) {
                if (!known.contains(entry.getKey()) && entry.getValue() instanceof JsonObject obj) {
                    unmanagedModules.put(entry.getKey(), obj.deepCopy());
                }
            }
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
                // 1.9.1 makes On the default shadow. Soft was the shipped default, so a Soft saved without the marker below was never
                // a choice: it becomes On, once. Saved with the marker, Soft is the player's own pick and stays.
                HudSettings.getInstance().setSoftShadow(hud.get("softShadow").getAsBoolean() && hud.has("shadowOnDefault"));
            }
            if (hud.has("hudFpsLimit")) {
                HudSettings.getInstance().setHudFpsLimit(hud.get("hudFpsLimit").getAsInt());
            }
            if (hud.has("hudFpsCapOnDefault")) {
                if (hud.has("hudFpsCapOptIn")) {
                    HudSettings.getInstance().setHudFpsCapEnabled(hud.get("hudFpsCapOptIn").getAsBoolean());
                } else if (hud.has("hudFpsCapEnabled")) {
                    HudSettings.getInstance().setHudFpsCapEnabled(hud.get("hudFpsCapEnabled").getAsBoolean());
                }
            } else {
                // 1.9.2: HUD FPS cap is on by default at 60. Configs from 1.9.1 and older had hudFpsCapOptIn=false
                // written by default; turn the cap on at 60 for configs that never changed it from the old default.
                if (hud.has("hudFpsCapOptIn")) {
                    boolean optIn = hud.get("hudFpsCapOptIn").getAsBoolean();
                    if (optIn) {
                        HudSettings.getInstance().setHudFpsCapEnabled(true);
                    } else if (HudSettings.getInstance().getHudFpsLimit() == 60) {
                        HudSettings.getInstance().setHudFpsCapEnabled(true);
                    } else {
                        HudSettings.getInstance().setHudFpsCapEnabled(false);
                    }
                } else if (hud.has("hudFpsCapEnabled")) {
                    // 1.4.1-1.4.4 saved their default (on, 60) in every config; uncapped was not an explicit choice.
                    if (HudSettings.getInstance().getHudFpsLimit() == 60) {
                        HudSettings.getInstance().setHudFpsCapEnabled(true);
                    } else {
                        HudSettings.getInstance().setHudFpsCapEnabled(hud.get("hudFpsCapEnabled").getAsBoolean());
                    }
                } else {
                    HudSettings.getInstance().setHudFpsCapEnabled(true);
                    HudSettings.getInstance().setHudFpsLimit(60);
                }
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
            if (hud.get("anchors") instanceof JsonObject anchors) {
                for (String key : anchors.keySet()) {
                    try {
                        JsonObject a = anchors.getAsJsonObject(key);
                        JsonArray at = a.getAsJsonArray("at"), h = a.getAsJsonArray("x"), v = a.getAsJsonArray("y");
                        String to = a.has("to") ? a.get("to").getAsString() : null;
                        HudSettings.getInstance().setAnchor(key, new HudSettings.Anchor(at.get(0).getAsInt(), at.get(1).getAsInt(), to, axis(h), axis(v)));
                    } catch (Exception ignored) {
                    }
                }
            }
            if (!hud.has(HUD_1910_FLAG)) apply1910HudOverride();
            if (!hud.has(F3_1911_FLAG)) apply1911BetterF3Override();
            if (hud.has("hudScale")) HudSettings.getInstance().setHudScale(hud.get("hudScale").getAsInt());
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
            if (hud.has("excludedModules")) {
                HudSettings.getInstance().replaceExcludedModules(readHudNames(hud.get("excludedModules")));
            }
            if (hud.has("excludedExternalMods")) {
                HudSettings.getInstance().replaceExcludedExternalMods(readHudNames(hud.get("excludedExternalMods")));
            }
        }
    }

    /** Before 1.7.1 Discord presence was "Coming soon", so nothing saved for it was the player's choice: it starts from the new defaults. */
    public static void migrateDiscord(JsonObject modules) {
        if (modules.get("DiscordRPC") instanceof JsonObject discord && discord.get("options") instanceof JsonObject options
                && options.has("Share activity")) modules.remove("DiscordRPC");
    }

    /**
     * 1.9.10, once per config (owner): the Action Bar goes back to Minecraft's place over the hotbar, above the held item's name and
     * the armor row (HudDefaults); Potion Effects loses its plate; the Armor HUD shows as hotbar slots attached beside the offhand slot.
     */
    static void apply1910HudOverride() {
        HudSettings.getInstance().clearPosition(com.thelads.core.client.hud.ActionBar.MODULE);
        Module potion = ModuleManager.getInstance().getModule("Potion Effects"), armor = ModuleManager.getInstance().getModule("ArmorHUD");
        if (potion != null && potion.getOption("Background") instanceof ColorOption background) {
            background.setUseGlobal(false);
            background.setColor(0);
        }
        if (armor != null && armor.getOption("Style") instanceof DropdownOption style) style.setIndex(0);
        if (armor != null && armor.getOption("Attach to hotbar") instanceof BoolOption attach) attach.set(true);
    }

    /**
     * 1.9.11, once per config (owner): Better F3 takes its new defaults, the BetterF3 look (its own coloured lines, no plates, shadow,
     * a slide in and out). Whether it is on stays the player's choice.
     */
    static void apply1911BetterF3Override() {
        Module f3 = ModuleManager.getInstance().getModule("BetterF3");
        if (f3 != null) f3.getOptions().forEach(Option::reset);
    }

    /**
     * 1.7.2 turns OldDamageTilt on by default with Directional on; a config from before (no Directional saved) upgrades as the owner
     * chose: Directional off, so the tilt keeps leaning the fixed way; the module on unless the player ever changed it (lastModified 0
     * or missing, as in migrateDynamicLights: an "enabled" saved without a change is only the old default); Intensity, a choice before
     * (Subtle, Normal, Strong), becomes a percentage: Subtle 50, else 100. 1.9.10 renamed the module to Damage Tilt: the old entry
     * moves over unless the new one exists.
     */
    static void migrateDamageTilt(JsonObject modules) {
        JsonElement old = modules.remove(com.thelads.core.client.DamageTilt.OLD_NAME);
        if (old != null && !modules.has(com.thelads.core.client.DamageTilt.MODULE)) modules.add(com.thelads.core.client.DamageTilt.MODULE, old);
        if (!(modules.get(com.thelads.core.client.DamageTilt.MODULE) instanceof JsonObject tilt)) return;
        if (!(tilt.get("options") instanceof JsonObject)) tilt.add("options", new JsonObject());
        JsonObject options = tilt.getAsJsonObject("options");
        if (options.has("Directional")) return;
        options.addProperty("Directional", false);
        if (options.get("Intensity") != null) options.addProperty("Intensity", number(options.get("Intensity")) == 0 ? 50 : 100);
        JsonElement changed = tilt.get("lastModified");
        if (changed == null || changed.isJsonPrimitive() && changed.getAsJsonPrimitive().isNumber() && changed.getAsLong() == 0) tilt.remove("enabled");
    }

    /**
     * 1.8.5 folded ParticleBudget into Particle Core (on by default, with the old quota as its "Limit decorative particles" option,
     * off as ParticleBudget was). A player who had ParticleBudget on keeps the quota on with their own numbers; whatever Particle
     * Core already saved wins, so a config an older client re-saved with the old entry does not undo it.
     */
    public static void migrateParticleBudget(JsonObject modules) {
        if (!(modules.remove("ParticleBudget") instanceof JsonObject budget)) return;
        if (!(modules.get(com.thelads.core.client.ParticleCore.MODULE) instanceof JsonObject)) modules.add(com.thelads.core.client.ParticleCore.MODULE, new JsonObject());
        JsonObject core = modules.getAsJsonObject(com.thelads.core.client.ParticleCore.MODULE);
        if (!(core.get("options") instanceof JsonObject)) core.add("options", new JsonObject());
        JsonObject options = core.getAsJsonObject("options");
        JsonElement saved = budget.get("options");
        if (!options.has(com.thelads.core.client.ParticleCore.LIMIT)) options.addProperty(com.thelads.core.client.ParticleCore.LIMIT, bool(budget.get("enabled")));
        if (saved instanceof JsonObject old) {
            if (old.has("Particles per tick") && !options.has(com.thelads.core.client.ParticleCore.PER_TICK)) options.add(com.thelads.core.client.ParticleCore.PER_TICK, old.get("Particles per tick"));
            if (old.has("Distance") && !options.has(com.thelads.core.client.ParticleCore.DISTANCE)) options.add(com.thelads.core.client.ParticleCore.DISTANCE, old.get("Distance"));
        }
    }

    /**
     * 1.9.11 folded 1.8.9's Unfocused FPS cap into Dynamic FPS (built in there now). A cap the player changed carries its on/off and FPS
     * limit over as Dynamic FPS's on/off and Unfocused FPS, unless Dynamic FPS was changed itself (26.x shares this file).
     */
    public static void migrateUnfocusedCap(JsonObject modules) {
        if (!(modules.remove("UnfocusedFpsCap") instanceof JsonObject cap) || !changed(cap)) return;
        if (modules.get("DynamicFPS") instanceof JsonObject own && changed(own)) return;
        JsonObject dynamic = modules.get("DynamicFPS") instanceof JsonObject saved ? saved : new JsonObject();
        modules.add("DynamicFPS", dynamic);
        if (cap.has("enabled")) dynamic.add("enabled", cap.get("enabled"));
        dynamic.add("lastModified", cap.get("lastModified"));
        JsonElement limit = legacy(cap, "options", "FPS limit");
        if (limit != null) {
            if (!(dynamic.get("options") instanceof JsonObject)) dynamic.add("options", new JsonObject());
            dynamic.getAsJsonObject("options").add("Unfocused FPS", limit);
        }
    }

    /** A module entry the player changed (lastModified set); an "enabled" saved without a change is only the default. */
    private static boolean changed(JsonObject module) {
        JsonElement time = module.get("lastModified");
        return time != null && time.isJsonPrimitive() && time.getAsJsonPrimitive().isNumber() && time.getAsLong() != 0;
    }

    /** 1.9.2 grouped the 6 Hypixel modules into "HYPIXEL Mods": each old module's on/off becomes its switch, its options "<module>: <option>". */
    public static void migrateHypixelMods(JsonObject modules) {
        String[] oldNames = {"Auto GG", "Auto Friend", "Auto Tip", "Auto Play Again", "Auto Who", "Chat Cleanup"};
        boolean found = false;
        for (String n : oldNames) if (modules.has(n)) found = true;
        if (!found) return;

        JsonObject hypixelMods;
        if (modules.has("HYPIXEL Mods") && modules.get("HYPIXEL Mods") instanceof JsonObject hm) {
            hypixelMods = hm;
        } else {
            hypixelMods = new JsonObject();
            hypixelMods.addProperty("enabled", true);
            modules.add("HYPIXEL Mods", hypixelMods);
        }
        JsonObject options;
        if (hypixelMods.has("options") && hypixelMods.get("options") instanceof JsonObject opt) {
            options = opt;
        } else {
            options = new JsonObject();
            hypixelMods.add("options", options);
        }

        for (String oldName : oldNames) {
            JsonElement oldMod = modules.remove(oldName);
            if (oldMod instanceof JsonObject oldModObj) {
                if (oldModObj.has("enabled") && !options.has(oldName)) options.add(oldName, oldModObj.get("enabled"));
                if (oldModObj.has("options") && oldModObj.get("options") instanceof JsonObject oldOpts) {
                    for (String key : oldOpts.keySet()) {
                        String optName = oldName + ": " + key;
                        if (!options.has(optName)) options.add(optName, oldOpts.get(key));
                    }
                }
            }
        }
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
     * 1.9.0 folded the Chat Heads module into the Chat module's options: its on/off and its Position, Detect by name and Keep text
     * aligned options carry over. The old entry is dropped once read; what Chat already saved wins, so a config an older client
     * re-saved with the old entry does not undo it. An older client reading the new file finds no Chat Heads entry and uses its defaults.
     */
    static void migrateChatHeads(JsonObject modules) {
        if (!(modules.remove("Chat Heads") instanceof JsonObject heads)) return;
        if (!(modules.get("Chat") instanceof JsonObject)) modules.add("Chat", new JsonObject());
        JsonObject chat = modules.getAsJsonObject("Chat");
        if (!(chat.get("options") instanceof JsonObject)) chat.add("options", new JsonObject());
        JsonObject options = chat.getAsJsonObject("options");
        carry(heads.get("enabled"), options, com.thelads.core.client.ChatHeads.ENABLED);
        if (!(heads.get("options") instanceof JsonObject old)) return;
        carry(old.get("Position"), options, com.thelads.core.client.ChatHeads.POSITION);
        carry(old.get("Detect by name"), options, com.thelads.core.client.ChatHeads.BY_NAME);
        carry(old.get("Keep text aligned"), options, com.thelads.core.client.ChatHeads.ALIGNED);
    }

    private static void carry(JsonElement value, JsonObject options, String key) {
        if (value != null && !options.has(key)) options.add(key, value);
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

    private static JsonArray ints(int... values) { JsonArray array = new JsonArray(); for (int value : values) array.add(value); return array; }
    private static HudSettings.Axis axis(JsonArray a) {
        int ref = a.get(0).getAsInt(), pin = a.get(1).getAsInt();
        if (ref < 0 || ref > 2 || pin < 0 || pin > 2) throw new IllegalArgumentException("anchor point");
        return new HudSettings.Axis(ref, pin, a.get(2).getAsInt(), a.size() > 3 ? a.get(3).getAsInt() : 0);
    }

    private static JsonObject modules(JsonElement module) { return module instanceof JsonObject object ? object : null; }
    private static boolean bool(JsonElement value) { try { return value != null && value.getAsBoolean(); } catch (RuntimeException e) { return false; } }
    private static int number(JsonElement value) { try { return value == null ? 0 : value.getAsInt(); } catch (RuntimeException e) { return 0; } }

    /**
     * 1.7.0 builds Dynamic Lights in, on as the LambDynamicLights it replaces was. Until then nobody could change the module
     * (its card showed that mod's own settings), so a state saved without a change holds only the old defaults: drop it.
     */
    static void migrateDynamicLights(JsonObject modules) {
        dropUnchanged(modules, "DynamicLights");
        // 1.8.5 builds Not Enough Animations in (1.20.1), on as the mod it replaces was: the same rule.
        dropUnchanged(modules, com.thelads.core.modules.NotEnoughAnimationsModule.NAME);
    }

    /**
     * 1.9.0 defaults Autohide to off: configs saved with the old default (lastModified 0 or missing) migrate
     * to enabled=false; explicit player changes are kept.
     */
    static void migrateAutohide(JsonObject modules) {
        if (!(modules.get("Autohide") instanceof JsonObject autohide)) return;
        JsonElement changed = autohide.get("lastModified");
        if (changed == null || (changed.isJsonPrimitive() && changed.getAsJsonPrimitive().isNumber() && changed.getAsLong() == 0)) {
            autohide.addProperty("enabled", false);
        }
    }

    private static void dropUnchanged(JsonObject modules, String name) {
        JsonElement changed = legacy(modules, name, "lastModified");
        if (modules.get(name) instanceof JsonObject
            && (changed == null || changed.isJsonPrimitive() && changed.getAsJsonPrimitive().isNumber() && changed.getAsLong() == 0))
            modules.remove(name);
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

    public static boolean has197PresetApplied(JsonObject json) {
        return json != null
            && json.has(PRESET_197_FLAG)
            && json.get(PRESET_197_FLAG).isJsonPrimitive()
            && json.get(PRESET_197_FLAG).getAsBoolean();
    }

    /** Resets all modules and HUD settings to the shipped 1.9.7 preset and saves immediately. */
    public static void resetTo197PresetAndSave() {
        unmanagedModules.clear();
        ModuleManager.getInstance().resetToDefaults();
        HudSettings.getInstance().resetToDefaults();
        SkinLayersLodMigration.migrate(null);
        save();
    }

    private static boolean needsMigrationSave(JsonObject json) {
        if (json == null) return false;
        if (!json.has(SkinLayersLodMigration.FLAG) || !json.get(SkinLayersLodMigration.FLAG).getAsBoolean()) {
            return true;
        }
        if (json.get("modules") instanceof JsonObject modules) {
            if (!modules.has(ToggleSprintModule.NAME) && (modules.has("ToggleSprint") || modules.has("ToggleSneak"))) return true;
            if (legacy(modules, "ClientTools", "options", "Chat timestamps") != null || legacy(modules, "HideChatIndicators", "enabled") != null) return true;
            if (modules.has("Chat Heads") || modules.has("ParticleBudget") || modules.has("RenderScale") || modules.has("UnfocusedFpsCap")) return true;
            if (modules.has("ToggleNametags") && !modules.has("Nametags")) return true;
            if (modules.get("DiscordRPC") instanceof JsonObject discord && discord.get("options") instanceof JsonObject opts && opts.has("Share activity")) return true;
            if (modules.has(com.thelads.core.client.DamageTilt.OLD_NAME)) return true;
            if (modules.get(com.thelads.core.client.DamageTilt.MODULE) instanceof JsonObject tilt && (!(tilt.get("options") instanceof JsonObject o) || !o.has("Directional"))) return true;
            for (String oldHypixel : new String[]{"Auto GG", "Auto Friend", "Auto Tip", "Auto Play Again", "Auto Who", "Chat Cleanup"}) {
                if (modules.has(oldHypixel)) return true;
            }
        }
        if (json.get("hud") instanceof JsonObject hud) {
            if (!hud.has("shadowOnDefault") || !hud.has("hudFpsCapOnDefault") || !hud.has(HUD_1910_FLAG) || !hud.has(F3_1911_FLAG)) return true;
        }
        return false;
    }

    public static void load() {
        File configFile = getConfigFile();
        if (!configFile.exists()) {
            if (testConfigFile == null) {
                resetTo197PresetAndSave();
            } else {
                SkinLayersLodMigration.migrate(null);
            }
            return;
        }
        JsonObject loaded;
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            loaded = GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            System.err.println("Failed to load config");
            e.printStackTrace();
            return;
        }
        // 1.9.7 one-time migration: override pre-1.9.7 module & HUD settings into the new 1.9.7 default preset.
        if (!has197PresetApplied(loaded) && (testConfigFile == null || (loaded != null && loaded.has("enforce197PresetMigration")))) {
            resetTo197PresetAndSave();
            return;
        }
        boolean dirty = needsMigrationSave(loaded);
        applyJson(loaded);
        if (dirty && testConfigFile == null) {
            save();
        }
    }

    /** A module's saved on/off, read before modules exist: for features set up once at startup (Jasione). */
    public static boolean savedEnabled(String module, boolean fallback) {
        File configFile = getConfigFile();
        if (!configFile.exists()) return fallback;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (testConfigFile == null && !has197PresetApplied(root)) {
                return fallback;
            }
            JsonElement enabled = legacy(root, "modules", module, "enabled");
            return enabled == null ? fallback : enabled.getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Writes the config now. Anything {@link #saveLater} still holds is older than this snapshot and is dropped. */
    /** Whether this game version already had its one-time Emotes key clash fix (EmoteKeys). */
    public static boolean emoteKeyClashResolved(String version) { return emoteKeysResolved.contains(version); }

    /** Marks this game version's Emotes key clash fix done, once; saves. */
    public static void markEmoteKeyClashResolved(String version) { if (emoteKeysResolved.add(version)) save(); }

    public static void save() {
        String json = GSON.toJson(toJson());
        write(json, sequence.incrementAndGet(), testConfigFile);
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
            latestTargetFile = testConfigFile;
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
        File target;
        long order;
        synchronized (LATER) {
            json = latestJson;
            target = latestTargetFile;
            order = latestOrder;
            latestJson = null;
            latestTargetFile = null;
        }
        if (json != null) write(json, order, target);
    }

    /** Atomic replace; a snapshot older than one already written is skipped, so a late background write never undoes a newer save. */
    private static synchronized void write(String json, long order, File explicitTestFile) {
        if (order < writtenOrder) return;
        writtenOrder = order;
        File configFile = explicitTestFile != null ? explicitTestFile : getConfigFile();
        writeAtomic(configFile, json);

        if (explicitTestFile != null || testConfigFile != null) return;

        // Mirror to shared, global root, local instance, and all other Lads Client instances
        try {
            Path primaryPath = configFile.toPath().toAbsolutePath().normalize();
            for (File target : ClientPaths.getMirrorTargetsForSharedFile("thelads_config.json")) {
                Path targetPath = target.toPath().toAbsolutePath().normalize();
                if (!targetPath.equals(primaryPath)) {
                    writeAtomic(target, json);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void writeAtomic(File destinationFile, String content) {
        if (destinationFile == null) return;
        try {
            Path destination = destinationFile.toPath().toAbsolutePath();
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = null;
            try {
                temporary = Files.createTempFile(parent, ".lads-config-", ".tmp");
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
