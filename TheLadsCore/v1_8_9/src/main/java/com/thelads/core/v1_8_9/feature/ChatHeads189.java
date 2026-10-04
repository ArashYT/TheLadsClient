package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.ChatHeads;
import com.thelads.core.client.Nicknames;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.fml.common.Loader;

/**
 * Chat Heads on 1.8.9 (GuiNewChatMixin, ChatLineMixin). 1.8.9 chat does not name its sender, so a message's head is the tab-list
 * player ChatHeads.match finds in it as it arrives; the message and its drawn lines keep it and where the name starts, so
 * re-wrapping (refreshChat) keeps the head even after that player left. Before name: the first line draws the head just before
 * the sender's name and moves the rest of that line. Start of line: the first line draws the head at its start and every line of
 * the message moves right after it.
 */
public final class ChatHeads189 {
    /** A ChatLine's sender (ChatLineMixin), where the sender's name starts, and whether this drawn line is its message's first. */
    public interface Line {
        NetworkPlayerInfo ladsHead();
        int ladsAt();
        boolean ladsFirst();
        void ladsHead(NetworkPlayerInfo head, int at, boolean first);
    }

    private static Boolean active;
    private ChatHeads189() {}

    /** A Chat Heads jar left in the mods folder keeps chat to itself. */
    private static boolean active() {
        if (active == null) active = !Loader.isModLoaded("chat_heads");
        return active;
    }

    /** The tab-list player a new message is from and where their name starts, or null. */
    public static ChatHeads.Match<NetworkPlayerInfo> sender(IChatComponent message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (message == null || !active() || !ChatHeads.enabled() || !ChatHeads.byName() || mc.getNetHandler() == null) return null;
        Map<String, NetworkPlayerInfo> names = new HashMap<String, NetworkPlayerInfo>();
        Map<String, String> nicknames = Nicknames.active(mc.getSession().getUsername());
        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            IChatComponent display = info.getDisplayName();
            ChatHeads.names(names, info, info.getGameProfile().getName(), display == null ? null : display.getUnformattedText(), nicknames);
        }
        return ChatHeads.match(message.getUnformattedText(), names);
    }

    /** Chat pixels a line's text moves right (and its message wraps narrower). */
    public static int offset(NetworkPlayerInfo head) {
        return active() ? ChatHeads.offset(head != null) : 0;
    }

    public static boolean layoutChanged() {
        return active() && ChatHeads.layoutChanged();
    }

    /** The head (face and hat) with its top-left at the text's, faded with it (alpha 0-255). */
    public static void draw(NetworkPlayerInfo head, int x, int y, int alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(head.getLocationSkin());
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, alpha / 255f);
        Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, 8, 8, 64, 64);
        Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, 8, 8, 64, 64);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
