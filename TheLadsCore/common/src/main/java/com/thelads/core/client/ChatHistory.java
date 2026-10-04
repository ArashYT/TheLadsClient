package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The Chat module's Infinite History (1.7.0, on by default): no 100-line cap, and after a resize or a deleted message only the lines
 * near the scroll position are laid out again; older messages are laid out as the chat scrolls up to them. Every version's chat keeps
 * messages and laid-out lines newest first, a message's last line first, and only draws the lines on screen.
 */
public final class ChatHistory {
    /** ponytail: hard ceiling on kept messages and laid-out lines so a spamming server cannot exhaust memory (~1 KB each). */
    public static final int CEILING = 100_000;
    /** Lines laid out beyond the visible page, so scrolling up never waits on a layout. */
    public static final int AHEAD = 100;

    private ChatHistory() {}

    public static boolean infinite() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption("Infinite History") instanceof BoolOption option && option.get();
    }

    /** The vanilla history cap, or the ceiling with Infinite History. */
    public static int limit(int vanilla) {
        return infinite() ? CEILING : vanilla;
    }

    public static boolean shadow() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat == null || !chat.isEnabled() || !(chat.getOption("Text Shadow") instanceof BoolOption option) || option.get();
    }

    /**
     * Lays out messages from index laidOut onwards (older ones) at the end of lines until it holds wanted lines or every message is
     * laid out; split gives a message's lines first to last. Returns the new laidOut.
     */
    public static <M, L> int layOut(List<M> messages, int laidOut, List<L> lines, int wanted, Predicate<M> visible, Function<M, List<L>> split) {
        while (lines.size() < wanted && laidOut < messages.size()) {
            M message = messages.get(laidOut++);
            if (!visible.test(message)) continue;
            List<L> parts = split.apply(message);
            for (int i = parts.size() - 1; i >= 0; i--) lines.add(parts.get(i));
        }
        return laidOut;
    }
}
