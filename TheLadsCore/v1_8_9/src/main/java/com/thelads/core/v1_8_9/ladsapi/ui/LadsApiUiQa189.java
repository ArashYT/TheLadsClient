package com.thelads.core.v1_8_9.ladsapi.ui;

import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.client.ladsapi.minigame.Caps;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.client.ladsapi.minigame.LockState.Lock;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ScreenShotHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only (env LADS_QA_LADSUI or -Dthelads.qaLadsUi): a command file driver for the input/2D half of the minigame API, so a QA PC can
 * run the checks against a real server without a focused window. Write lines to {game dir}/lads-qa/ladsui.cmd; each is run in
 * order, and the result is logged as "LUIQA ...". Commands: wait N (ticks), press KEY, release KEY, status, shot NAME, screen
 * chat|pause|none, say TEXT (chat or a command), savecheck TOKEN (saves options.txt and says whether TOKEN is in it).
 */
final class LadsApiUiQa189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static Audio189 audio;
    private static ServerKeys189 keys;
    private static final ArrayDeque<String> QUEUE = new ArrayDeque<String>();
    private static int waiting;
    private static int ticks;

    private LadsApiUiQa189() {}

    static void init(Audio189 a, ServerKeys189 k) {
        audio = a;
        keys = k;
        LOG.info("LUIQA ready; commands in lads-qa/ladsui.cmd");
    }

    static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getMinecraft();
        if (QUEUE.isEmpty() && ticks % 10 == 0) {
            File f = new File(mc.mcDataDir, "lads-qa/ladsui.cmd");
            if (f.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                    File done = new File(f.getParentFile(), "ladsui.cmd.done");
                    done.delete();
                    f.renameTo(done);
                    for (String l : lines) if (!l.trim().isEmpty()) QUEUE.add(l.trim());
                } catch (java.io.IOException e) {
                    LOG.warn("LUIQA cannot read the command file", e);
                }
            }
        }
        if (waiting > 0) {
            waiting--;
            return;
        }
        while (!QUEUE.isEmpty() && waiting == 0) {
            String line = QUEUE.poll();
            try {
                run(mc, line);
            } catch (RuntimeException e) {
                LOG.warn("LUIQA command failed: " + line, e);
            }
        }
    }

    private static void run(Minecraft mc, String line) {
        int sp = line.indexOf(' ');
        String cmd = sp < 0 ? line : line.substring(0, sp);
        String arg = sp < 0 ? "" : line.substring(sp + 1).trim();
        if (cmd.equals("wait")) {
            waiting = Integer.parseInt(arg);
        } else if (cmd.equals("press") || cmd.equals("release")) {
            int code = ServerKeys189.code(arg.contains(".") && arg.startsWith("key.") ? arg : "key.keyboard." + arg);
            boolean down = cmd.equals("press");
            KeyBinding.setKeyBindState(code, down);
            if (down) KeyBinding.onTick(code);
            LOG.info("LUIQA {} {} code={}", cmd, arg, code);
        } else if (cmd.equals("status")) {
            status(mc, arg);
        } else if (cmd.equals("shot")) {
            File folder = new File(mc.mcDataDir, "lads-qa");
            new File(folder, "screenshots").mkdirs();
            ScreenShotHelper.saveScreenshot(folder, arg + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            LOG.info("LUIQA shot {} screen={}", arg, mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
        } else if (cmd.equals("screen")) {
            if (arg.equals("chat")) mc.displayGuiScreen(new GuiChat());
            else if (arg.equals("pause")) mc.displayGuiScreen(new GuiIngameMenu());
            else mc.displayGuiScreen(null);
            LOG.info("LUIQA screen {}", arg);
        } else if (cmd.equals("say")) {
            mc.thePlayer.sendChatMessage(arg);
            LOG.info("LUIQA say {}", arg);
        } else if (cmd.equals("controls")) {
            mc.displayGuiScreen(new net.minecraft.client.gui.GuiControls(new GuiIngameMenu(), mc.gameSettings));
            LOG.info("LUIQA controls opened");
        } else if (cmd.equals("mod")) {
            int msp = arg.lastIndexOf(' ');
            com.thelads.core.config.Module m = com.thelads.core.config.ModuleManager.getInstance().getModule(arg.substring(0, msp));
            if (m != null) m.setEnabled(arg.substring(msp + 1).equals("on"));
            LOG.info("LUIQA mod {} -> {}", arg, m == null ? "missing" : m.isEnabled());
        } else if (cmd.equals("mkprofile")) {
            com.thelads.core.config.ProfileManager.get().saveAs(arg);
            LOG.info("LUIQA mkprofile {} names={}", arg, com.thelads.core.config.ProfileManager.get().getProfileNames());
        } else if (cmd.equals("cfg")) {
            com.thelads.core.config.Module m = com.thelads.core.config.ModuleManager.getInstance().getModule("Server HUD 2");
            File f = com.thelads.core.client.util.ClientPaths.getConfigFile();
            String sum = "?";
            try {
                byte[] b = Files.readAllBytes(f.toPath());
                java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
                StringBuilder sb = new StringBuilder();
                for (byte x : d.digest(b)) sb.append(String.format("%02x", x));
                sum = sb.substring(0, 16) + " len=" + b.length;
            } catch (Exception e) {
                sum = "err " + e;
            }
            LOG.info("LUIQA cfg {} serverHud2={} profile={} file={} {}", arg, m == null ? "missing" : m.isEnabled(),
                Hooks.profiles == null ? "-" : Hooks.profiles.current(), f.getName(), sum);
        } else if (cmd.equals("search")) {
            if (mc.currentScreen instanceof com.thelads.core.v1_8_9.gui.ControlsScreen189) {
                ((com.thelads.core.v1_8_9.gui.ControlsScreen189) mc.currentScreen).search().setText(arg);
                mc.currentScreen.initGui();
            }
            LOG.info("LUIQA search {}", arg);
        } else if (cmd.equals("scroll")) {
            try {
                java.lang.reflect.Field f = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
                    net.minecraft.client.gui.GuiControls.class, "keyBindingList", "field_146494_r");
                ((net.minecraft.client.gui.GuiSlot) f.get(mc.currentScreen)).scrollBy(Integer.parseInt(arg));
                LOG.info("LUIQA scrolled {}", arg);
            } catch (Exception e) {
                LOG.warn("LUIQA scroll failed", e);
            }
        } else if (cmd.equals("rebind")) {
            String[] a = arg.split(" ");
            LOG.info("LUIQA rebind {} -> {}", arg, keys.rebind(a[0], ServerKeys189.code("key.keyboard." + a[1])));
        } else if (cmd.equals("click")) {
            String[] a = arg.split(" ");
            LOG.info("LUIQA click {} -> {}", arg, DialogOverlay.click(Integer.parseInt(a[0]), Integer.parseInt(a[1])));
        } else if (cmd.equals("slider")) {
            String[] a = arg.split(" ");
            mc.gameSettings.setSoundLevel(net.minecraft.client.audio.SoundCategory.valueOf(a[0].toUpperCase()), Float.parseFloat(a[1]));
            LOG.info("LUIQA slider {} = {}", a[0], a[1]);
        } else if (cmd.equals("savecheck")) {
            mc.gameSettings.saveOptions();
            boolean found = false;
            try {
                for (String l : Files.readAllLines(new File(mc.mcDataDir, "options.txt").toPath(), StandardCharsets.UTF_8)) {
                    if (l.contains(arg)) found = true;
                }
            } catch (java.io.IOException e) {
                LOG.warn("LUIQA cannot read options.txt", e);
            }
            LOG.info("LUIQA savecheck token={} inOptionsTxt={}", arg, found);
        } else {
            LOG.info("LUIQA unknown command {}", line);
        }
    }

    private static void status(Minecraft mc, String tag) {
        long now = Minigame.now();
        String locks = "";
        for (Lock l : Lock.values()) if (Minigame.lock.locked(l, now)) locks += l.name() + " ";
        double x = mc.thePlayer == null ? 0 : mc.thePlayer.posX, z = mc.thePlayer == null ? 0 : mc.thePlayer.posZ;
        int inCategory = 0;
        for (KeyBinding k : mc.gameSettings.keyBindings) if (ServerKeys189.CATEGORY.equals(k.getKeyCategory())) inCategory++;
        LOG.info("LUIQA status {} screen={} pos={},{} locks=[{}] serverKeys[{}] controlsServerCat={} categorySet={} audio[{}] dialogs={} modal={} "
                + "caps={} device={} fwdDown={} moveFwd={} onGround={}", tag, mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName(),
            String.format(java.util.Locale.ROOT, "%.2f", x), String.format(java.util.Locale.ROOT, "%.2f", z), locks.trim(), keys.qa(),
            inCategory, KeyBinding.getKeybinds().contains(ServerKeys189.CATEGORY), audio.qa(), Minigame.dialogs.open(now).size(),
            DialogOverlay.modal() != null, Caps.declared(), com.thelads.core.client.ladsapi.minigame.Hooks.input.device(),
            mc.gameSettings.keyBindForward.isKeyDown(), mc.thePlayer == null ? 0 : mc.thePlayer.movementInput.moveForward,
            mc.thePlayer != null && mc.thePlayer.onGround);
    }
}
