package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.ladsapi.LadsApiQa;
import com.thelads.core.client.qa.QaProgress;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Scripted QA runner for Minecraft 1.8.9 driven by -Dthelads.qaScript.
 * Executes verification steps (ladsapi probe, settings, shot, disconnect, connect, cmd, wait, quit)
 * and updates the live QA boss bar. Java 8 compatible.
 */
public final class QaProbe189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore");

    private enum Phase { TITLE, SCRIPT, DONE }

    private static boolean registered, active, failed, scriptStarted, scriptDone;
    private static Phase phase = Phase.TITLE;
    private static Path gameDir;
    private static List<String[]> steps = new ArrayList<String[]>();
    private static int index, waitTicks, titleTicks, passed, failedSteps;
    private static String pendingShot;
    /** "hold tab": regaining focus reads every key from the keyboard again (KeyBinding.updateKeyBindState), so it is set each tick. */
    private static boolean holdTab;
    private static long nextStopCheck;

    public QaProbe189() {}

    public static void register() {
        if (registered) return;
        String script = System.getProperty("thelads.qaScript");
        if (script == null || script.trim().isEmpty()) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(new QaProbe189());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        if (!active) {
            try {
                gameDir = mc.mcDataDir.toPath().toRealPath();
                active = true;
                String script = System.getProperty("thelads.qaScript");
                if (script != null && !script.trim().isEmpty()) steps = parse(Paths.get(script));
                LOGGER.info("Lads 1.8.9 QA script BEGIN: {} steps from {}", steps.size(), script);
            } catch (Exception e) {
                fail("init", e);
                return;
            }
        }
        if (failed) return;
        if (holdTab) net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindPlayerList.getKeyCode(), true);
        long now = System.nanoTime();
        if (now >= nextStopCheck) {
            nextStopCheck = now + 250_000_000L;
            if (Files.isRegularFile(gameDir.resolve(".lads-qa-stop"))) {
                LOGGER.info("Lads 1.8.9 QA STOP: graceful stop request received");
                active = false;
                mc.shutdown();
                return;
            }
        }
        try {
            switch (phase) {
                case TITLE:
                    title(mc);
                    break;
                case SCRIPT:
                    script(mc);
                    break;
                case DONE:
                    break;
            }
        } catch (Exception e) {
            fail(phase.name().toLowerCase(Locale.ROOT), e);
        }
    }

    private static void title(Minecraft mc) {
        if (mc.theWorld != null && mc.thePlayer != null && mc.currentScreen == null && mc.thePlayer.ticksExisted >= 20) {
            LOGGER.info("Lads 1.8.9 QA READY: joined server/world alive and unpaused");
            phase = Phase.SCRIPT;
            return;
        }
        if (mc.currentScreen instanceof GuiMainMenu) {
            if (++titleTicks >= 65) {
                LOGGER.info("Lads 1.8.9 QA READY: title screen script ready");
                phase = Phase.SCRIPT;
            }
        }
    }

    private static void script(Minecraft mc) {
        if (steps.isEmpty()) {
            if (!scriptDone) {
                scriptDone = true;
                phase = Phase.DONE;
                QaProgress.begin("1.8.9 QA", 1);
                QaProgress.step("empty script");
                QaProgress.done();
            }
            return;
        }
        if (!scriptStarted) {
            scriptStarted = true;
            LOGGER.info("Lads 1.8.9 qa script BEGIN: {} steps", steps.size());
            QaProgress.begin("1.8.9 script", steps.size());
            QaProgress.slowAfter(600_000);
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (pendingShot != null) {
            String name = pendingShot;
            pendingShot = null;
            try {
                shot(mc, name);
                LOGGER.info("Lads 1.8.9 qa shot: {}", name);
                passed++;
            } catch (Exception e) {
                stepFailed("shot " + name, e);
            }
        }
        while (waitTicks == 0 && pendingShot == null && index < steps.size()) {
            String[] step = steps.get(index++);
            String label = String.join(" ", step);
            LOGGER.info("Lads 1.8.9 qa step {}/{}: {}", index, steps.size(), label);
            QaProgress.step(label);
            try {
                run(mc, step);
                if (!step[0].equals("wait")) {
                    if (waitTicks == 0) waitTicks = 1;
                }
                passed++;
                if (scriptDone) break;
            } catch (Exception e) {
                stepFailed(label, e);
                waitTicks = 1;
            }
        }
        if (!scriptDone && waitTicks == 0 && pendingShot == null && index >= steps.size()) {
            scriptDone = true;
            phase = Phase.DONE;
            QaProgress.done();
            LOGGER.info("Lads 1.8.9 qa script END: {} passed, {} failed", passed, failedSteps);
        }
    }

    private static void stepFailed(String what, Exception e) {
        failedSteps++;
        QaProgress.result(QaProgress.Result.FAIL);
        LOGGER.error("Lads 1.8.9 qa step FAILED: {}: {}", what, e.toString());
    }

    /** QA: a servers.dat with no stored choice, Disabled and Enabled, read by the game's own list; the title Network button's entry. The file is put back. */
    private static void packs113(Minecraft mc) throws IOException {
        File file = new File(mc.mcDataDir, "servers.dat");
        byte[] before = file.exists() ? Files.readAllBytes(file.toPath()) : null;
        net.minecraft.nbt.NBTTagList servers = new net.minecraft.nbt.NBTTagList();
        for (String kind : new String[] {"none", "false", "true"}) {
            net.minecraft.nbt.NBTTagCompound entry = new net.minecraft.nbt.NBTTagCompound();
            entry.setString("name", "QA " + kind);
            entry.setString("ip", "qa-" + kind + ".invalid");
            if (!kind.equals("none")) entry.setBoolean("acceptTextures", kind.equals("true"));
            servers.appendTag(entry);
        }
        net.minecraft.nbt.NBTTagCompound root = new net.minecraft.nbt.NBTTagCompound();
        root.setTag("servers", servers);
        net.minecraft.nbt.CompressedStreamTools.write(root, file);
        net.minecraft.client.multiplayer.ServerList list = new net.minecraft.client.multiplayer.ServerList(mc);
        for (int i = 0; i < list.countServers(); i++) LOGGER.info("Lads 1.8.9 qa packs113: {} = {}", list.getServerData(i).serverIP, list.getServerData(i).getResourceMode());
        LOGGER.info("Lads 1.8.9 qa packs113: Network button entry = {}", new ServerData(com.thelads.core.client.LadsNetwork.NAME, com.thelads.core.client.LadsNetwork.ADDRESS, false).getResourceMode());
        if (before != null) Files.write(file.toPath(), before); else Files.delete(file.toPath());
    }

    private static void run(Minecraft mc, String[] s) throws Exception {
        String verb = s[0];
        switch (verb) {
            case "wait":
                waitTicks = duration(arg(s, 1));
                break;
            case "shot":
                pendingShot = arg(s, 1);
                waitTicks = 3;
                break;
            case "ladsapi":
                LadsApiQa.recordProbe(s.length > 1 ? rest(s, 1) : ("step-" + index));
                break;
            case "import113":
                com.thelads.core.client.gui.ClientImportQa.step(((LadsSettingsScreen189) mc.currentScreen).ui(), s);
                break;
            case "packs113":
                packs113(mc);
                break;
            case "settings": {
                LadsSettingsScreen189 screen = new LadsSettingsScreen189(mc.currentScreen);
                mc.displayGuiScreen(screen);
                if (s.length > 1) screen.openModule(rest(s, 1));
                break;
            }
            case "screen": {
                String target = arg(s, 1).toLowerCase(Locale.ROOT);
                if ("none".equals(target) || "close".equals(target)) {
                    mc.displayGuiScreen(null);
                } else if ("title".equals(target)) {
                    mc.displayGuiScreen(new GuiMainMenu());
                } else if ("pause".equals(target)) {
                    mc.displayGuiScreen(new net.minecraft.client.gui.GuiIngameMenu());
                } else if ("multiplayer".equals(target)) {
                    mc.displayGuiScreen(new net.minecraft.client.gui.GuiMultiplayer(mc.currentScreen));
                } else if ("options".equals(target)) {
                    mc.displayGuiScreen(new net.minecraft.client.gui.GuiOptions(mc.currentScreen, mc.gameSettings));
                } else if ("video".equals(target)) {
                    mc.displayGuiScreen(new net.minecraft.client.gui.GuiVideoSettings(mc.currentScreen, mc.gameSettings));
                } else if ("friends".equals(target)) {
                    mc.displayGuiScreen(new com.thelads.core.v1_8_9.social.FriendsScreen189(mc.currentScreen));
                } else if ("lads".equals(target) || "settings".equals(target)) {
                    LadsSettingsScreen189 ladsScreen = new LadsSettingsScreen189(mc.currentScreen);
                    mc.displayGuiScreen(ladsScreen);
                    if (s.length > 2) ladsScreen.openModule(rest(s, 2));
                }
                break;
            }
            case "titlelayout": // what the title's layout toggle does
                com.thelads.core.modules.TitleScreenModule.toggleLayout();
                break;
            case "disconnect":
                if (mc.theWorld != null) {
                    mc.theWorld.sendQuittingDisconnectingPacket();
                    mc.loadWorld((WorldClient) null);
                }
                mc.displayGuiScreen(new GuiMainMenu());
                break;
            case "connect": {
                String address = arg(s, 1);
                if (mc.theWorld != null) {
                    mc.theWorld.sendQuittingDisconnectingPacket();
                    mc.loadWorld((WorldClient) null);
                }
                ServerData data = new ServerData("QA", address, false);
                mc.displayGuiScreen(new GuiConnecting(new GuiMainMenu(), mc, data));
                break;
            }
            case "cmd":
                if (mc.thePlayer != null) {
                    mc.thePlayer.sendChatMessage("/" + rest(s, 1).replaceFirst("^/", ""));
                }
                break;
            case "chat":
                if (mc.thePlayer != null) {
                    mc.thePlayer.sendChatMessage(rest(s, 1));
                }
                break;
            case "srvcmd": { // a command run by the integrated server itself (full permission: QA worlds have cheats off)
                final net.minecraft.server.MinecraftServer server = mc.getIntegratedServer();
                if (server == null) throw new IllegalStateException("srvcmd: no integrated server");
                final String command = rest(s, 1).replaceFirst("^/", "");
                server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
                break;
            }
            case "hold": { // hold tab [off]: the player list, as holding Tab (the QA bar steps aside: both sit at the top)
                if (!"tab".equalsIgnoreCase(arg(s, 1))) throw new IllegalArgumentException("hold: tab");
                boolean down = !"off".equalsIgnoreCase(s.length > 2 ? s[2] : "on");
                holdTab = down;
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindPlayerList.getKeyCode(), down);
                com.thelads.core.client.qa.QaProgress.hide(down);
                break;
            }
            case "log":
                LOGGER.info("Lads 1.8.9 qa log: {}", rest(s, 1));
                break;
            case "account": { // account <offline name> | account ms <name>: the switcher open and that account clicked
                if (!(mc.currentScreen instanceof com.thelads.core.v1_8_9.gui.AccountSwitcherScreen189)) mc.displayGuiScreen(new com.thelads.core.v1_8_9.gui.AccountSwitcherScreen189(mc.currentScreen));
                com.thelads.core.client.auth.AccountSwitcherScreen ui = ((com.thelads.core.v1_8_9.gui.AccountSwitcherScreen189) mc.currentScreen).ui();
                if (arg(s, 1).equals("ms")) { com.thelads.core.client.auth.LadsAccount ms = new com.thelads.core.client.auth.LadsAccount(arg(s, 2), "069a79f444e94726a5befca90e38aaf5", "microsoft", "0"); ui.getAccounts().add(ms); ui.selectAccount(ms); }
                else ui.addOfflineAccount(arg(s, 1));
                break;
            }
            case "session":
                LOGGER.info("Lads 1.8.9 qa session: name={} uuid={} screen={} status={}", mc.getSession().getUsername(), mc.getSession().getPlayerID(),
                    mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName(),
                    mc.currentScreen instanceof com.thelads.core.v1_8_9.gui.AccountSwitcherScreen189 ? ((com.thelads.core.v1_8_9.gui.AccountSwitcherScreen189) mc.currentScreen).ui().getStatusMessage() : "");
                break;
            case "quit":
                LOGGER.info("Lads 1.8.9 qa quit requested");
                scriptDone = true;
                phase = Phase.DONE;
                QaProgress.done();
                mc.shutdown();
                break;
            default:
                LOGGER.warn("Lads 1.8.9 qa: unknown step '{}'", verb);
                break;
        }
    }

    static void shot(Minecraft mc, String name) {
        Path p = Paths.get(name.endsWith(".png") ? name : (name + ".png"));
        Path output = p.isAbsolute() ? p : (name.contains("/") || name.contains("\\") ? gameDir.resolve(p) : gameDir.resolve("screenshots").resolve(name + ".png"));
        File file = output.toFile();
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        ScreenShotHelper.saveScreenshot(parent, file.getName(), mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        if (!p.isAbsolute() && (name.startsWith("artifacts/") || name.startsWith("artifacts\\"))) {
            Path cwdOutput = Paths.get("").toAbsolutePath().resolve(p);
            if (!cwdOutput.normalize().equals(output.normalize())) {
                try {
                    File cwdFile = cwdOutput.toFile();
                    File cwdParent = cwdFile.getParentFile();
                    if (cwdParent != null) cwdParent.mkdirs();
                    ScreenShotHelper.saveScreenshot(cwdParent, cwdFile.getName(), mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                } catch (Throwable ignored) {}
            }
        }
    }

    private static int duration(String v) {
        v = v.toLowerCase(Locale.ROOT);
        if (v.endsWith("ms")) return Math.max(1, Integer.parseInt(v.substring(0, v.length() - 2)) / 50);
        if (v.endsWith("s")) return Math.max(1, (int) Math.round(Double.parseDouble(v.substring(0, v.length() - 1)) * 20));
        return Math.max(1, Integer.parseInt(v));
    }

    private static String arg(String[] s, int i) {
        if (i >= s.length) throw new IllegalArgumentException("'" + s[0] + "' needs argument " + i);
        return s[i];
    }

    private static String rest(String[] s, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(s, Math.min(from, s.length), s.length));
    }

    private static List<String[]> parse(Path file) throws IOException {
        List<String[]> out = new ArrayList<String[]>();
        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
            List<String> tokens = new ArrayList<String>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false, any = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') { quoted = !quoted; any = true; }
                else if (Character.isWhitespace(c) && !quoted) {
                    if (cur.length() > 0 || any) { tokens.add(cur.toString()); cur.setLength(0); any = false; }
                } else cur.append(c);
            }
            if (cur.length() > 0 || any) tokens.add(cur.toString());
            tokens.set(0, tokens.get(0).toLowerCase(Locale.ROOT));
            out.add(tokens.toArray(new String[0]));
        }
        return out;
    }

    private static void fail(String where, Exception e) {
        failed = true;
        QaProgress.result(QaProgress.Result.FAIL);
        QaProgress.done();
        LOGGER.error("Lads 1.8.9 QA FAILED: {}: {}", where, e.toString(), e);
    }
}
