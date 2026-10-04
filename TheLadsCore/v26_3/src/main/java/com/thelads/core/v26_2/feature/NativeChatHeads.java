package com.thelads.core.v26_2.feature;

import com.mojang.authlib.GameProfile;
import com.thelads.core.client.ChatHeads;
import com.thelads.core.client.Nicknames;
import com.thelads.core.config.ModuleSupport;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Chat Heads on 26.x. A message gets its sender's skin as it is added (ChatHeadsMixin; signed chat names the sender through
 * ChatHeadsListenerMixin, other messages are matched by ChatHeads.sender), GuiMessage keeps it (ChatHeadsMessageMixin, which
 * also wraps the message narrower) and each message's first visible line draws it (ChatHeadsLineMixin). The head is drawn into
 * the chat's own pose and alpha, so chat scale, opacity, fading, the Chat module's slide-in and the HUD FPS cap's replay carry
 * it like the text. Position "Before name" draws the head inside the message's first line, just before the sender's name
 * (ChatHeads.Match.at), and moves only the rest of that line. A Chat Heads jar left in the mods folder keeps chat to itself.
 */
public final class NativeChatHeads {
    /** GuiMessage's sender skin, where its name starts and the message's first wrapped line (ChatHeadsMessageMixin). */
    public interface Sender {
        Supplier<PlayerSkin> lads$head();
        int lads$at();
        void lads$head(Supplier<PlayerSkin> head, int at);
        FormattedCharSequence lads$first();
    }

    private static boolean active;
    /** The sender of the signed chat message being added (ChatHeadsListenerMixin). */
    public static GameProfile signedSender;
    /** The chat being drawn into; null while chat only collects clickable text. */
    private static GuiGraphicsExtractor graphics;
    private NativeChatHeads() {}

    public static void register() {
        if (active || FabricLoader.getInstance().isModLoaded("chat_heads")) return;
        active = true;
        ModuleSupport.registerBuiltIn(ChatHeads.NAME);
    }

    /** A message about to be shown: its sender's skin, from signed chat or from the tab-list names in its text. */
    public static GuiMessage attach(GuiMessage message) {
        if (!active || !ChatHeads.enabled()) return message;
        Minecraft mc = Minecraft.getInstance();
        var connection = mc.getConnection();
        Supplier<PlayerSkin> head = null;
        int at = 0;
        String text = message.content().getString();
        Map<String, String> nicknames = Nicknames.active(mc.getUser().getName());
        if (signedSender != null) {
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(signedSender.id());
            head = info != null ? info::getSkin : mc.getSkinManager().createLookup(signedSender, false);
            // The server named the sender; the head goes before the first of that player's names in the line ("<Steve> hi").
            Map<String, Boolean> names = new HashMap<>();
            var display = info == null ? null : info.getTabListDisplayName();
            ChatHeads.names(names, Boolean.TRUE, signedSender.name(), display == null ? null : display.getString(), nicknames);
            ChatHeads.Match<Boolean> match = ChatHeads.match(text, names);
            if (match != null) at = match.at();
        } else if (connection != null && ChatHeads.byName()) {
            Map<String, PlayerInfo> names = new HashMap<>();
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                var display = info.getTabListDisplayName();
                ChatHeads.names(names, info, info.getProfile().name(), display == null ? null : display.getString(), nicknames);
            }
            ChatHeads.Match<PlayerInfo> match = ChatHeads.match(text, names);
            if (match != null) {
                head = match.player()::getSkin;
                at = match.at();
            }
        }
        ((Sender) (Object) message).lads$head(head, at);
        return message;
    }

    /** Chat pixels the message's text moves right (and narrower it wraps). */
    public static int offset(GuiMessage message) {
        return active ? ChatHeads.offset(((Sender) (Object) message).lads$head() != null) : 0;
    }

    /** Before name: only the message's first line holds the head and moves. Start of line: every line moves. */
    public static boolean beforeName() { return ChatHeads.beforeName(); }

    /** Chat pixels this line's text moves right after the head (Before name: the first line only; its text before the name stays). */
    public static int shift(GuiMessage.Line line) {
        int offset = offset(line.parent());
        return offset == 0 || !beforeName() || line.content() == ((Sender) (Object) line.parent()).lads$first() ? offset : 0;
    }

    /** Code points of the first line before the head: the sender's name, or 0 when the name is not on that line. */
    public static int at(GuiMessage message, FormattedCharSequence line) {
        int at = ((Sender) (Object) message).lads$at(), length[] = {0};
        if (at <= 0) return 0;
        line.accept((position, style, codePoint) -> ++length[0] <= at);
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

    /** The head with its left edge at {@code x} and its top on the text's top, faded with the text. */
    public static void draw(GuiMessage message, int x, int top, float opacity) {
        Supplier<PlayerSkin> head = ((Sender) (Object) message).lads$head();
        if (graphics != null && head != null && offset(message) > 0)
            PlayerFaceExtractor.extractRenderState(graphics, head.get(), x, top, 8, ARGB.white(opacity));
    }

    /** Chat starts drawing into {@code target} (null: done); true when the layout changed and chat must wrap its lines again. */
    public static boolean drawing(GuiGraphicsExtractor target) {
        graphics = target;
        return active && target != null && ChatHeads.layoutChanged();
    }
}
