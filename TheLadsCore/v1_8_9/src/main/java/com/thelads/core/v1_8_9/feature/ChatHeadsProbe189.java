package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.thelads.core.client.ChatHeads;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ToggleNametagsModule;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * QA only, run by CoreProbe in its QA world (-Dthelads.verifyChatHeads=true runs it alone): Chat Heads with the Chat module's
 * timestamps on. A second player, Lads_Tester (Lads nickname "Testy"), joins the client's tab list. Own chat goes to the integrated
 * server and back; the tester's chat, server lines with rank prefixes and colour codes (for the tester and, long enough to wrap,
 * for the own player), a similar unknown name and a plain line arrive as chat packets (Forge's chat event renames Lads nicknames
 * first). Each message's head and where it goes (Position Before name, the default: just before the sender's name) are checked
 * on its chat lines, then 170-chatheads-1-closed, -2-open (GuiChat), -3-startofline (Position Start of line, Keep text aligned),
 * -5-noshadow (the Chat module's Text Shadow off), -6-scrolledback (150 newer lines, a refresh, then Infinite History lays the
 * own messages out again as the chat scrolls back to them) and -4-off (module off: 1.8.9's own chat) are saved. Everything is put back.
 */
final class ChatHeadsProbe189 {
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(ChatHeadsProbe189::start, ChatHeadsProbe189::closed,
        ChatHeadsProbe189::open, ChatHeadsProbe189::aligned, ChatHeadsProbe189::noShadow, ChatHeadsProbe189::scrolledBack,
        ChatHeadsProbe189::off, ChatHeadsProbe189::restored);
    private static final GameProfile TESTER = new GameProfile(UUID.fromString("6f3b2c1a-0d4e-4f5a-9b8c-7d6e5f4a3b2c"), "Lads_Tester");
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<Option, JsonElement>();
    private static final Map<Module, Boolean> ENABLED = new LinkedHashMap<Module, Boolean>();
    private static NetworkPlayerInfo tester;
    private ChatHeadsProbe189() {}

    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }

    private static boolean start(Minecraft mc) throws Exception {
        for (String name : new String[] {ChatHeads.NAME, "Chat", "Nametags"}) {
            Module module = module(name);
            ENABLED.put(module, module.isEnabled());
            for (Option option : module.getOptions()) OPTIONS.put(option, option.save()); // a new element each time
        }
        for (Option option : module(ChatHeads.NAME).getOptions()) option.reset();
        module(ChatHeads.NAME).setEnabled(true);
        ((BoolOption) module("Chat").getOption("Timestamps")).set(true);
        ToggleNametagsModule tags = (ToggleNametagsModule) module("Nametags");
        tags.setEnabled(true);
        tags.nicknames.setValue("Lads_Tester=Testy");
        tester = new NetworkPlayerInfo(TESTER);
        tabList(mc).put(TESTER.getId(), tester);
        mc.ingameGUI.getChatGUI().clearChatMessages();
        String own = mc.getSession().getUsername();
        mc.thePlayer.sendChatMessage("my own chat, sent to the server and back");
        receive(mc, new ChatComponentTranslation("chat.type.text", TESTER.getName(), "hi, this is a second player's chat"));
        receive(mc, new ChatComponentText("§c[Admin] §fLads_Tester§7: §fplugin chat with a rank and colour codes"));
        IChatComponent rank = new ChatComponentText("[VIP+] ");
        rank.getChatStyle().setColor(EnumChatFormatting.GOLD);
        IChatComponent name = new ChatComponentText(own);
        name.getChatStyle().setColor(EnumChatFormatting.WHITE);
        receive(mc, new ChatComponentText("").appendSibling(rank).appendSibling(name).appendSibling(new ChatComponentText(
            " » a long plugin line from me that wraps onto more lines: only its first line has the head, the rest lines up after it")));
        IChatComponent joined = new ChatComponentText("Lads_Tester2 joined the game");
        joined.getChatStyle().setColor(EnumChatFormatting.YELLOW);
        receive(mc, joined);
        receive(mc, new ChatComponentText("Server restarting in 5 minutes"));
        return after(30);
    }

    /** A server chat packet, through the client's own handler (and so Forge's chat event). */
    private static void receive(Minecraft mc, IChatComponent message) {
        mc.getNetHandler().handleChat(new S02PacketChat(message, (byte) 0));
    }

    private static boolean closed(Minecraft mc) throws Exception {
        NetworkPlayerInfo own = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        List<ChatLine> lines = lines(mc.ingameGUI.getChatGUI());
        check(own != null && own != tester, "the own player and Lads_Tester are both in the tab list");
        check(text(lines, "Testy§7: ") != null, "the rank line shows the Lads nickname Testy");
        expect(lines, "my own chat", own);
        expect(lines, "second player's chat", tester);
        expect(lines, "plugin chat with a rank", tester);
        expect(lines, "a long plugin line from me", own);
        expect(lines, "Lads_Tester2 joined", null);
        expect(lines, "Server restarting", null);
        int first = 0;
        for (ChatLine line : lines) if (((ChatHeads189.Line) line).ladsFirst() && ((ChatHeads189.Line) line).ladsHead() == own) first++;
        check(first == 2, "each of the own player's two messages has one first drawn line (" + first + ")");
        check(ChatHeads.beforeName(), "Position defaults to Before name");
        expectAt(lines, "my own chat", mc.getSession().getUsername());
        expectAt(lines, "hi, this is", "Testy", TESTER.getName());
        expectAt(lines, "[Admin]", "Testy");
        expectAt(lines, "[VIP+]", mc.getSession().getUsername());
        // Infinite History (on by default) lays the drawn lines out again lazily (Chat189.layOut): they keep their messages' heads.
        mc.ingameGUI.getChatGUI().refreshChat();
        lines = lines(mc.ingameGUI.getChatGUI());
        first = 0;
        for (ChatLine line : lines) if (((ChatHeads189.Line) line).ladsFirst() && ((ChatHeads189.Line) line).ladsHead() == own) first++;
        check(first == 2, "after a chat refresh each of the own player's two messages still has one first drawn line (" + first + ")");
        expectAt(lines, "[VIP+]", mc.getSession().getUsername());
        screenshot(mc, "170-chatheads-1-closed");
        mc.displayGuiScreen(new GuiChat());
        return after(20);
    }

    private static boolean open(Minecraft mc) {
        check(mc.currentScreen instanceof GuiChat, "the chat screen is open");
        screenshot(mc, "170-chatheads-2-open");
        ((DropdownOption) module(ChatHeads.NAME).getOption(ChatHeads.POSITION)).setIndex(1);
        ((BoolOption) module(ChatHeads.NAME).getOption(ChatHeads.ALIGNED)).set(true);
        return after(20);
    }

    private static boolean aligned(Minecraft mc) throws Exception {
        screenshot(mc, "170-chatheads-3-startofline");
        List<ChatLine> lines = lines(mc.ingameGUI.getChatGUI());
        expect(lines, "a long plugin line from me", mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()));
        ((DropdownOption) module(ChatHeads.NAME).getOption(ChatHeads.POSITION)).setIndex(0);
        ((BoolOption) module(ChatHeads.NAME).getOption(ChatHeads.ALIGNED)).set(false);
        ((BoolOption) module("Chat").getOption("Text Shadow")).set(false);
        return after(20);
    }

    /** Text Shadow off, then (Infinite History) 150 newer lines: the own messages are laid out again only when scrolled back to. */
    private static boolean noShadow(Minecraft mc) throws Exception {
        screenshot(mc, "170-chatheads-5-noshadow");
        ((BoolOption) module("Chat").getOption("Text Shadow")).set(true);
        for (int i = 1; i <= 150; i++) receive(mc, new ChatComponentText("Server line " + i));
        mc.ingameGUI.getChatGUI().refreshChat();
        Chat189.History history = (Chat189.History) mc.ingameGUI.getChatGUI();
        int own = ownFirstLines(mc); // only drawn lines are marked first
        check(own == 0 && history.ladsDrawnLines() < history.ladsMessages(), "a refresh lays out only the newest lines, not the own messages ("
            + history.ladsDrawnLines() + " lines of " + history.ladsMessages() + " messages, " + own + " own first lines)");
        mc.ingameGUI.getChatGUI().scroll(1000);
        return after(20);
    }

    private static int ownFirstLines(Minecraft mc) throws Exception {
        NetworkPlayerInfo own = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        int first = 0;
        for (ChatLine line : lines(mc.ingameGUI.getChatGUI())) if (((ChatHeads189.Line) line).ladsFirst() && ((ChatHeads189.Line) line).ladsHead() == own) first++;
        return first;
    }

    private static boolean scrolledBack(Minecraft mc) throws Exception {
        List<ChatLine> lines = lines(mc.ingameGUI.getChatGUI());
        int first = ownFirstLines(mc);
        check(first == 2, "scrolled back, each of the own player's two messages is laid out again with its head on one first line (" + first + ")");
        expectAt(lines, "my own chat", mc.getSession().getUsername());
        expectAt(lines, "[Admin]", "Testy");
        expectAt(lines, "[VIP+]", mc.getSession().getUsername());
        screenshot(mc, "170-chatheads-6-scrolledback");
        mc.ingameGUI.getChatGUI().resetScroll();
        mc.displayGuiScreen(null);
        module(ChatHeads.NAME).setEnabled(false);
        return after(20);
    }

    private static boolean off(Minecraft mc) throws Exception {
        screenshot(mc, "170-chatheads-4-off");
        stop(mc);
        return after(10);
    }

    private static boolean restored(Minecraft mc) throws Exception {
        check(!tabList(mc).containsKey(TESTER.getId()) && module(ChatHeads.NAME).isEnabled() == Boolean.TRUE.equals(ENABLED.get(module(ChatHeads.NAME))),
            "Lads_Tester left the tab list and Chat Heads, Chat and Nametags are as found");
        return true;
    }

    /** Puts everything back (also when the probe fails part-way). */
    static void stop(Minecraft mc) {
        if (ENABLED.isEmpty()) return;
        for (Map.Entry<Option, JsonElement> option : OPTIONS.entrySet()) option.getKey().load(option.getValue());
        for (Map.Entry<Module, Boolean> module : ENABLED.entrySet()) module.getKey().setEnabled(module.getValue());
        try { if (mc.getNetHandler() != null) tabList(mc).remove(TESTER.getId()); } catch (Exception ignored) {}
    }

    private static void expect(List<ChatLine> lines, String text, NetworkPlayerInfo head) {
        ChatLine line = text(lines, text);
        check(line != null && ((ChatHeads189.Line) line).ladsHead() == head, "\"" + (line == null ? text : line.getChatComponent().getUnformattedText())
            + "\" has the head of " + (head == null ? "nobody" : head.getGameProfile().getName()));
    }

    /** Before name: the head goes just before the first of {@code names} on the message's first drawn line. */
    private static void expectAt(List<ChatLine> lines, String text, String... names) {
        for (ChatLine line : lines) {
            ChatHeads189.Line head = (ChatHeads189.Line) line;
            String plain = line.getChatComponent().getUnformattedText().replaceAll("(?s)\u00a7.", "");
            if (!head.ladsFirst() || !plain.contains(text)) continue;
            int expected = Integer.MAX_VALUE;
            for (String name : names) if (plain.indexOf(name) >= 0) expected = Math.min(expected, plain.indexOf(name));
            check(expected > 0 && expected < plain.length() && head.ladsAt() == plain.codePointCount(0, expected),
                "\"" + plain + "\": head before " + names[0] + " at " + head.ladsAt() + " (expected " + expected + ")");
            return;
        }
        check(false, "a first drawn line containing \"" + text + "\"");
    }

    private static ChatLine text(List<ChatLine> lines, String text) {
        for (ChatLine line : lines) if (line.getChatComponent().getUnformattedText().contains(text)) return line;
        return null;
    }

    /** The chat's stored messages and drawn lines (GuiNewChat's ChatLine lists; the sent-message history holds strings). */
    private static List<ChatLine> lines(GuiNewChat chat) throws Exception {
        List<ChatLine> lines = new ArrayList<ChatLine>();
        for (Field field : GuiNewChat.class.getDeclaredFields()) {
            if (field.getType() != List.class) continue;
            field.setAccessible(true);
            for (Object line : (List<?>) field.get(chat)) if (line instanceof ChatLine) lines.add((ChatLine) line);
        }
        return lines;
    }

    /** The client's tab list (NetHandlerPlayClient's only Map), so the server never hears of Lads_Tester. */
    @SuppressWarnings("unchecked")
    private static Map<UUID, NetworkPlayerInfo> tabList(Minecraft mc) throws Exception {
        for (Field field : NetHandlerPlayClient.class.getDeclaredFields()) {
            if (field.getType() != Map.class) continue;
            field.setAccessible(true);
            return (Map<UUID, NetworkPlayerInfo>) field.get(mc.getNetHandler());
        }
        throw new IllegalStateException("NetHandlerPlayClient has no tab list map");
    }
}
