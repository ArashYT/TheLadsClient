package com.thelads.core.v26_2.feature;

import com.google.common.collect.Lists;
import com.thelads.core.perf.ChatAsync;
import com.thelads.core.perf.OrderedAsync;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.ComponentCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async Chat: the line wrapping of a new chat message runs on the Lads-Chat worker; the lines are added to the chat on the
 * main thread in arrival order. The worker only reads the message and a thread-safe width cache. The result is used only
 * when the wrapped text, width and font are exactly what vanilla would wrap; otherwise the vanilla wrap runs.
 */
public final class ChatAsync26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final FormattedCharSequence INDENT = FormattedCharSequence.codepoint(32, Style.EMPTY);

    private ChatAsync26() {}

    /** The wrap of one message, or null lines when the worker lacked glyph widths. */
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

    /** Main thread: wrap content at width on the worker, then run display (the vanilla layout call) in arrival order. */
    public static void add(Component content, int width, Runnable display) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        boolean colors = mc.options.chatColors().get();
        queue().submit(() -> wrap(content, width, font, colors), p -> {
            if (p != null && p.misses != null) resolve(font, p.misses);
            ChatAsync.applying = true;
            ChatAsync.current = p != null && p.lines != null ? p : null;
            try {
                display.run();
            } finally {
                ChatAsync.applying = false;
                ChatAsync.current = null;
            }
        }, mc);
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
