package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.Nicknames;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Nametags on 1.8.9, as on 26.x: own tag in third person, background, text shadow and display-only renames. The name-tag
 * mixins (NametagRenderMixin, NametagLivingMixin, NametagEssentialMixin) and NicknameTabMixin ask here; chat is renamed
 * once as it arrives (Forge's ClientChatReceivedEvent). Options and renames are cached per tick to eliminate per-tag
 * reflection and map lookups.
 */
public final class Nametags189 {
    private static int nickGen;
    private static Map<String, String> cachedNames = Collections.emptyMap();
    private static long lastNamesTick = -1;

    private static long lastOptionTick = -1;
    private static boolean cachedBackground = true;
    private static boolean cachedShadow = true;
    private static boolean cachedOwnNametag = false;

    private Nametags189() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new Nametags189());
    }

    private static void updateOptions() {
        Minecraft mc = Minecraft.getMinecraft();
        long tick = mc.theWorld != null ? mc.theWorld.getTotalWorldTime() : -1;
        if (tick != lastOptionTick || tick == -1) { // no world (menus, previews): no tick to cache by
            lastOptionTick = tick;
            cachedOwnNametag = queryOption("Show Own Nametag in Third Person", false);
            cachedBackground = !queryEnabled() || queryOption("Render Background", true);
            cachedShadow = queryOption("Text Shadow", true);
        }
    }

    public static boolean ownNametag() { updateOptions(); return cachedOwnNametag; }
    public static boolean background() { updateOptions(); return cachedBackground; }
    public static boolean shadow() { updateOptions(); return cachedShadow; }

    /** Formatted text (name tags, tab list) with renamed players; unchanged when nothing matched. */
    public static String rename(String text) {
        if (text == null) return null;
        if (com.thelads.core.client.hypixel.LevelHead.isEnabled()) {
            String lvlTag = com.thelads.core.client.hypixel.LevelHead.formatLevelTag(text);
            if (lvlTag != null) text = lvlTag + text;
        }
        Map<String, String> names = names();
        if (names.isEmpty()) return text;
        EntityLivingBase entity = RenderTweaks189.currentEntity;
        if (entity instanceof NametagCache189) {
            NametagCache189 cache = (NametagCache189) entity;
            if (cache.ladsNickGen() == nickGen && cache.ladsRenamed() != null && text.equals(cache.ladsFormattedName())) {
                return cache.ladsRenamed();
            }
            String renamed = Nicknames.rename(text, names);
            // Only the entity's own tag text is cached: the tab list asks here too, while currentEntity is still the last tag drawn.
            if (text.equals(cache.ladsFormattedName())) {
                cache.ladsNickGen(nickGen);
                cache.ladsRenamed(renamed);
            }
            return renamed;
        }
        return Nicknames.rename(text, names);
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return Collections.emptyMap();
        long tick = mc.theWorld.getTotalWorldTime();
        if (tick != lastNamesTick) {
            lastNamesTick = tick;
            Map<String, String> current = Nicknames.active(mc.getSession().getUsername());
            if (current != cachedNames) {
                cachedNames = current;
                nickGen++;
            }
        }
        return cachedNames != null ? cachedNames : Collections.emptyMap();
    }

    private static boolean queryEnabled() {
        Module module = ModuleManager.getInstance().getModule("Nametags");
        return module != null && module.isEnabled();
    }

    private static boolean queryOption(String name, boolean fallback) {
        Module module = ModuleManager.getInstance().getModule("Nametags");
        if (module == null || !module.isEnabled()) return false;
        return module.getOption(name) instanceof BoolOption ? ((BoolOption) module.getOption(name)).get() : fallback;
    }
}
