package com.thelads.core.config;

import java.util.HashMap;
import java.util.Map;

/** Runtime capabilities are supplied by actual game integrations, never by saved toggles. */
public final class ModuleSupport {
    public record Status(boolean configurable, String label, String description) {}
    private static final Map<String, Status> STATUS = new HashMap<>();
    private static final Map<String, String> EXTERNAL_IDS = new HashMap<>();
    /** Upstream mod id each module wraps or replaces, kept whether or not that mod is installed (launcher catalog). */
    private static final Map<String, String> EXTERNAL_MOD_IDS = new HashMap<>();
    private static final java.util.Set<String> BUILT_IN = new java.util.HashSet<>(), SETTINGS_ONLY = new java.util.HashSet<>();
    private static long revision;
    private static final Status PENDING = new Status(false, "Unavailable", "This feature is not connected to this game version yet.");
    private ModuleSupport() {}
    public static void registerBuiltIn(String... names) {
        for (String name : names) {
            STATUS.put(name, new Status(true, "Built in", "Included in The Lads Client. Changes apply immediately."));
            EXTERNAL_IDS.remove(name);
            BUILT_IN.add(name);
            revision++;
        }
    }
    /** Built in, with what this game version cannot do (the status detail the launcher shows), e.g. a mechanic it lacks. */
    public static void registerBuiltInLimited(String name, String limits) {
        registerBuiltIn(name);
        STATUS.put(name, new Status(true, "Built in", "Included in The Lads Client. Changes apply immediately. " + limits));
    }
    /** Built in and set up once while the game starts, so a toggle takes effect after a restart. */
    public static void registerBuiltInRestart(String name) {
        registerBuiltIn(name);
        STATUS.put(name, new Status(true, "Built in", "Included in The Lads Client. Changes apply after a restart."));
    }
    /** Built in with no on/off switch: the card opens its settings (e.g. Flashback's folder and export options). */
    public static void registerSettingsOnly(String name) {
        registerBuiltIn(name);
        SETTINGS_ONLY.add(name);
        STATUS.put(name, new Status(true, "Built in", "Included in The Lads Client and always on. Its settings apply immediately."));
    }
    public static boolean isSettingsOnly(String name) { return BUILT_IN.contains(name) && SETTINGS_ONLY.contains(name); }
    public static void registerExternal(String name, String modName, boolean installed) {
        BUILT_IN.remove(name);
        EXTERNAL_IDS.remove(name);
        revision++;
        STATUS.put(name, new Status(false, installed ? "Installed mod" : "Mod required", installed
            ? modName + " is included in this client. Open its settings below. Loading or unloading the engine requires a restart."
            : "Install a compatible " + modName + " release for this Minecraft version, then restart the game."));
    }
    public static void registerExternal(String name, String modName, String modId, boolean installed) {
        registerExternal(name, modName, installed);
        if (installed) EXTERNAL_IDS.put(name, modId); else EXTERNAL_IDS.remove(name);
        EXTERNAL_MOD_IDS.put(name, modId);
    }
    public static String externalModId(String name) { return EXTERNAL_MOD_IDS.get(name); }
    public static String getExternalId(String name) { return EXTERNAL_IDS.get(name); }
    public static boolean isBuiltIn(String name) { return BUILT_IN.contains(name); }
    /** Same rule as the in-game card toggle: only built-in modules that are not settings-only. */
    public static boolean isToggleable(String name) { return isBuiltIn(name) && !isSettingsOnly(name); }
    /** Catalog support kind: builtIn, external (wraps an upstream mod), unavailable, or pending (never registered here). */
    public static String support(String name) {
        if (BUILT_IN.contains(name)) return "builtIn";
        Status status = STATUS.get(name);
        if (status == null) return "pending";
        return status.label().equals("Unavailable") ? "unavailable" : "external";
    }
    public static long revision() { return revision; }
    public static java.util.Set<String> externalIds() { return java.util.Set.copyOf(EXTERNAL_IDS.values()); }
    public static void registerUnavailable(String name, String reason) {
        BUILT_IN.remove(name); EXTERNAL_IDS.remove(name); revision++;
        STATUS.put(name, new Status(false, "Unavailable", reason));
    }
    public static Status get(String name) { return STATUS.getOrDefault(name, PENDING); }
}
