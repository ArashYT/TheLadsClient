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
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Chat Heads on 26.x. A message gets its sender's skin as it is added (ChatHeadsMixin; signed chat names the sender through
 * ChatHeadsListenerMixin, other messages are matched by ChatHeads.sender), GuiMessage keeps it (ChatHeadsMessageMixin, which
 * also wraps the message narrower) and each message's first visible line draws it (ChatHeadsLineMixin). The head is drawn into
 * the chat's own pose and alpha, so chat scale, opacity, fading, the Chat module's slide-in and the HUD FPS cap's replay carry
 * it like the text. A Chat Heads jar left in the mods folder keeps chat to itself.
 */
public final class NativeChatHeads {
    /** GuiMessage's sender skin (ChatHeadsMessageMixin). */
    public interface Sender {
        Supplier<PlayerSkin> lads$head();
        void lads$head(Supplier<PlayerSkin> head);
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
        if (signedSender != null) {
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(signedSender.id());
            head = info != null ? info::getSkin : mc.getSkinManager().createLookup(signedSender, false);
        } else if (connection != null && ChatHeads.byName()) {
            Map<String, PlayerInfo> names = new HashMap<>();
            Map<String, String> nicknames = Nicknames.active(mc.getUser().getName());
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                var display = info.getTabListDisplayName();
                ChatHeads.names(names, info, info.getProfile().name(), display == null ? null : display.getString(), nicknames);
            }
            PlayerInfo info = ChatHeads.sender(message.content().getString(), names);
            if (info != null) head = info::getSkin;
        }
        ((Sender) (Object) message).lads$head(head);
        return message;
    }

    /** Chat pixels the message's text moves right (and narrower it wraps). */
    public static int offset(GuiMessage message) {
        return active ? ChatHeads.offset(((Sender) (Object) message).lads$head() != null) : 0;
    }

    /** The head before a message's first visible line, its top on the text's top, faded with the text. */
    public static void draw(GuiMessage message, int top, float opacity) {
        Supplier<PlayerSkin> head = ((Sender) (Object) message).lads$head();
        if (graphics != null && head != null && offset(message) > 0)
            PlayerFaceExtractor.extractRenderState(graphics, head.get(), 0, top, 8, ARGB.white(opacity));
    }

    /** Chat starts drawing into {@code target} (null: done); true when the layout changed and chat must wrap its lines again. */
    public static boolean drawing(GuiGraphicsExtractor target) {
        graphics = target;
        return active && target != null && ChatHeads.layoutChanged();
    }
}
