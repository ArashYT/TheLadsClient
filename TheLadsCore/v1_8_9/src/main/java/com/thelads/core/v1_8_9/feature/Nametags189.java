package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.Nicknames;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Nametags on 1.8.9, as on 26.x: own tag in third person, background, text shadow and display-only renames. The name-tag
 * mixins (NametagRenderMixin, NametagLivingMixin, NametagEssentialMixin) and NicknameTabMixin ask here; chat is renamed
 * once as it arrives (Forge's ClientChatReceivedEvent).
 */
public final class Nametags189 {
    private Nametags189() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new Nametags189());
    }

    public static boolean ownNametag() { return option("Show Own Nametag in Third Person", false); }
    public static boolean background() { return !enabled() || option("Render Background", true); }
    public static boolean shadow() { return option("Text Shadow", true); }

    /** Formatted text (name tags, tab list) with renamed players; unchanged when nothing matched. */
    public static String rename(String text) {
        return text == null ? null : Nicknames.rename(text, names());
    }

    /** A chat message with renamed players, each piece keeping its style; the same instance when nothing matched. */
    public static IChatComponent rename(IChatComponent message) {
        Map<String, String> names = names();
        if (message == null || names.isEmpty()) return message;
        List<IChatComponent> parts = new ArrayList<IChatComponent>();
        for (IChatComponent part : message) parts.add(part); // flattened, styles resolved (as getFormattedText reads them)
        String[] texts = new String[parts.size()];
        for (int i = 0; i < texts.length; i++) texts[i] = parts.get(i).getUnformattedTextForChat();
        String[] renamed = Nicknames.rename(texts, names);
        if (renamed == null) return message;
        IChatComponent out = new ChatComponentText("");
        for (int i = 0; i < renamed.length; i++) {
            if (renamed[i].isEmpty()) continue;
            IChatComponent piece = new ChatComponentText(renamed[i]);
            piece.setChatStyle(parts.get(i).getChatStyle().createDeepCopy());
            out.appendSibling(piece);
        }
        return out;
    }

    @SubscribeEvent
    public void chat(ClientChatReceivedEvent event) {
        event.message = rename(event.message);
    }

    private static Map<String, String> names() {
        return Nicknames.active(Minecraft.getMinecraft().getSession().getUsername());
    }

    private static boolean enabled() {
        Module module = ModuleManager.getInstance().getModule("Nametags");
        return module != null && module.isEnabled();
    }

    private static boolean option(String name, boolean fallback) {
        Module module = ModuleManager.getInstance().getModule("Nametags");
        if (module == null || !module.isEnabled()) return false;
        return module.getOption(name) instanceof BoolOption ? ((BoolOption) module.getOption(name)).get() : fallback;
    }
}
