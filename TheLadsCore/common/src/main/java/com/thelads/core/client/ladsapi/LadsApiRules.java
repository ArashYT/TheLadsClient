package com.thelads.core.client.ladsapi;

import com.thelads.core.client.HypixelSafetyManager;
import java.util.Locale;
import java.util.Set;

/**
 * Fixed client-side limits on what a LadsClientAPI server may do (PLAN rule 4). Blocking and limiting always work; these lists only
 * stop servers from forcing things on. Names are the registered module names, compared without case, spaces or punctuation.
 */
public final class LadsApiRules {
    /** Automation: sends clicks/packets/chat the player didn't make, or moves for them. Never forced on; only a by-name "allow" lifts it. */
    private static final Set<String> AUTOMATION = Set.of("stackrefill", "usewhilemining", "autoreconnect", "mousetweaks", "keepuiopen",
        "elytraswap", "inventorymove", "armorswap", "containersorting", "autotext", "creativeflyboost", "mlgcobweb", "servernotificationactions");

    /** Never forced on, besides automation: privacy (Discord, cosmetics relay, voice chat, replays, screenshots), input, files, window, threads. */
    private static final Set<String> NEVER_FORCED = Set.of("discordrpc", "cosmetics", "togglesprintsneak", "voicechat", "voicechatgroup",
        "flashbacksettings", "betterscreenshots", "clienttools", "resourcepacks", "texturepacks", "threads", "performance",
        "borderlessfullscreen", "betterresolution");

    /** Options of other modules a server never sets or limits: Translate Chat sends every chat line to Google ("module.option"). */
    private static final Set<String> NEVER_FORCED_OPTIONS = Set.of("chat.translatechat", "chat.translateto");

    private LadsApiRules() {}

    public static boolean isAutomation(String moduleName) {
        return moduleName != null && AUTOMATION.contains(key(moduleName));
    }

    /**
     * Whether this module is gated by the client's anti-ban / safety rules (HypixelSafetyManager).
     * If a server policy allows it by name, the join toast reports it as an unlocked module.
     */
    public static boolean isGated(String moduleName) {
        if (moduleName == null) return false;
        return HypixelSafetyManager.isRiskyModule(moduleName)
            || HypixelSafetyManager.isGatedHypixelModule(moduleName)
            || HypixelSafetyManager.isDisallowedModule(moduleName)
            || AUTOMATION.contains(key(moduleName));
    }

    /** A server may not use "on" for this module (it may still block or allow it). */
    public static boolean isNeverForced(String moduleName) {
        if (moduleName == null) return false;
        return AUTOMATION.contains(key(moduleName))
            || NEVER_FORCED.contains(key(moduleName))
            || HypixelSafetyManager.isRiskyModule(moduleName)
            || HypixelSafetyManager.isGatedHypixelModule(moduleName);
    }

    /** A server may not touch this option (its rules are ignored and reported): every option of a never-forced module, and privacy ones. */
    public static boolean isNeverForced(String moduleName, String optionName) {
        if (moduleName == null) return false;
        String modKey = key(moduleName);
        return AUTOMATION.contains(modKey)
            || NEVER_FORCED.contains(modKey)
            || (optionName != null && NEVER_FORCED_OPTIONS.contains(modKey + "." + key(optionName)));
    }

    /** "Toggle Sprint & Sneak" -> "togglesprintsneak". */
    private static String key(String name) {
        return name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
