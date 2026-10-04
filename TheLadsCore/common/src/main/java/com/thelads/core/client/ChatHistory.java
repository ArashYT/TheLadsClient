package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    /**
     * A Chat Heads jar a player added on 26.x (Lads' own Chat Heads stand down for it; they keep the head on the message, so lazy
     * layout needs nothing for them) gives each new chat line ChatHeads.getLineData(): the message's own head only while its
     * refreshTrimmedMessages hook sets refreshing, otherwise the newest message's pending head. Lazy layout skips that method, so it
     * sets the same state around each message it splits. Null without that jar.
     */
    private static final Field HEADS_REFRESHING, HEADS_DATA;
    private static final Method HEAD_OF;

    static {
        Field refreshing = null, data = null;
        Method head = null;
        try {
            Class<?> heads = Class.forName("dzwdz.chat_heads.ChatHeads");
            refreshing = heads.getField("refreshing");
            data = heads.getField("refreshingLineData");
            head = Class.forName("dzwdz.chat_heads.mixininterface.HeadRenderable").getMethod("chatheads$getHeadData");
        } catch (ReflectiveOperationException | LinkageError absent) {
            head = null;
        }
        HEADS_REFRESHING = refreshing;
        HEADS_DATA = data;
        HEAD_OF = head;
    }

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
            List<L> parts = split(message, split);
            for (int i = parts.size() - 1; i >= 0; i--) lines.add(parts.get(i));
        }
        return laidOut;
    }

    private static <M, L> List<L> split(M message, Function<M, List<L>> split) {
        if (HEAD_OF == null || !HEAD_OF.getDeclaringClass().isInstance(message)) return split.apply(message);
        try {
            HEADS_DATA.set(null, HEAD_OF.invoke(message));
            HEADS_REFRESHING.setBoolean(null, true);
            try {
                return split.apply(message);
            } finally {
                HEADS_REFRESHING.setBoolean(null, false);
            }
        } catch (ReflectiveOperationException e) {
            return split.apply(message); // never after the split ran: resetting a field set a moment before cannot fail
        }
    }
}
