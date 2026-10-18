package com.thelads.core.v1_20_1.cosmetics.anim;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.cosmetics.Emotes;
import com.thelads.core.client.cosmetics.anim.Emote;
import com.thelads.core.client.cosmetics.anim.EmoteAnimator;
import com.thelads.core.client.cosmetics.anim.EmoteLibrary;
import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.client.cosmetics.anim.EmoteWheel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (LADS_QA_EMOTE=1, with an --mc1201 run whose script waits): the emote in the QA world, photographed each client tick to
 * artifacts/verification/emote1911/1.20.1: the wheel, one loop in third person (F5 front) and in first person, the inventory preview,
 * a stop by walking in both views (a frame every tick, 50 ms, across the blend-out), a held sword, and a second (client-side) player
 * dancing and stopping when it moves. Logs which key bindings share the Emotes key. Ends with "Lads emote capture END".
 */
public final class EmoteCapture1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final TreeMap<Long, List<Runnable>> STEPS = new TreeMap<>();
    private static long startedAt = -1;
    private static int inWorld, passed, failed;
    private static boolean done, stoppedSeen;
    private static Path out;
    private static String pendingShot;
    private static RemotePlayer remote;
    private static final List<Float> stopWeights = new ArrayList<>();
    private static long walkAt;

    private EmoteCapture1201() {}

    static void register() {
        if (System.getenv("LADS_QA_EMOTE") != null) ClientTickEvents.END_CLIENT_TICK.register(EmoteCapture1201::tick);
    }

    private static void tick(Minecraft mc) {
        if (done || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (startedAt < 0) {
            if (mc.screen != null || ++inWorld < 100) return; // after the QA world's own capture
            out = FabricLoader.getInstance().getGameDir().toAbsolutePath().getParent().resolve("emote1911").resolve("1.20.1");
            startedAt = System.currentTimeMillis();
            LOGGER.info("Lads emote capture BEGIN: {}", out);
            plan(mc);
        }
        long now = System.currentTimeMillis() - startedAt;
        while (!STEPS.isEmpty() && STEPS.firstKey() <= now) for (Runnable step : STEPS.pollFirstEntry().getValue()) step.run();
        if (walkAt > 0) {
            Emotes.Playing p = Emotes.get(mc.player.getUUID());
            if (p != null && p.stopping()) stoppedSeen = true;
        }
        String name = pendingShot;
        if (name == null) return;
        pendingShot = null;
        if (name.startsWith("stop")) {
            EmotePose pose = EmoteAnimator.pose(mc.player.getUUID(), false, 0, 0, 0);
            float weight = pose == null ? 0 : pose.weight;
            stopWeights.add(weight);
            LOGGER.info("Lads emote capture frame {}: {} ms after the walk key, weight {}", name, System.currentTimeMillis() - walkAt, String.format(Locale.ROOT, "%.3f", weight));
        }
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(out);
            image.writeToFile(out.resolve(name + ".png"));
        } catch (Exception failure) {
            LOGGER.error("Lads emote capture {} failed", name, failure);
        }
    }

    private static void at(long ms, Runnable step) { STEPS.computeIfAbsent(ms, k -> new ArrayList<>()).add(step); }
    private static void shot(long ms, String name) { at(ms, () -> pendingShot = name); }

    private static void plan(Minecraft mc) {
        UUID self = mc.player.getUUID();
        Emote groove = EmoteLibrary.get(EmoteLibrary.GROOVE);
        at(0, () -> {
            command("time set noon"); command("weather clear"); command("clear @p"); command("effect clear @p");
            command("tp @p ~40 ~ ~");
            keyConflicts(mc);
            mc.gui.getChat().clearMessages(false);
        });
        at(1500, () -> {
            mc.player.setYRot(180); mc.player.setYHeadRot(180); mc.player.setYBodyRot(180); mc.player.setXRot(0);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        at(2500, () -> {
            mc.setScreen(new EmoteWheelScreen1201());
            EmoteWheelScreen1201.qaPointer = new int[]{mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2 - EmoteWheel.OUTER + 20};
        });
        shot(2900, "wheel");
        at(3100, () -> {
            if (mc.screen instanceof EmoteWheelScreen1201 wheel) wheel.keyReleased(EmoteAnim1201.KEY.getDefaultKey().getValue(), 0, 0);
            EmoteWheelScreen1201.qaPointer = null;
            mc.options.hideGui = true;
            Emotes.Playing playing = Emotes.get(self);
            check(playing != null && EmoteLibrary.GROOVE.equals(playing.id()) && mc.screen == null, "releasing the key over Groove closes the wheel and plays it");
        });
        long loop = 3100 + 400;
        for (int i = 0; i < 16; i++) shot(loop + Math.round(i * groove.periodMs / 16), String.format(Locale.ROOT, "3p-%02d", i));
        at(loop + 1000, () -> {
            EmotePose pose = EmoteAnimator.pose(self, false, 0, 0, 0);
            check(pose != null && pose.weight == 1, "blended fully in after 200 ms (weight " + (pose == null ? "none" : pose.weight) + ")");
        });
        long back = loop + (long) groove.periodMs + 100;
        at(back, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        shot(back + 300, "3p-back-a");
        shot(back + 300 + Math.round(groove.periodMs / 4), "3p-back-b");
        long inv = back + 1000;
        at(inv, () -> { mc.options.hideGui = false; mc.setScreen(new InventoryScreen(mc.player)); });
        shot(inv + 400, "inventory");
        at(inv + 600, () -> mc.setScreen(null));
        long fp = inv + 900;
        at(fp, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        for (int i = 0; i < 16; i++) shot(fp + 400 + Math.round(i * groove.periodMs / 16), String.format(Locale.ROOT, "fp-%02d", i));
        long fpDown = fp + 400 + (long) groove.periodMs + 100;
        at(fpDown, () -> mc.player.setXRot(25));
        for (int i = 0; i < 8; i++) shot(fpDown + 200 + Math.round(i * groove.periodMs / 8), String.format(Locale.ROOT, "fpdown-%02d", i));
        long stop3p = fpDown + 200 + (long) groove.periodMs + 300;
        at(stop3p - 400, () -> { mc.player.setXRot(0); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.options.hideGui = true; });
        walk(mc, self, stop3p, "stop3p");
        long stopFp = stop3p + 2500;
        at(stopFp - 1500, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; Emotes.playLocal(self, EmoteLibrary.GROOVE); });
        walk(mc, self, stopFp, "stopfp");
        long sword = stopFp + 2500;
        at(sword, () -> { command("give @p minecraft:iron_sword"); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.options.hideGui = true; });
        at(sword + 300, () -> Emotes.playLocal(self, EmoteLibrary.GROOVE));
        shot(sword + 300 + 1100, "3p-sword-a");
        shot(sword + 300 + 1100 + Math.round(groove.periodMs / 4), "3p-sword-b");
        at(sword + 2200, () -> Emotes.stopLocal(self));
        long other = sword + 2800;
        at(other, () -> {
            command("clear @p");
            remote = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "LadsDancer"));
            double yaw = Math.toRadians(mc.player.getYRot());
            remote.moveTo(mc.player.getX() - Math.cos(yaw) * 1.6, mc.player.getY(), mc.player.getZ() - Math.sin(yaw) * 1.6, mc.player.getYRot(), 0);
            remote.setYHeadRot(mc.player.getYRot()); remote.setYBodyRot(mc.player.getYRot());
            mc.level.addPlayer(remote.getId(), remote);
            Emotes.onRemote(remote.getUUID(), EmoteLibrary.GROOVE, 700);
            Emotes.playLocal(self, EmoteLibrary.GROOVE);
        });
        shot(other + 1200, "remote-a");
        shot(other + 1200 + Math.round(groove.periodMs / 4), "remote-b");
        at(other + 1800, () -> remote.moveTo(remote.getX() + 1, remote.getY(), remote.getZ(), remote.getYRot(), 0));
        at(other + 1900, () -> {
            Emotes.Playing p = Emotes.get(remote.getUUID());
            check(p == null || p.stopping(), "another player who moves blends its emote out here before any network stop");
        });
        shot(other + 2000, "remote-moved");
        at(other + 2600, () -> {
            check(Emotes.get(remote.getUUID()) == null, "its blend-out finished and it left Emotes");
            mc.level.removeEntity(remote.getId(), Entity.RemovalReason.DISCARDED);
            Emotes.stopLocal(self);
        });
        at(other + 3200, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
            done = true;
            LOGGER.info("Lads emote capture END: {} passed, {} failed; frames in {}", passed, failed, out);
        });
    }

    private static void walk(Minecraft mc, UUID self, long at, String name) {
        at(at - 1, () -> { stopWeights.clear(); stoppedSeen = false; });
        at(at, () -> { mc.options.keyUp.setDown(true); walkAt = System.currentTimeMillis(); });
        for (int i = 0; i < 12; i++) shot(at + 10 + i * 50L, String.format(Locale.ROOT, "%s-%02d", name, i));
        at(at + 700, () -> {
            mc.options.keyUp.setDown(false);
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

    private static void command(String command) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void keyConflicts(Minecraft mc) {
        String key = EmoteAnim1201.KEY.saveString();
        StringBuilder same = new StringBuilder();
        java.util.Set<String> used = new java.util.TreeSet<>();
        for (KeyMapping mapping : mc.options.keyMappings) {
            used.add(mapping.saveString());
            if (mapping != EmoteAnim1201.KEY && mapping.saveString().equals(key)) same.append(mapping.getName()).append(' ');
        }
        StringBuilder free = new StringBuilder();
        for (char c = 'a'; c <= 'z'; c++) if (!used.contains("key.keyboard." + c)) free.append(c);
        LOGGER.info("Lads emote capture keys: Emotes = {}; also bound there: [{}]; free letters: {}", key, same.toString().trim(), free);
    }

    private static void check(boolean ok, String what) {
        if (ok) passed++; else failed++;
        LOGGER.info("Lads emote capture {}: {}", ok ? "PASS" : "FAIL", what);
    }
}
