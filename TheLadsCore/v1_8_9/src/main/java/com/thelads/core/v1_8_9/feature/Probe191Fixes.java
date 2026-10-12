package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.thelads.core.client.FixesQa;
import com.thelads.core.client.util.FileDialogsQa;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.v1_8_9.gui.SkinChangerScreen189;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only, 1.9.1 fixes lane. LADS_VERIFY_189_ONLY=fixes: fast clicking through LWJGL's mouse queue (runTick's own input path) at 10
 * clicks a second, in survival, at the sky and at the ground, with vanilla's swing rule (OldAnimations189.qaVanillaSwing) and with the
 * fix, with LegacySwing, and on a blocking sword with 1.7 Animations: every click must start a swing (counted the tick after it);
 * then the common FixesQa routine (Fullbright, name tags, the pause menu's Multiplayer) with 1.8.9's world and screens.
 * LADS_VERIFY_189_ONLY=dialogs: the common FileDialogsQa (a machine nobody watches), then the Skin changer's Add File (the modern
 * dialog, closed: "Selection canceled"). Screenshots: lads-qa/screenshots.
 */
final class Probe191Fixes {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final int CLICKS = 20;
    private static FixesQa fixes;
    private static FileDialogsQa dialogs;
    private static int clicks, started, phase;
    private static boolean legacyWas, animationsWas, focusWas;

    private Probe191Fixes() {}

    static List<CoreProbe.Step> fixes() {
        List<CoreProbe.Step> steps = new ArrayList<CoreProbe.Step>();
        steps.add(Probe191Fixes::swingSetup);
        steps.addAll(clicking("vanilla, at the sky", true, false, -90));
        steps.addAll(clicking("fixed, at the sky", false, false, -90));
        steps.addAll(clicking("vanilla, at the ground", true, false, 90));
        steps.addAll(clicking("fixed, at the ground", false, false, 90));
        steps.addAll(clicking("fixed with LegacySwing, at the ground", false, true, 90));
        steps.addAll(clicking("fixed, blocking with 1.7 Animations", false, false, 0));
        steps.add(Probe191Fixes::swingDone);
        steps.add(mc -> {
            if (fixes == null) fixes = new FixesQa();
            if (!fixes.tick(GAME)) return retry(0);
            check(fixes.failures().isEmpty(), "Fullbright, name tags and the pause menu's Multiplayer: " + fixes.failures());
            return after(5);
        });
        return steps;
    }

    static List<CoreProbe.Step> dialogs() {
        return Arrays.<CoreProbe.Step>asList(mc -> {
            if (dialogs == null) dialogs = new FileDialogsQa(new File(mc.mcDataDir, "lads-qa/screenshots").toPath());
            if (!dialogs.tick()) return retry(0);
            check(dialogs.failures().isEmpty(), "file dialogs: " + dialogs.failures());
            mc.displayGuiScreen(new SkinChangerScreen189(null)); // its Add File: the same modern dialog, and closing it is a cancel
            return after(10);
        }, mc -> {
            GuiButton add = ((SkinChangerScreen189) mc.currentScreen).button("Add File");
            CoreProbe.click(add.xPosition + add.width / 2, add.yPosition + add.height / 2);
            clicks = 0;
            return after(20);
        }, mc -> {
            if (!FileDialogsQa.showing()) {
                check(++clicks < 40, "the Skin changer's Add File opens a dialog within 10 s");
                return retry(5);
            }
            return after(40); // let it paint
        }, mc -> {
            check(FileDialogsQa.photographAndClose(new File(mc.mcDataDir, "lads-qa/screenshots/dialog-skin-add-file.png").toPath()),
                "the Skin changer's Add File opens the modern Windows dialog, owned by the game window");
            clicks = 0;
            return after(10);
        }, mc -> {
            String status = ((SkinChangerScreen189) mc.currentScreen).status();
            if (!"Selection canceled".equals(status) && ++clicks < 40) return retry(5);
            CoreProbe.screenshot(mc, "dialog-skin-canceled");
            check("Selection canceled".equals(status), "closing it is a cancel: " + status);
            mc.displayGuiScreen(null);
            return after(5);
        });
    }

    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }

    private static boolean swingSetup(Minecraft mc) {
        legacyWas = module("LegacySwing").isEnabled();
        animationsWas = module(OldAnimationsModule.NAME).isEnabled();
        focusWas = mc.inGameHasFocus;
        mc.inGameHasFocus = true; // QA: clicks must not grab the mouse of a window the player is not in
        Probe151.onServer(mc, player -> {
            player.setGameType(WorldSettings.GameType.SURVIVAL);
            player.inventory.mainInventory[0] = new ItemStack(Items.diamond_sword);
        });
        Probe151.select(mc, 0);
        return after(20);
    }

    /** Twenty clicks, one every second tick, each checked the tick after it was read. */
    private static List<CoreProbe.Step> clicking(final String label, final boolean vanilla, final boolean legacy, final float pitch) {
        final boolean blocking = label.contains("blocking");
        return Arrays.<CoreProbe.Step>asList(
            mc -> {
                OldAnimations189.qaVanillaSwing = vanilla;
                module("LegacySwing").setEnabled(legacy);
                module(OldAnimationsModule.NAME).setEnabled(blocking || animationsWas);
                look(mc, 0, pitch);
                if (blocking) Probe151.key(mc.gameSettings.keyBindUseItem, true);
                clicks = started = phase = 0;
                return after(blocking ? 10 : 5);
            },
            mc -> {
                if (blocking && clicks == 0) check(mc.thePlayer.isUsingItem(), label + ": the sword blocks");
                if (phase == 0) { // a click as the mouse sends it: runTick reads it next tick
                    CoreProbe.mouse(0, true, 0, 0);
                    CoreProbe.mouse(0, false, 0, 0);
                    clicks++;
                    phase = 1;
                    return retry(0);
                }
                if (mc.thePlayer.isSwingInProgress && mc.thePlayer.swingProgressInt == 0) started++;
                if (clicks == 6) CoreProbe.screenshot(mc, "191-swing-" + label.replaceAll("[^a-z0-9]+", "-"));
                phase = 0;
                if (clicks < CLICKS) return retry(0);
                LOG.info("Lads fixes swing: {}: {} of {} clicks at 10 a second started a swing", label, started, clicks);
                if (vanilla) check(true, label + ": " + started + " of " + clicks + " clicks swing (vanilla skips the ones in a swing's first half)");
                else check(started == clicks, label + ": every click swings (" + started + " of " + clicks + ")");
                if (blocking) Probe151.key(mc.gameSettings.keyBindUseItem, false);
                return after(10);
            });
    }

    private static boolean swingDone(Minecraft mc) {
        OldAnimations189.qaVanillaSwing = false;
        module("LegacySwing").setEnabled(legacyWas);
        module(OldAnimationsModule.NAME).setEnabled(animationsWas);
        mc.inGameHasFocus = focusWas;
        Probe151.onServer(mc, player -> {
            player.setGameType(WorldSettings.GameType.CREATIVE);
            player.inventory.mainInventory[0] = null;
        });
        return after(20);
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = yaw;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitch;
    }

    /** QA rule: the server list pings every entry, so it only opens on local ones; an empty list gets two nobody listens on. */
    static void localServersOnly() {
        ServerList list = new ServerList(Minecraft.getMinecraft());
        list.loadServerList();
        for (int i = 0; i < list.countServers(); i++) {
            String ip = list.getServerData(i).serverIP.toLowerCase(Locale.ROOT);
            if (!ip.startsWith("localhost") && !ip.startsWith("127.0.0.1")) throw new IllegalStateException("the QA server list has a non-local entry " + ip);
        }
        if (list.countServers() == 0) {
            list.addServerData(new ServerData("QA Local 1", "127.0.0.1:25599", false));
            list.addServerData(new ServerData("QA Local 2", "localhost:25598", false));
            list.saveServerList();
        }
    }

    private static List<GuiButton> buttons(GuiScreen screen) {
        return ReflectionHelper.getPrivateValue(GuiScreen.class, screen, "buttonList", "field_146292_n");
    }

    private static final FixesQa.Game GAME = new FixesQa.Game() {
        @Override public double[] feet() {
            Minecraft mc = Minecraft.getMinecraft();
            return new double[] {mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ};
        }

        @Override public void command(final String command) {
            final MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
            server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
        }

        @Override public void fill(int x1, int y1, int z1, int x2, int y2, int z2, boolean stone) {
            command("fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + (stone ? " minecraft:stone 0 hollow" : " minecraft:air"));
        }

        @Override public void summonTag(String name, double x, double y, double z) {
            command(String.format(Locale.ROOT, "summon ArmorStand %.2f %.2f %.2f {CustomName:\"%s\",CustomNameVisible:1,NoGravity:1}", x, y, z, name));
        }

        @Override public void removeTags() { command("kill @e[type=ArmorStand]"); }

        @Override public void look(float yaw, float pitch) { Probe191Fixes.look(Minecraft.getMinecraft(), yaw, pitch); }

        @Override public Path screenshot(String name) {
            Minecraft mc = Minecraft.getMinecraft();
            CoreProbe.screenshot(mc, name);
            return new File(mc.mcDataDir, "lads-qa/screenshots/" + name + ".png").toPath();
        }

        @Override public boolean inWorld() { return Minecraft.getMinecraft().theWorld != null; }

        @Override public void openPause() { Minecraft.getMinecraft().displayGuiScreen(new GuiIngameMenu()); }

        @Override public void closeScreen() { Minecraft.getMinecraft().displayGuiScreen(null); }

        @Override public void press(String label) {
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            for (GuiButton button : buttons(screen))
                if (button.visible && button.enabled && button.displayString.equalsIgnoreCase(label)) { click(button); return; }
            throw new IllegalStateException("no button '" + label + "' on " + screen.getClass().getName());
        }

        @Override public void back() {
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            for (String wanted : new String[] {"back", "cancel"})
                for (GuiButton button : buttons(screen))
                    if (button.visible && button.enabled && button.displayString.toLowerCase(Locale.ROOT).contains(wanted)) { click(button); return; }
            throw new IllegalStateException("no Back or Cancel button on " + screen.getClass().getName());
        }

        private void click(GuiButton button) {
            try {
                CoreProbe.click(button.xPosition + button.width / 2, button.yPosition + button.height / 2); // GuiScreen.mouseClicked next tick
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        @Override public String screen() {
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            if (screen == null) return "none";
            if (screen instanceof GuiIngameMenu) return "pause";
            if (screen instanceof GuiMultiplayer) return "servers";
            if (screen instanceof GuiConnecting) return "connect";
            if (screen instanceof net.minecraft.client.gui.GuiDisconnected) return "failed";
            if (screen instanceof GuiMainMenu || screen.getClass().getSimpleName().equals("LadsTitleScreen189")) return "title";
            return screen.getClass().getName();
        }

        @Override public void localServersOnly() { Probe191Fixes.localServersOnly(); }

        @Override public void joinFirstServer() {
            GuiMultiplayer servers = (GuiMultiplayer) Minecraft.getMinecraft().currentScreen;
            servers.selectServer(0);
            servers.connectToSelected();
        }

        @Override public Path worldFolder() {
            Minecraft mc = Minecraft.getMinecraft();
            return new File(new File(mc.mcDataDir, "saves"), mc.getIntegratedServer().getFolderName()).toPath();
        }
    };
}
