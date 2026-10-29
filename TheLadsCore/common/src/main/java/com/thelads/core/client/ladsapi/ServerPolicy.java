package com.thelads.core.client.ladsapi;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import java.util.Collections;
import java.util.Map;

/**
 * The current server's rules: one immutable snapshot, or none. Read by Module/Option getters every frame, so lookups never allocate.
 * Only ever in memory: config files keep the player's own values, so leaving, a crash or a server switch restores them.
 *
 * FROZEN INTERFACE (phase 0): option hooks (lane HOOKS), GUI (lane GUI) and HypixelSafetyManager call exactly these.
 * Lane CORE builds snapshots from policy messages (and implements the bodies marked below).
 */
public final class ServerPolicy {
    private static volatile ServerPolicy active;

    private final String serverName;
    private final ModuleState unlisted;
    private final Map<String, ModuleState> modules;
    private final Map<String, Map<String, OptionRule>> options;
    private final int changedCount;
    private final String notice;

    ServerPolicy(String serverName, ModuleState unlisted, Map<String, ModuleState> modules,
                 Map<String, Map<String, OptionRule>> options, int changedCount, String notice) {
        this.serverName = serverName == null ? "" : serverName;
        this.unlisted = unlisted == null ? ModuleState.INHERIT : unlisted;
        this.modules = modules == null ? Collections.emptyMap() : Collections.unmodifiableMap(modules);
        this.options = options == null ? Collections.emptyMap() : Collections.unmodifiableMap(options);
        this.changedCount = changedCount;
        this.notice = notice == null ? "" : notice;
    }

    /** The same rules without any by-name "allow" (a 1.8.9 dimension change: maybe another backend), or this when there is none. */
    ServerPolicy withoutAllows() {
        if (!modules.containsValue(ModuleState.ALLOW)) return this;
        Map<String, ModuleState> kept = new java.util.HashMap<>(modules);
        kept.values().removeIf(state -> state == ModuleState.ALLOW);
        return new ServerPolicy(serverName, unlisted, kept, options, changedCount, notice);
    }

    /** The rules in force, or null (no LadsClientAPI server, or not yet received). One volatile read: the fast path for every getter. */
    public static ServerPolicy active() {
        return active;
    }

    /** Lane CORE: installs a validated snapshot (null clears). */
    static void set(ServerPolicy policy) {
        active = policy;
    }

    /** The server's display name from its hello ("Lads SMP"). */
    public String serverName() {
        return serverName;
    }

    public int changedCount() {
        return changedCount;
    }

    public String notice() {
        return notice;
    }

    public boolean hasOptionRules(String moduleName) {
        if (moduleName == null) return false;
        Map<String, OptionRule> opts = options.get(moduleName);
        return opts != null && !opts.isEmpty();
    }

    /** The module's state; INHERIT when not mentioned (after applying "unlisted"). Exact registered module name. */
    public ModuleState module(String moduleName) {
        if (moduleName == null || "Server Control".equalsIgnoreCase(moduleName)) {
            return ModuleState.INHERIT;
        }
        ModuleState state = modules.get(moduleName);
        // One mod, two names: "Minimap" is the module players see, "XaeroMinimap" its hidden legacy name (PROTOCOL.md's example).
        // A rule on either covers both, or blocking "XaeroMinimap" left the real minimap on screen (1.9.19 QA, 26.3).
        if (state == null && "Minimap".equals(moduleName)) state = modules.get("XaeroMinimap");
        else if (state == null && "XaeroMinimap".equals(moduleName)) state = modules.get("Minimap");
        if (state != null) {
            return state;
        }
        if (unlisted == ModuleState.BLOCK) {
            return ModuleState.BLOCK;
        }
        // Anti-ban: "unlisted: allow" is the client default too; lifting a safety block takes the module's name
        return ModuleState.INHERIT;
    }

    /** The option's rule, or null. Exact registered module and option names. */
    public OptionRule option(String moduleName, String optionName) {
        if (moduleName == null || optionName == null || "Server Control".equalsIgnoreCase(moduleName)) {
            return null;
        }
        Map<String, OptionRule> opts = options.get(moduleName);
        return opts == null ? null : opts.get(optionName);
    }

    /** A forced change the player turned off ("Let servers change my Lads settings"): its module is "on" or its rule forces a value. */
    public boolean refusesChanges() {
        try {
            Module sc = ModuleManager.getInstance().getModule("Server Control");
            if (sc != null) {
                Option opt = sc.getOption("Let servers change my Lads settings");
                if (opt instanceof BoolOption b) {
                    return !b.getOwn();
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ---- helpers for the option getters (lane HOOKS calls these; lane CORE implements). Each returns `own` when no rule applies. ----

    public static boolean applyBool(String module, String option, boolean own) {
        ServerPolicy policy = active;
        if (policy == null) return own;
        OptionRule rule = policy.option(module, option);
        if (rule == null || rule.bool == null) return own;
        if (policy.refusesChanges()) return own;
        return rule.bool;
    }

    /** Clamps into the option's own [min, max] and step after applying the rule. */
    public static double applySlider(String module, String option, double own, double min, double max, double step) {
        ServerPolicy policy = active;
        if (policy == null) return own;
        OptionRule rule = policy.option(module, option);
        if (rule == null) return own;
        if (!Double.isNaN(rule.value) && !policy.refusesChanges()) {
            return rule.value;
        }
        double lower = Double.isNaN(rule.min) ? min : Math.max(min, rule.min);
        double upper = Double.isNaN(rule.max) ? max : Math.min(max, rule.max);
        if (lower > upper) {
            lower = min;
            upper = max;
        }
        double v = own;
        if (v < lower) v = lower;
        if (v > upper) v = upper;
        if (step > 0) {
            v = min + Math.round((v - min) / step) * step;
            if (v > upper) v = upper;
            if (v < lower) v = lower;
        }
        return v;
    }

    /** Returns an index into {@code choices}. */
    public static int applyDropdown(String module, String option, int ownIndex, String[] choices) {
        ServerPolicy policy = active;
        if (policy == null || choices == null || choices.length == 0) return ownIndex;
        OptionRule rule = policy.option(module, option);
        if (rule == null) return ownIndex;
        if (rule.choice != null && !policy.refusesChanges()) {
            for (int i = 0; i < choices.length; i++) {
                if (choices[i].equals(rule.choice)) return i;
            }
            return ownIndex;
        }
        if (rule.choices != null && rule.choices.length > 0) {
            if (ownIndex >= 0 && ownIndex < choices.length) {
                String ownChoice = choices[ownIndex];
                for (String allowed : rule.choices) {
                    if (allowed.equals(ownChoice)) return ownIndex;
                }
            }
            for (String allowed : rule.choices) {
                for (int i = 0; i < choices.length; i++) {
                    if (choices[i].equals(allowed)) return i;
                }
            }
        }
        return ownIndex;
    }

    public static int applyColor(String module, String option, int ownArgb) {
        ServerPolicy policy = active;
        if (policy == null) return ownArgb;
        OptionRule rule = policy.option(module, option);
        if (rule == null || rule.color == null) return ownArgb;
        if (policy.refusesChanges()) return ownArgb;
        return rule.color;
    }
}
