package com.thelads.core.v1_20_1.hud.chat;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.ChatHeads;
import com.thelads.core.client.Nicknames;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.hud.Mods1201;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Chat Heads on 1.20.1. A message gets its sender's skin as it is added (ChatHeadsComponentMixin; signed chat names the sender
 * through ChatHeadsListenerMixin, every other message is matched by {@link ChatHeads#match} against the tab-list names), the
 * stored {@link GuiMessage} and each of its wrapped {@link GuiMessage.Line}s keep it (ChatDataMixin) and the first visible line
 * draws it, just before the sender's name (Position "Before name") or at the start of the line. 1.20.1's GuiMessage.Line has no
 * parent message, so every line carries the head itself, plus whether it is its message's first line. A chat_heads jar in the
 * game (Zero SMP ships one) keeps chat to itself: the module is then registered as that mod's and nothing here draws.
 */
public final class NativeChatHeads1201 {
    /** What a GuiMessage and each of its lines remember of the sender (ChatDataMixin). */
    public interface Data {
        Supplier<ResourceLocation> lads$head();
        /** Code points of the line's text without formatting codes before the sender's name; only the first line is searched. */
        int lads$at();
        /** Whether this is the first wrapped line of its message (always false on a GuiMessage itself). */
        boolean lads$first();
        void lads$set(Supplier<ResourceLocation> head, int at, boolean first);
    }

    private static boolean active;
    /** The sender of the signed chat message being added (ChatHeadsListenerMixin). */
    public static GameProfile signedSender;
    /** The message being wrapped (a new one, or a stored one wrapped again): what its lines and GuiMessage get. */
    private static Supplier<ResourceLocation> pendingHead;
    private static int pendingAt;
    private static boolean pendingFirst = true;

    private NativeChatHeads1201() {}

    public static void register() {
        if (Mods1201.loaded("chat_heads")) {
            ModuleSupport.registerExternal(ChatHeads.NAME, "Chat Heads", "chat_heads", true);
            return;
        }
        active = true;
        ModuleSupport.registerBuiltIn(ChatHeads.NAME);
    }

    private static Data data(Object recordInstance) { return (Data) recordInstance; }

    /** A message about to be wrapped: finds its sender (signed, or by the names in its text) and keeps it as the pending one. */
    public static void prepare(Component content) {
        pendingHead = null;
        pendingAt = 0;
        pendingFirst = true;
        if (!active || !ChatHeads.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        var connection = mc.getConnection();
        if (connection == null) return;
        String text = content.getString();
        Map<String, String> nicknames = Nicknames.active(mc.getUser().getName());
        if (signedSender != null) {
            GameProfile sender = signedSender;
            PlayerInfo info = connection.getPlayerInfo(sender.getId());
            if (info != null) pendingHead = info::getSkinLocation;
            else {
                ResourceLocation[] skin = new ResourceLocation[1];
                pendingHead = () -> skin[0] != null ? skin[0] : (skin[0] = Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(sender));
            }
            // The server named the sender; the head goes before the first of that player's names in the line ("<Steve> hi").
            Map<String, Boolean> names = new HashMap<>();
            Component display = info == null ? null : info.getTabListDisplayName();
            ChatHeads.names(names, Boolean.TRUE, sender.getName(), display == null ? null : display.getString(), nicknames);
            ChatHeads.Match<Boolean> match = ChatHeads.match(text, names);
            if (match != null) pendingAt = match.at();
        } else if (ChatHeads.byName()) {
            Map<String, PlayerInfo> names = new HashMap<>();
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                Component display = info.getTabListDisplayName();
                ChatHeads.names(names, info, info.getProfile().getName(), display == null ? null : display.getString(), nicknames);
            }
            ChatHeads.Match<PlayerInfo> match = ChatHeads.match(text, names);
            if (match != null) {
                pendingHead = match.player()::getSkinLocation;
                pendingAt = match.at();
            }
        }
    }

    /** A stored message is wrapped again (resize, chat setting): its lines get the sender it arrived with. */
    public static void rewrap(Object message) {
        Data stored = data(message);
        pendingHead = stored.lads$head();
        pendingAt = stored.lads$at();
        pendingFirst = true;
    }

    /** The pending message has been wrapped completely. */
    public static void done() {
        pendingHead = null;
        pendingAt = 0;
        pendingFirst = true;
    }

    /** A new GuiMessage.Line or GuiMessage of the pending message (ChatHeadsComponentMixin hooks the list add that stores it). */
    public static Object stamp(Object created) {
        if (created instanceof Data stamped) {
            boolean line = created instanceof GuiMessage.Line;
            stamped.lads$set(pendingHead, pendingAt, line && pendingFirst);
            if (line) pendingFirst = false;
        }
        return created;
    }

    /** Chat pixels a message wraps narrower: its head's room (or, Start of line with Keep text aligned, every message's). */
    public static int wrapOffset() {
        return active ? ChatHeads.offset(pendingHead != null) : 0;
    }

    /** Chat pixels the message's text moves right (and wraps narrower). */
    public static int offset(Object recordInstance) {
        return active ? ChatHeads.offset(data(recordInstance).lads$head() != null) : 0;
    }

    /** Whether this line is the first of its message. */
    public static boolean first(GuiMessage.Line line) {
        return data(line).lads$first();
    }

    /** Chat pixels this line's text moves right after the head (Before name: the first line only; its text before the name stays). */
    public static int shift(GuiMessage.Line line) {
        if (!active) return 0;
        Data data = data(line);
        int offset = ChatHeads.offset(data.lads$head() != null);
        return offset == 0 || !ChatHeads.beforeName() || data.lads$first() ? offset : 0;
    }

    /** True once after the module, Position or Keep text aligned changed: chat must wrap its lines again. */
    public static boolean layoutChanged() {
        return active && ChatHeads.layoutChanged();
    }

    /** Code points of the first line before the head: the sender's name, or 0 when the name is not on that line. */
    public static int at(GuiMessage.Line line, FormattedCharSequence text) {
        int at = data(line).lads$at(), length[] = {0};
        if (at <= 0) return 0;
        text.accept((position, style, codePoint) -> ++length[0] <= at);
        return length[0] > at ? at : 0;
    }

    /** Code points {@code from} (inclusive) to {@code to} (exclusive) of a line, each keeping its own style. */
    public static FormattedCharSequence slice(FormattedCharSequence line, int from, int to) {
        return sink -> {
            int[] index = {0};
            return line.accept((position, style, codePoint) -> {
                int at = index[0]++;
                return at < from || at >= to || sink.accept(at - from, style, codePoint);
            });
        };
    }

    /** The head with its left edge at {@code x} and its top at {@code y} (the text's), faded with the text (alpha 0-255). */
    public static void draw(GuiGraphics graphics, GuiMessage.Line line, int x, int y, int alpha) {
        Supplier<ResourceLocation> head = data(line).lads$head();
        if (head == null || ChatHeads.offset(true) == 0) return;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha / 255f);
        PlayerFaceRenderer.draw(graphics, head.get(), x, y, 8);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** A stored message wrapped into lines as ChatComponent wraps it (Infinite History's lazy layout), first line first. */
    public static List<GuiMessage.Line> split(Font font, GuiMessage message, int width) {
        Data data = data(message);
        GuiMessageTag tag = message.tag();
        if (tag != null && tag.icon() != null) width -= tag.icon().width + 4 + 2;
        width -= offset(message);
        List<FormattedCharSequence> wrapped = ComponentRenderUtils.wrapComponents(message.content(), width, font);
        List<GuiMessage.Line> lines = new ArrayList<>(wrapped.size());
        for (int i = 0; i < wrapped.size(); i++) {
            GuiMessage.Line line = new GuiMessage.Line(message.addedTime(), wrapped.get(i), tag, i == wrapped.size() - 1);
            data(line).lads$set(data.lads$head(), data.lads$at(), i == 0);
            lines.add(line);
        }
        return lines;
    }

    /** Whether the module draws heads itself (false while a chat_heads jar is loaded). */
    public static boolean active() { return active; }
}
