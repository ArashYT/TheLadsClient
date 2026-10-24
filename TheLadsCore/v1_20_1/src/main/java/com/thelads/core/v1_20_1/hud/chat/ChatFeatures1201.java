package com.thelads.core.v1_20_1.hud.chat;

import com.thelads.core.client.ChatHistory;
import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import com.thelads.core.v1_20_1.hud.Hud1201;
import com.thelads.core.v1_20_1.hud.Mods1201;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Chat (module), Chat Heads, ClientTools (+ the Clock / Stopwatch / ItemCounter / ReachDisplay HUD feeds), Nicknames, Privacy,
 * Narrator, SecureChatToast and FontMetrics (hud-chat group). Each feature owns a class here; the vanilla hooks are in {@code mixin}.
 */
public final class ChatFeatures1201 {
    /** ponytail: with a chat_heads jar every resize wraps the whole history again, so Infinite History stops here instead of at ChatHistory.CEILING. */
    public static final int WRAPPED_CEILING = 5_000;
    /** SurvivalTweaks Longer Chat History: the history cap when Infinite History is off. */
    public static final int LONGER_HISTORY = 65_536;
    private static Boolean signingMod;

    private ChatFeatures1201() {}

    public static void register() {
        NativeChatHeads1201.register();
        NativeClientTools1201.register();
        ChatTweaks1201.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            NativeClientTools1201.tick();
            NativeNarrator1201.tick();
        });
    }

    /**
     * Infinite History lays out only the lines near the scroll position (ChatHistory). It needs every chat line to take its chat head
     * from the stored message, which Lads' own heads do; a chat_heads jar keeps its own heads on lines it wraps itself, so with that
     * jar the chat is wrapped completely (vanilla's way, through chat_heads' own hooks) and the history ceiling is lower.
     */
    public static boolean lazyHistory() {
        return ChatHistory.infinite() && NativeChatHeads1201.active();
    }

    /** The history cap (100 messages and lines): vanilla's, the ceiling with Infinite History, or {@link #WRAPPED_CEILING} when every line is wrapped on a resize. */
    public static int historyLimit(int vanilla) {
        int limit = ChatHistory.limit(vanilla);
        // SurvivalTweaks' Longer Chat History (26.3 does the same): without Infinite History the 100-message cap becomes 65536.
        if (limit == vanilla && NativeSurvivalTweaks1201.isLongerChatHistoryEnabled()) return LONGER_HISTORY;
        return limit == vanilla || NativeChatHeads1201.active() ? limit : Math.min(limit, WRAPPED_CEILING);
    }

    /**
     * Chat's Hide Signing Indicators: the coloured bars and the modified-message icon next to chat lines and the "chat messages
     * can't be verified" toast are hidden. Stands down for the Chat Signing Hider mod.
     */
    public static boolean hideChatSigning() {
        if (signingMod == null) signingMod = Mods1201.loaded("chatsigninghider");
        return !signingMod && Hud1201.enabled("Chat") && Hud1201.bool("Chat", "Hide Signing Indicators", true);
    }
}
