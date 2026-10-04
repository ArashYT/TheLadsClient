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
        RaisedDollProbe189::sneak, RaisedDollProbe189::hover, RaisedDollProbe189::fly, RaisedDollProbe189::eat, RaisedDollProbe189::faded,
        RaisedDollProbe189::editor, RaisedDollProbe189::done);
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static boolean raisedWas, dollWas, togglesWas, started, swapped;
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
        // The doll's sprint holds the Sprint key as vanilla does; Toggle Sprint & Sneak (on in upgraded configs) would want a tap.
        togglesWas = Toggles189.toggles().isEnabled();
        Toggles189.toggles().setEnabled(false);
        positionWas = HudSettings.getInstance().getPosition("Paperdoll");
        gameModeWas = mc.playerController.getCurrentGameType().getID();
        raised().getOptions().forEach(Option::reset);
        raised().setEnabled(true);
        doll().setEnabled(false);
        survival(mc); // hearts and hunger sit on the hotbar
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
        check(Raised189.chat() == 12, "Raised: with chat open chat moves 12 px up, so the raised hotbar does not cover its newest line");
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
        HudSettings.getInstance().setPosition("Paperdoll", 60, 60); // clear of the tutorial toast in the corner
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
        // Off the ground first: landing ends flying.
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
        return after(5);
    }

    private static boolean hover(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        return after(45); // past the crouch's display time: flying alone keeps it up
    }

    private static boolean fly(Minecraft mc) {
        check(mc.thePlayer.capabilities.isFlying && PaperDoll.INSTANCE.visible(true), "Paper doll: creative flying (switched on) keeps it up");
        screenshot(mc, "doll-3-fly");
        mc.thePlayer.capabilities.isFlying = false;
        mc.thePlayer.sendPlayerAbilities();
        bool(doll(), "Using Items", true);
        survival(mc); // 1.8.9's creative players cannot eat
        slot = mc.thePlayer.inventory.currentItem;
        heldWas = mc.thePlayer.inventory.getCurrentItem();
        hold(mc, new ItemStack(Items.golden_apple));
        swapped = true;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        return after(20);
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

    /** Survival, with nothing in the QA world able to hurt the player meanwhile. */
    private static void survival(Minecraft mc) {
        command(mc, "effect " + mc.thePlayer.getName() + " 11 60 255 true"); // resistance
        command(mc, "effect " + mc.thePlayer.getName() + " 12 60 0 true"); // fire resistance
        command(mc, "gamemode 0 " + mc.thePlayer.getName());
    }

    /** The held slot on the server (in order with the commands, in any game mode), which sends it to the client. */
    private static void hold(Minecraft mc, ItemStack stack) {
        final MinecraftServer server = mc.getIntegratedServer();
        final java.util.UUID id = mc.thePlayer.getUniqueID();
        final ItemStack copy = ItemStack.copyItemStack(stack);
        final int held = slot;
        server.addScheduledTask(() -> {
            net.minecraft.entity.player.EntityPlayerMP player = server.getConfigurationManager().getPlayerByUUID(id);
            if (player == null) return;
            player.inventory.setInventorySlotContents(held, copy);
            player.inventoryContainer.detectAndSendChanges();
        });
    }

    /** Puts everything back (also when an earlier check failed). */
    static void stop() {
        if (!started) return;
        started = false;
        Minecraft mc = Minecraft.getMinecraft();
        for (KeyBinding key : new KeyBinding[] {mc.gameSettings.keyBindForward, mc.gameSettings.keyBindSprint, mc.gameSettings.keyBindSneak,
            mc.gameSettings.keyBindJump, mc.gameSettings.keyBindUseItem}) KeyBinding.setKeyBindState(key.getKeyCode(), false);
        for (Map.Entry<Option, JsonElement> entry : OPTIONS.entrySet()) entry.getKey().load(entry.getValue());
        raised().setEnabled(raisedWas);
        doll().setEnabled(dollWas);
        Toggles189.toggles().setEnabled(togglesWas);
        if (positionWas == null) HudSettings.getInstance().getPositions().remove("Paperdoll");
        else HudSettings.getInstance().setPosition("Paperdoll", positionWas[0], positionWas[1]);
        if (mc.thePlayer != null && mc.getIntegratedServer() != null) {
            if (swapped) hold(mc, heldWas);
            swapped = false;
            command(mc, "gamemode " + gameModeWas + " " + mc.thePlayer.getName());
            command(mc, "effect " + mc.thePlayer.getName() + " clear");
        }
        ConfigManager.save();
    }
}
