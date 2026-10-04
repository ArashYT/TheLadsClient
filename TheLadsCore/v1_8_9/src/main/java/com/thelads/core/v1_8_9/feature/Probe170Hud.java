package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.google.gson.JsonElement;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

/**
 * QA only (1.7.0 HUD lane), run by CoreProbe in its QA world: the Hotbar Slots Armor HUD with a damaged helmet, no chestplate,
 * leggings and damaged boots; the Lads scoreboard in its own colours (a red team) with shadow; one boss bar with Boss Bar on;
 * 150 chat lines kept and scrolled past the old 100-line cap; Autohide hidden and half faded. Screenshots 170-*.
 */
final class Probe170Hud {
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe170Hud::setup, Probe170Hud::hud, Probe170Hud::chat,
        Probe170Hud::chatScrolled, Probe170Hud::chatShot, Probe170Hud::autohide, Probe170Hud::hidden, Probe170Hud::fading, Probe170Hud::restore);
    private static final String[] MODULES = {"ArmorHUD", "Scoreboard", "BossBar", "Autohide", "Chat"};
    private static final Map<Option, JsonElement> optionsWere = new LinkedHashMap<>();
    private static final Map<Module, Boolean> enabledWere = new LinkedHashMap<>();
    private static boolean started;

    private Probe170Hud() {}

    private static boolean setup(Minecraft mc) {
        started = true;
        for (String name : MODULES) {
            Module module = Options189.module(name);
            enabledWere.put(module, module.isEnabled());
            for (Option option : module.getOptions()) optionsWere.put(option, option.save());
            module.getOptions().forEach(Option::reset);
            module.setEnabled(!name.equals("Autohide"));
        }
        onServer(mc, player -> {
            ItemStack helmet = new ItemStack(Items.chainmail_helmet), boots = new ItemStack(Items.golden_boots);
            helmet.setItemDamage(helmet.getMaxDamage() * 9 / 10);
            boots.setItemDamage(boots.getMaxDamage() / 5);
            player.inventory.armorInventory[3] = helmet;
            player.inventory.armorInventory[2] = null;
            player.inventory.armorInventory[1] = new ItemStack(Items.iron_leggings);
            player.inventory.armorInventory[0] = boots;
        });
        for (String command : new String[] {"scoreboard objectives add lads dummy §6Lads QA", "scoreboard objectives setdisplay sidebar lads",
            "scoreboard teams add qared", "scoreboard teams option qared color red", "scoreboard teams join qared Redstone",
            "scoreboard players set Redstone lads 3", "scoreboard players set Plain lads 2"}) command(mc, command);
        BossStatus.bossName = "QA Wither";
        BossStatus.healthScale = 0.6f;
        BossStatus.statusBarTime = 100_000;
        return after(40);
    }

    private static boolean hud(Minecraft mc) {
        check(BossStatus.statusBarTime < 100_000, "Boss Bar: vanilla's bar is replaced and its lifetime still counts down ("
            + BossStatus.statusBarTime + ")");
        LadsGameBridge.ScoreboardSnapshot board = LadsGameBridge.get().getScoreboard();
        check(board != null && board.title().contains("§6") && board.lines().get(0).name().contains("§c"),
            "Scoreboard: the gold title and the red team's name keep their colours (" + board + ")");
        check(LadsGameBridge.get().getArmor().size() == 3, "Armor HUD: the server's helmet, leggings and boots reached the client");
        screenshot(mc, "170-hud");
        for (int i = 1; i <= 150; i++) mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("QA chat line " + i));
        mc.displayGuiScreen(new GuiChat());
        return after(5);
    }

    private static boolean chat(Minecraft mc) {
        Chat189.History history = (Chat189.History) mc.ingameGUI.getChatGUI();
        check(history.ladsMessages() >= 150, "Infinite History: all 150 new messages are kept (" + history.ladsMessages() + ")");
        mc.ingameGUI.getChatGUI().refreshChat();
        int laidOut = history.ladsDrawnLines();
        check(laidOut < history.ladsMessages() && laidOut >= mc.ingameGUI.getChatGUI().getLineCount(),
            "Infinite History: a refresh lays out only the lines near the newest message (" + laidOut + " of " + history.ladsMessages() + ")");
        mc.ingameGUI.getChatGUI().scroll(130);
        check(history.ladsScrollPos() == 130, "Infinite History: the chat scrolls 130 lines up, past the old 100-line cap ("
            + history.ladsScrollPos() + ", " + history.ladsDrawnLines() + " lines laid out)");
        return after(5);
    }

    private static boolean chatScrolled(Minecraft mc) {
        screenshot(mc, "170-chat-scrolled");
        return after(1);
    }

    private static boolean chatShot(Minecraft mc) {
        mc.displayGuiScreen(null);
        return after(5);
    }

    private static boolean autohide(Minecraft mc) {
        Module autohide = Options189.module("Autohide");
        autohide.setEnabled(true);
        ((BoolOption) autohide.getOption("Show when hurt or hungry")).set(false);
        ((BoolOption) autohide.getOption("Show while moving")).set(false);
        ((SliderOption) autohide.getOption("Fade milliseconds")).setValue(0);
        return after(5); // Autohide189 sees the player first
    }

    private static boolean hidden(Minecraft mc) {
        Autohide189.idle(0);
        return after(5);
    }

    private static boolean fading(Minecraft mc) {
        check(Autohide189.shown == 0, "Autohide: idle, the hotbar, its items, the status bars and the Lads HUD are hidden");
        screenshot(mc, "170-autohide-hidden");
        ((SliderOption) Options189.module("Autohide").getOption("Fade milliseconds")).setValue(3000);
        Autohide189.idle(0.6f);
        return after(3);
    }

    private static boolean restore(Minecraft mc) {
        check(Autohide189.shown > 0.3f && Autohide189.shown < 0.6f, "Autohide: fading out (" + Autohide189.shown + ")");
        screenshot(mc, "170-autohide-fading");
        stop();
        return after(5);
    }

    static void stop() {
        if (!started) return;
        started = false;
        optionsWere.forEach(Option::load);
        enabledWere.forEach(Module::setEnabled);
        BossStatus.statusBarTime = 0;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getIntegratedServer() == null || mc.thePlayer == null) return;
        for (String command : new String[] {"scoreboard objectives remove lads", "scoreboard teams remove qared"}) command(mc, command);
        onServer(mc, player -> Arrays.fill(player.inventory.armorInventory, null));
    }

    private interface ServerTask { void run(EntityPlayerMP player); }

    private static void onServer(Minecraft mc, ServerTask task) {
        MinecraftServer server = mc.getIntegratedServer();
        java.util.UUID id = mc.thePlayer.getUniqueID();
        server.addScheduledTask(() -> task.run(server.getConfigurationManager().getPlayerByUUID(id)));
    }

    private static void command(Minecraft mc, String command) {
        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
    }
}
