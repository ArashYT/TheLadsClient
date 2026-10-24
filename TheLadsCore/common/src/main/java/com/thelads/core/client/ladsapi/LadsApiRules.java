package com.thelads.core.client.ladsapi;

import java.util.Locale;
import java.util.Set;

/**
 * Fixed client-side limits on what a LadsClientAPI server may do (PLAN rule 4). Blocking and limiting always work; these lists only
 * stop servers from forcing things on. Names are the registered module names, compared without case.
 */
public final class LadsApiRules {
    /** Automation: sends clicks/packets the player didn't make. "unlisted: allow" never lifts these; only a by-name "allow" does. */
    private static final Set<String> AUTOMATION = Set.of("stackrefill", "usewhilemining", "autoreconnect", "mousetweaks", "keepuiopen");

    /** Never forced on: automation, privacy (Discord, cosmetics relay, voice chat, replays, screenshots), input, files, window, threads. */
    private static final Set<String> NEVER_FORCED = Set.of("stackrefill", "usewhilemining", "autoreconnect", "mousetweaks", "keepuiopen",
        "discordrpc", "cosmetics", "togglesprint", "voice chat", "voice chat group", "flashback settings", "betterscreenshots", "clienttools",
        "resourcepacks", "texturepacks", "threads", "performance", "borderlessfullscreen", "betterresolution");

    private LadsApiRules() {}

    public static boolean isAutomation(String moduleName) {
        return moduleName != null && AUTOMATION.contains(moduleName.toLowerCase(Locale.ROOT));
    }

    /** A server may not use "on" for this module (it may still block or allow it). */
    public static boolean isNeverForced(String moduleName) {
        return moduleName != null && NEVER_FORCED.contains(moduleName.toLowerCase(Locale.ROOT));
    }
}
