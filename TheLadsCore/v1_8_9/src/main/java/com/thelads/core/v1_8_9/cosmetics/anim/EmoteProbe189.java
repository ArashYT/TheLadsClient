package com.thelads.core.v1_8_9.cosmetics.anim;

import com.mojang.authlib.GameProfile;
import com.thelads.core.client.cosmetics.Emotes;
import com.thelads.core.client.cosmetics.anim.Emote;
import com.thelads.core.client.cosmetics.anim.EmoteAnimator;
import com.thelads.core.client.cosmetics.anim.EmoteLibrary;
import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.client.cosmetics.anim.EmoteWheel;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/**
 * QA only (LADS_VERIFY_189_ONLY=emote, a CoreProbe step in its QA world): the emote photographed each client tick to
 * artifacts/verification/emote1911/1.8.9/screenshots: the wheel, one loop in third person (F5 front) and in first person, the
 * inventory preview, a stop by walking in both views (a frame every tick, 50 ms, across the blend-out), a held sword, and a second
 * (client-side) player dancing and stopping when it moves. Logs which key bindings share the Emotes key.
 */
public final class EmoteProbe189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final TreeMap<Long, List<Runnable>> STEPS = new TreeMap<Long, List<Runnable>>();
    private static long startedAt = -1, walkAt;
    private static int passed, failed;
    private static boolean done, stoppedSeen, hudWas;
    private static File out;
    private static String pendingShot;
    private static EntityOtherPlayerMP remote;
    private static final List<Float> stopWeights = new ArrayList<Float>();

    private EmoteProbe189() {}

    /** One CoreProbe step, every client tick until it returns true. */
    public static boolean run(final Minecraft mc) {
        if (startedAt < 0) {
            out = new File(mc.mcDataDir.getAbsoluteFile().getParentFile(), "emote1911/1.8.9");
            startedAt = System.currentTimeMillis();
            LOG.info("Lads emote capture BEGIN: {}", out);
            plan(mc);
        }
        long now = System.currentTimeMillis() - startedAt;
        while (!STEPS.isEmpty() && STEPS.firstKey() <= now) for (Runnable step : STEPS.pollFirstEntry().getValue()) step.run();
        if (walkAt > 0) {
            Emotes.Playing p = Emotes.get(mc.thePlayer.getUniqueID());
            if (p != null && p.stopping()) stoppedSeen = true;
        }
        String name = pendingShot;
        if (name != null) {
            pendingShot = null;
            if (name.startsWith("stop")) {
                EmotePose pose = EmoteAnimator.pose(mc.thePlayer.getUniqueID(), false, 0, 0, 0);
                float weight = pose == null ? 0 : pose.weight;
                stopWeights.add(weight);
                LOG.info("Lads emote capture frame {}: {} ms after the walk key, weight {}", name, System.currentTimeMillis() - walkAt, String.format(Locale.ROOT, "%.3f", weight));
            }
            out.mkdirs();
            new File(out, "screenshots").mkdirs();
            ScreenShotHelper.saveScreenshot(out, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        }
        return done;
    }

    private static void at(long ms, Runnable step) {
        List<Runnable> list = STEPS.get(ms);
        if (list == null) STEPS.put(ms, list = new ArrayList<Runnable>());
        list.add(step);
    }

    private static void shot(long ms, final String name) { at(ms, new Runnable() { public void run() { pendingShot = name; } }); }

    private static void plan(final Minecraft mc) {
        final UUID self = mc.thePlayer.getUniqueID();
        final Emote groove = EmoteLibrary.get(EmoteLibrary.GROOVE);
        at(0, () -> {
            command("time set 6000"); command("weather clear"); command("clear @p"); command("effect @p clear");
            command("tp @p ~40 ~ ~");
            keyConflicts(mc);
            mc.ingameGUI.getChatGUI().clearChatMessages();
            hudWas = mc.gameSettings.hideGUI;
        });
        at(1500, () -> {
            mc.thePlayer.rotationYaw = 180; mc.thePlayer.rotationYawHead = 180; mc.thePlayer.renderYawOffset = 180; mc.thePlayer.rotationPitch = 0;
            mc.gameSettings.thirdPersonView = 2;
        });
        at(2500, () -> {
            mc.displayGuiScreen(new EmoteWheelScreen189());
            ScaledResolution res = new ScaledResolution(mc);
            EmoteWheelScreen189.qaPointer = new int[]{res.getScaledWidth() / 2, res.getScaledHeight() / 2 - EmoteWheel.OUTER + 20};
        });
        shot(2900, "wheel");
        at(3100, () -> {
            if (mc.currentScreen instanceof EmoteWheelScreen189) ((EmoteWheelScreen189) mc.currentScreen).release();
            EmoteWheelScreen189.qaPointer = null;
            mc.gameSettings.hideGUI = true;
            Emotes.Playing playing = Emotes.get(self);
            check(playing != null && EmoteLibrary.GROOVE.equals(playing.id()) && mc.currentScreen == null, "releasing the key over Groove closes the wheel and plays it");
        });
        long loop = 3100 + 400;
        for (int i = 0; i < 16; i++) shot(loop + Math.round(i * groove.periodMs / 16), String.format(Locale.ROOT, "3p-%02d", i));
        at(loop + 1000, () -> {
            EmotePose pose = EmoteAnimator.pose(self, false, 0, 0, 0);
            check(pose != null && pose.weight == 1, "blended fully in after 200 ms (weight " + (pose == null ? "none" : String.valueOf(pose.weight)) + ")");
        });
        long back = loop + (long) groove.periodMs + 100;
        at(back, () -> mc.gameSettings.thirdPersonView = 1);
        shot(back + 300, "3p-back-a");
        shot(back + 300 + Math.round(groove.periodMs / 4), "3p-back-b");
        long inv = back + 1000;
        at(inv - 300, () -> command("gamemode 0 @p")); // the creative inventory has no player preview
        at(inv, () -> { mc.gameSettings.hideGUI = false; mc.displayGuiScreen(new GuiInventory(mc.thePlayer)); });
        shot(inv + 400, "inventory");
        at(inv + 600, () -> { mc.displayGuiScreen(null); command("gamemode 1 @p"); });
        long fp = inv + 900;
        at(fp, () -> mc.gameSettings.thirdPersonView = 0);
        for (int i = 0; i < 16; i++) shot(fp + 400 + Math.round(i * groove.periodMs / 16), String.format(Locale.ROOT, "fp-%02d", i));
        long fpDown = fp + 400 + (long) groove.periodMs + 100;
        at(fpDown, () -> mc.thePlayer.rotationPitch = 25);
        for (int i = 0; i < 8; i++) shot(fpDown + 200 + Math.round(i * groove.periodMs / 8), String.format(Locale.ROOT, "fpdown-%02d", i));
        long stop3p = fpDown + 200 + (long) groove.periodMs + 300;
        at(stop3p - 400, () -> { mc.thePlayer.rotationPitch = 0; mc.gameSettings.thirdPersonView = 2; mc.gameSettings.hideGUI = true; });
        walk(mc, self, stop3p, "stop3p");
        long stopFp = stop3p + 2500;
        at(stopFp - 1500, () -> { mc.gameSettings.thirdPersonView = 0; mc.gameSettings.hideGUI = false; Emotes.playLocal(self, EmoteLibrary.GROOVE); });
        walk(mc, self, stopFp, "stopfp");
        long sword = stopFp + 2500;
        at(sword, () -> { command("give @p minecraft:iron_sword"); mc.gameSettings.thirdPersonView = 2; mc.gameSettings.hideGUI = true; });
        at(sword + 300, () -> Emotes.playLocal(self, EmoteLibrary.GROOVE));
        shot(sword + 300 + 1100, "3p-sword-a");
        shot(sword + 300 + 1100 + Math.round(groove.periodMs / 4), "3p-sword-b");
        // First person with the held items: each moves with its dancing hand, as in third person.
        at(sword + 2000, () -> { mc.gameSettings.thirdPersonView = 0; mc.gameSettings.hideGUI = false; });
        for (int i = 0; i < 4; i++) shot(sword + 2400 + Math.round(i * groove.periodMs / 4), "fp-items-" + i);
        at(sword + 2400 + (long) groove.periodMs, () -> Emotes.stopLocal(self));
        long other = sword + 2400 + (long) groove.periodMs + 600;
        at(other, () -> {
            command("clear @p");
            remote = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "LadsDancer"));
            double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
            remote.setPositionAndRotation(mc.thePlayer.posX - Math.cos(yaw) * 1.6, mc.thePlayer.posY, mc.thePlayer.posZ - Math.sin(yaw) * 1.6, mc.thePlayer.rotationYaw, 0);
            remote.rotationYawHead = mc.thePlayer.rotationYaw; remote.renderYawOffset = mc.thePlayer.rotationYaw;
            mc.theWorld.addEntityToWorld(-1911, remote);
            Emotes.onRemote(remote.getUniqueID(), EmoteLibrary.GROOVE, 700);
            Emotes.playLocal(self, EmoteLibrary.GROOVE);
            mc.gameSettings.thirdPersonView = 2; mc.gameSettings.hideGUI = true;
        });
        shot(other + 1200, "remote-a");
        shot(other + 1200 + Math.round(groove.periodMs / 4), "remote-b");
        // Cosmetics "Show other players' emotes" off: the other player stands at once, we keep dancing.
        at(other + 2000, () -> cosmetics().showOthers.set(false));
        shot(other + 2300, "remote-hidden");
        at(other + 2400, () -> {
            check(EmoteAnimator.pose(remote.getUniqueID(), true, remote.posX, remote.posY, remote.posZ) == null
                && EmoteAnimator.pose(self, false, 0, 0, 0) != null, "\"Show other players' emotes\" off: the other player is not posed, we still dance");
            cosmetics().showOthers.set(true);
        });
        at(other + 2800, () -> remote.setPositionAndRotation(remote.posX + 1, remote.posY, remote.posZ, remote.rotationYaw, 0));
        at(other + 2900, () -> {
            Emotes.Playing p = Emotes.get(remote.getUniqueID());
            check(p == null || p.stopping(), "another player who moves blends its emote out here before any network stop");
        });
        shot(other + 3000, "remote-moved");
        at(other + 3600, () -> {
            check(Emotes.get(remote.getUniqueID()) == null, "its blend-out finished and it left Emotes");
            mc.theWorld.removeEntityFromWorld(-1911);
            Emotes.stopLocal(self);
        });
        long cos = other + 4000;
        at(cos, () -> mc.displayGuiScreen(new CosmeticsScreen189(null)));
        shot(cos + 400, "cosmetics-screen");
        long pause = cos + 700;
        at(pause, () -> mc.displayGuiScreen(new GuiIngameMenu()));
        shot(pause + 400, "pause-menu");
        long closeScreens = pause + 700;
        at(closeScreens, () -> mc.displayGuiScreen(null));
        long grooveKeyTest = closeScreens + 300;
        at(grooveKeyTest, () -> {
            KeyBinding grooveBinding = EmoteAnim189.key(EmoteLibrary.GROOVE);
            if (grooveBinding != null) {
                grooveBinding.setKeyCode(Keyboard.KEY_K);
                KeyBinding.resetKeyBindingArrayAndHash();
                KeyBinding.onTick(Keyboard.KEY_K);
            }
        });
        at(grooveKeyTest + 100, () -> {
            Emotes.Playing playing = Emotes.get(self);
            check(playing != null && EmoteLibrary.GROOVE.equals(playing.id()) && !playing.stopping(),
                "Groove emote key press plays groove and is not stopping");
            KeyBinding.onTick(Keyboard.KEY_K);
        });
        at(grooveKeyTest + 200, () -> {
            Emotes.Playing playing = Emotes.get(self);
            check(playing != null && EmoteLibrary.GROOVE.equals(playing.id()) && playing.stopping(),
                "Groove emote key press again stops groove");
            KeyBinding grooveBinding = EmoteAnim189.key(EmoteLibrary.GROOVE);
            if (grooveBinding != null) {
                grooveBinding.setKeyCode(Keyboard.KEY_NONE);
                KeyBinding.resetKeyBindingArrayAndHash();
            }
            Emotes.stopLocal(self);
        });
        at(grooveKeyTest + 500, () -> {
            mc.gameSettings.thirdPersonView = 0;
            mc.gameSettings.hideGUI = hudWas;
            done = true;
            LOG.info("Lads emote capture END: {} passed, {} failed; frames in {}", passed, failed, out);
        });
    }

    private static void walk(final Minecraft mc, final UUID self, long at, final String name) {
        final int forward = mc.gameSettings.keyBindForward.getKeyCode();
        at(at - 1, () -> { stopWeights.clear(); stoppedSeen = false; });
        at(at, () -> { KeyBinding.setKeyBindState(forward, true); walkAt = System.currentTimeMillis(); });
        for (int i = 0; i < 12; i++) shot(at + 10 + i * 50L, String.format(Locale.ROOT, "%s-%02d", name, i));
        at(at + 700, () -> {
            KeyBinding.setKeyBindState(forward, false);
            boolean smooth = stopWeights.size() >= 5;
            float biggest = 0;
            for (int i = 1; i < stopWeights.size(); i++) {
                float step = stopWeights.get(i - 1) - stopWeights.get(i);
                if (step < -1e-4f) smooth = false;
                biggest = Math.max(biggest, step);
            }
            check(stoppedSeen, name + ": walking stopped the emote");
            check(smooth && biggest < 0.45f && Emotes.get(self) == null, name + ": the pose blended out over frames, no cut (weights " + stopWeights
                + ", largest step " + biggest + "), and the emote left Emotes");
        });
    }

    private static com.thelads.core.modules.CosmeticsModule cosmetics() {
        return (com.thelads.core.modules.CosmeticsModule) com.thelads.core.config.ModuleManager.getInstance().getModule("Cosmetics");
    }

    private static void command(final String command) {
        final MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
        server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
    }

    private static void keyConflicts(Minecraft mc) {
        int key = EmoteAnim189.KEY.getKeyCode();
        StringBuilder same = new StringBuilder(), free = new StringBuilder();
        boolean clash = false;
        java.util.Set<Integer> used = new java.util.HashSet<Integer>();
        for (KeyBinding binding : mc.gameSettings.keyBindings) {
            used.add(binding.getKeyCode());
            if (binding != EmoteAnim189.KEY && binding.getKeyCode() == key) same.append(binding.getKeyDescription()).append(' ');
            if (com.thelads.core.client.cosmetics.anim.EmoteKeys.GIVE_WAY.contains(binding.getKeyDescription()) && binding.getKeyCode() == key) clash = true;
        }
        for (char c = 'A'; c <= 'Z'; c++) if (!used.contains(Keyboard.getKeyIndex(String.valueOf(c)))) free.append(Character.toLowerCase(c));
        check(!clash, "no other mod's key shares B with Emotes any more (Xaero's New Waypoint gave way once)");
        LOG.info("Lads emote capture keys: Emotes = {}; also bound there: [{}]; free letters: {}", Keyboard.getKeyName(key), same.toString().trim(), free);
    }

    private static void check(boolean ok, String what) {
        if (ok) passed++; else failed++;
        LOG.info("Lads emote capture {}: {}", ok ? "PASS" : "FAIL", what);
    }
}
