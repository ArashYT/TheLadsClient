package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.google.gson.JsonElement;
import com.thelads.core.client.hud.PaperDoll;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v1_8_9.gui.DraggableHudScreen189;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * QA only (-Dthelads.verify189Only=raised, or after the other probes): Raised and the paper doll in the 1.8.9 QA world. Survival for
 * the hearts and hunger: raised-1-default (2 px), raised-2-chat-open (16 px over the chat box), raised-3-changed (hotbar 12, chat
 * 24, an action bar message) and a click on the raised chat line. Creative for the doll: doll-1-sprint, doll-2-sneak, doll-3-fly,
 * doll-4-eat, doll-5-opacity-50, doll-6-editor (moved and scaled in the HUD editor). Settings, game mode and held item put back.
 */
final class RaisedDollProbe189 {
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(RaisedDollProbe189::setup, RaisedDollProbe189::defaults,
        RaisedDollProbe189::chatOpen, RaisedDollProbe189::changed, RaisedDollProbe189::chatClick, RaisedDollProbe189::sprint,
        RaisedDollProbe189::sneak, RaisedDollProbe189::fly, RaisedDollProbe189::eat, RaisedDollProbe189::faded,
        RaisedDollProbe189::editor, RaisedDollProbe189::done);
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static boolean raisedWas, dollWas, started;
    private static int[] positionWas;
    private static int gameModeWas, slot;
    private static ItemStack heldWas;

    private RaisedDollProbe189() {}

    private static Module raised() { return ModuleManager.getInstance().getModule("Raised"); }
    private static Module doll() { return ModuleManager.getInstance().getModule("Paperdoll"); }
    private static void number(Module module, String name, double value) { ((SliderOption) module.getOption(name)).setValue(value); }
    private static void bool(Module module, String name, boolean value) { ((BoolOption) module.getOption(name)).set(value); }

    private static void command(Minecraft mc, final String command) {
        final MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
    }

    private static boolean setup(Minecraft mc) {
        started = true;
        for (Module module : new Module[] {raised(), doll()}) for (Option option : module.getOptions()) OPTIONS.put(option, option.save());
        raisedWas = raised().isEnabled();
        dollWas = doll().isEnabled();
        positionWas = HudSettings.getInstance().getPosition("Paperdoll");
        gameModeWas = mc.playerController.getCurrentGameType().getID();
        raised().getOptions().forEach(Option::reset);
        raised().setEnabled(true);
        doll().setEnabled(false);
        command(mc, "gamemode 0 " + mc.thePlayer.getName()); // hearts and hunger sit on the hotbar
        return after(40);
    }

    private static boolean defaults(Minecraft mc) {
        check(Raised189.hotbar() == 2 && Raised189.chat() == 0, "Raised: the hotbar group sits 2 px off the bottom by default, chat where vanilla draws it");
        screenshot(mc, "raised-1-default");
        mc.displayGuiScreen(new GuiChat());
        return after(20);
    }

    private static boolean chatOpen(Minecraft mc) {
        check(mc.currentScreen instanceof GuiChat && Raised189.hotbar() == 16, "Raised: with chat open the hotbar group clears the chat box (2 + Distance 14)");
        screenshot(mc, "raised-2-chat-open");
        mc.displayGuiScreen(null);
        number(raised(), "Hotbar", 12);
        number(raised(), "Chat", 24);
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("Lads Raised QA: chat 24 px up"));
        mc.ingameGUI.setRecordPlaying("Lads Raised QA: action bar 12 px up", false);
        return after(20);
    }

    private static boolean changed(Minecraft mc) {
        check(Raised189.hotbar() == 12 && Raised189.chat() == 24, "Raised: changed sliders reach the game (hotbar 12, chat 24)");
        screenshot(mc, "raised-3-changed");
        mc.displayGuiScreen(new GuiChat());
        return after(10);
    }

    /** The newest chat line, drawn 24 px higher, answers a pointer on it there and not where vanilla would draw it. */
    private static boolean chatClick(Minecraft mc) {
        ScaledResolution resolution = new ScaledResolution(mc);
        int scale = resolution.getScaleFactor();
        IChatComponent raisedLine = mc.ingameGUI.getChatGUI().getChatComponent(10 * scale, (27 + 24 + 4) * scale);
        IChatComponent vanillaLine = mc.ingameGUI.getChatGUI().getChatComponent(10 * scale, (27 + 4) * scale);
        check(raisedLine != null && raisedLine.getUnformattedText().contains("Lads Raised QA") && vanillaLine == null,
            "Raised: the pointer finds the raised chat line where it is drawn (" + raisedLine + "), nothing where it was (" + vanillaLine + ")");
        mc.displayGuiScreen(null);
        raised().getOptions().forEach(Option::reset);
        doll().getOptions().forEach(Option::reset);
        doll().setEnabled(true);
        command(mc, "gamemode 1 " + mc.thePlayer.getName());
        // Sprinting only lasts while moving forward: hold both keys, as a player does.
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        return after(8);
    }

    private static boolean sprint(Minecraft mc) {
        check(mc.thePlayer.isSprinting() && PaperDoll.INSTANCE.visible(true), "Paper doll: sprinting brings it up");
        screenshot(mc, "doll-1-sprint");
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
        return after(15);
    }

    private static boolean sneak(Minecraft mc) {
        check(mc.thePlayer.isSneaking() && PaperDoll.INSTANCE.visible(true), "Paper doll: crouching keeps it up, crouched");
        screenshot(mc, "doll-2-sneak");
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        bool(doll(), "Creative Flying", true);
        mc.thePlayer.capabilities.isFlying = true;
        mc.thePlayer.sendPlayerAbilities();
        return after(50); // past the crouch's display time: flying alone keeps it up
    }

    private static boolean fly(Minecraft mc) {
        check(mc.thePlayer.capabilities.isFlying && PaperDoll.INSTANCE.visible(true), "Paper doll: creative flying (switched on) keeps it up");
        screenshot(mc, "doll-3-fly");
        mc.thePlayer.capabilities.isFlying = false;
        mc.thePlayer.sendPlayerAbilities();
        bool(doll(), "Using Items", true);
        slot = mc.thePlayer.inventory.currentItem;
        heldWas = mc.thePlayer.inventory.getCurrentItem();
        hold(mc, new ItemStack(Items.golden_apple));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        return after(12);
    }

    private static boolean eat(Minecraft mc) {
        check(mc.thePlayer.isUsingItem() && PaperDoll.INSTANCE.visible(true), "Paper doll: eating (using items switched on) keeps it up");
        screenshot(mc, "doll-4-eat");
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        hold(mc, heldWas);
        number(doll(), "Model Opacity", 50);
        bool(doll(), "Always Display", true);
        return after(10);
    }

    private static boolean faded(Minecraft mc) {
        check(PaperDoll.INSTANCE.opacity() == 0.5f, "Paper doll: Model Opacity 50");
        screenshot(mc, "doll-5-opacity-50");
        number(doll(), "Model Opacity", 100);
        number(doll(), "Model Scale", 6);
        HudSettings.getInstance().setPosition("Paperdoll", 40, 40);
        mc.displayGuiScreen(new DraggableHudScreen189(null));
        return after(20);
    }

    private static boolean editor(Minecraft mc) {
        check(mc.currentScreen instanceof DraggableHudScreen189, "the HUD editor shows the doll where it was moved, scaled up");
        screenshot(mc, "doll-6-editor");
        mc.displayGuiScreen(null);
        stop();
        return after(10);
    }

    private static boolean done(Minecraft mc) {
        check(raised().isEnabled() == raisedWas && doll().isEnabled() == dollWas, "settings put back");
        return after(1);
    }

    private static void hold(Minecraft mc, ItemStack stack) {
        mc.thePlayer.inventory.setInventorySlotContents(slot, stack);
        mc.playerController.sendSlotPacket(stack, 36 + slot); // creative: the server holds the same item
    }

    /** Puts everything back (also when an earlier check failed). */
    static void stop() {
        if (!started) return;
        started = false;
        Minecraft mc = Minecraft.getMinecraft();
        for (KeyBinding key : new KeyBinding[] {mc.gameSettings.keyBindForward, mc.gameSettings.keyBindSprint, mc.gameSettings.keyBindSneak,
            mc.gameSettings.keyBindUseItem}) KeyBinding.setKeyBindState(key.getKeyCode(), false);
        for (Map.Entry<Option, JsonElement> entry : OPTIONS.entrySet()) entry.getKey().load(entry.getValue());
        raised().setEnabled(raisedWas);
        doll().setEnabled(dollWas);
        if (positionWas == null) HudSettings.getInstance().getPositions().remove("Paperdoll");
        else HudSettings.getInstance().setPosition("Paperdoll", positionWas[0], positionWas[1]);
        if (mc.thePlayer != null && mc.getIntegratedServer() != null) {
            if (heldWas != null || mc.thePlayer.inventory.getCurrentItem() != null && mc.thePlayer.inventory.getCurrentItem().getItem() == Items.golden_apple) hold(mc, heldWas);
            command(mc, "gamemode " + gameModeWas + " " + mc.thePlayer.getName());
        }
        ConfigManager.save();
    }
}
