package com.thelads.core.v1_20_1.perf;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.thelads.core.perf.ChatAsync;
import com.thelads.core.perf.OrderedAsync;
import com.thelads.core.v1_20_1.hud.chat.NativeChatHeads1201;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.ComponentCollector;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async Chat: the line wrapping of a chat message runs on the Lads-Chat worker, the message is added on the main thread
 * in arrival order. The worker only reads the message and a thread-safe width cache; ChatComponent lists, the glyph
 * cache and everything else stay on the main thread. The result is used only when the wrapped text and width are exactly
 * what vanilla would wrap (identity of the component, same width and font); otherwise the vanilla wrap runs.
 */
public final class ChatAsync1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final FormattedCharSequence INDENT = FormattedCharSequence.codepoint(32, Style.EMPTY);

    private ChatAsync1201() {}

    /** The wrap of one message, or the glyph widths the worker did not know yet (then lines is null). */
    public static final class Prepared {
        final FormattedText source;
        final int width;
        final Font font;
        final boolean colors;
        final List<FormattedCharSequence> lines;
        final List<Object[]> misses;

        Prepared(FormattedText source, int width, Font font, boolean colors, List<FormattedCharSequence> lines, List<Object[]> misses) {
            this.source = source;
            this.width = width;
            this.font = font;
            this.colors = colors;
            this.lines = lines;
            this.misses = misses;
        }

        public boolean matches(FormattedText text, int w, Font f) {
            return lines != null && text == source && w == width && f == font && Minecraft.getInstance().options.chatColors().get() == colors;
        }

        public List<FormattedCharSequence> lines() {
            return lines;
        }
    }

    private static OrderedAsync queue() {
        OrderedAsync q = ChatAsync.queueIfAny();
        if (q == null) {
            q = ChatAsync.queue(t -> LOGGER.warn("[Lads] Async Chat worker failed, using the vanilla path for that message", t));
            LOGGER.info("[Lads] Async Chat: line wrapping runs on the Lads-Chat worker (-Dlads.asyncChat=false to disable)");
        }
        return q;
    }

    /** Called from the hooked ChatComponent.addMessage(Component, MessageSignature, GuiMessageTag) on the main thread. */
    public static void add(ChatComponent chat, Component content, MessageSignature signature, GuiMessageTag tag) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        boolean colors = mc.options.chatColors().get();
        int width = Mth.floor((double) chat.getWidth() / chat.getScale());
        if (tag != null && tag.icon() != null) width -= tag.icon().width + 4 + 2;
        final int w = width;
        final GameProfile sender = NativeChatHeads1201.signedSender;
        queue().submit(() -> wrap(content, w, font, colors), p -> {
            if (p != null && p.misses != null) resolve(font, p.misses);
            reenter(() -> chat.addMessage(content, signature, tag), p, sender);
        }, mc);
    }

    /** Called from the hooked ChatComponent.deleteMessage while adds are queued: the delete must not overtake them. */
    public static void delete(ChatComponent chat, MessageSignature signature) {
        queue().submit(() -> null, p -> reenter(() -> chat.deleteMessage(signature), null, null), Minecraft.getInstance());
    }

    public static boolean pending() {
        OrderedAsync q = ChatAsync.queueIfAny();
        return q != null && q.pending() > 0;
    }

    private static void reenter(Runnable action, Prepared prepared, GameProfile sender) {
        GameProfile previous = NativeChatHeads1201.signedSender;
        if (sender != null) NativeChatHeads1201.signedSender = sender;
        ChatAsync.applying = true;
        ChatAsync.current = prepared != null && prepared.lines != null ? prepared : null;
        try {
            action.run();
        } finally {
            ChatAsync.applying = false;
            ChatAsync.current = null;
            NativeChatHeads1201.signedSender = previous;
        }
    }

    /** Main thread: measure the glyphs the worker was missing with the vanilla splitter and remember them. */
    private static void resolve(Font font, List<Object[]> misses) {
        for (Object[] miss : misses) {
            Style style = (Style) miss[0];
            int codepoint = (Integer) miss[1];
            float width = font.getSplitter().stringWidth(FormattedText.of(new String(Character.toChars(codepoint)), style));
            ChatAsync.putWidth(style.getFont(), style.isBold(), codepoint, width);
        }
    }

    /** Worker thread: the same wrap as ComponentRenderUtils.wrapComponents, over the thread-safe width cache. */
    private static Prepared wrap(Component text, int width, Font font, boolean colors) {
        List<Object[]> misses = new ArrayList<>();
        StringSplitter splitter = new StringSplitter((codepoint, style) -> {
            Float known = ChatAsync.width(style.getFont(), style.isBold(), codepoint);
            if (known == null) {
                misses.add(new Object[] {style, codepoint});
                return 0f;
            }
            return known;
        });
        ComponentCollector collector = new ComponentCollector();
        text.visit((style, string) -> {
            collector.append(FormattedText.of(colors ? string : ChatFormatting.stripFormatting(string), style));
            return Optional.empty();
        }, Style.EMPTY);
        List<FormattedCharSequence> lines = Lists.newArrayList();
        Language language = Language.getInstance();
        splitter.splitLines(collector.getResultOrEmpty(), width, Style.EMPTY, (line, indented) ->
            lines.add(indented ? FormattedCharSequence.composite(INDENT, language.getVisualOrder(line)) : language.getVisualOrder(line)));
        if (misses.isEmpty() && lines.isEmpty()) lines.add(FormattedCharSequence.EMPTY);
        return new Prepared(text, width, font, colors, misses.isEmpty() ? lines : null, misses.isEmpty() ? null : misses);
    }
}
