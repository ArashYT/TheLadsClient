package com.thelads.core.modules;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.TextOption;

/**
 * Auto Text (Lunar's AutoText): a key per message; pressing it in game sends the message, or runs it when it starts with "/", as if
 * typed. Keys are set in Controls (each "key" row shows its key and opens Controls). Keys that send chat are automation, so
 * HypixelSafetyManager gates the module: off by default, and off on servers that ban it or whose rules are unknown. At most one
 * message a second, so a held or bouncing key never floods the chat.
 */
public final class AutoTextModule extends Module {
    public static final String NAME = "Auto Text";
    public static final int SLOTS = 6;
    static final long GAP = 1_000;
    public final TextOption[] messages = new TextOption[SLOTS];
    public final ActionOption[] keys = new ActionOption[SLOTS];
    private long sentAt = Long.MIN_VALUE / 2;

    public AutoTextModule() {
        super(NAME, "Send a chat message or run a /command with one key. Set each message here and its key in Controls. "
            + "Off on servers that don't allow macros or whose rules are unknown.");
        for (int i = 0; i < SLOTS; i++) {
            messages[i] = addOption(new TextOption("Message " + (i + 1), ""));
            keys[i] = addOption(new ActionOption("Message " + (i + 1) + " key", "Controls"));
        }
        setEnabled(false);
    }

    public static AutoTextModule get() {
        return ModuleManager.getInstance().getModule(NAME) instanceof AutoTextModule module ? module : null;
    }

    /** Message {@code slot}'s key was pressed in game: its text to send, or null (off, blocked, empty or too soon). */
    public String pressed(int slot, long now) {
        if (!isEnabled() || slot < 0 || slot >= SLOTS) return null;
        String text = messages[slot].getValue().trim();
        if (text.isEmpty() || now - sentAt < GAP) return null;
        sentAt = now;
        return text;
    }

    /** Sends message {@code slot} when its key is pressed in game (client thread). */
    public static void press(int slot) {
        AutoTextModule module = get();
        String text = module == null ? null : module.pressed(slot, System.currentTimeMillis());
        if (text != null) LadsGameBridge.get().sendChatLine(text);
    }
}
