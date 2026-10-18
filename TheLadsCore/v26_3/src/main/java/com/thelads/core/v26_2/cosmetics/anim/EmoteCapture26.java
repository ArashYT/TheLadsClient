package com.thelads.core.v26_2.cosmetics.anim;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.pipeline.RenderTarget;
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
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (LADS_QA_EMOTE=1): the emote in the QA world, photographed to artifacts/verification/emote1911/26.3: the wheel, one loop in
 * third person (F5 front) and in first person, the inventory preview, a stop by walking in both views (a frame every ~50 ms across the
 * blend-out), a held sword, and a second (client-side) player dancing and stopping when it moves. Logs which key bindings share the
 * Emotes key. Ends with "Lads emote capture END: N passed, M failed".
 */
public final class EmoteCapture26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final TreeMap<Long, List<Runnable>> STEPS = new TreeMap<>();
    private static long startedAt = -1;
    private static boolean done;
    private static int passed, failed;
    private static Path out;
    private static final java.util.ArrayDeque<String> shots = new java.util.ArrayDeque<>();
    private static String burst;
    private static int burstFrame;
    private static boolean hudWas;
    private static RemotePlayer remote;
    private static final List<Float> stopWeights = new ArrayList<>();
    private static long walkAt;
    private static boolean stoppedSeen;

    private EmoteCapture26() {}

    public static boolean busy() { return startedAt >= 0 && !done; }

    public static void tick(Path game, boolean ready) {
        if (done || startedAt >= 0 || !ready || System.getenv("LADS_QA_EMOTE") == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        out = game.getParent().resolve("emote1911").resolve("26.3");
        startedAt = System.currentTimeMillis();
        LOGGER.info("Lads emote capture BEGIN: {}", out);
        plan(mc, game);
        com.thelads.core.client.qa.QaProgress.begin("emotes", STEPS.values().stream().mapToInt(List::size).sum()); // one bar step per scheduled action
    }

    private static void at(long ms, Runnable step) { STEPS.computeIfAbsent(ms, k -> new ArrayList<>()).add(step); }
    private static void shot(long ms, String name) { at(ms, () -> shots.add(name)); }

    private static void plan(Minecraft mc, Path game) {
        UUID self = mc.player.getUUID();
        Emote groove = EmoteLibrary.get(EmoteLibrary.GROOVE);
        at(0, () -> {
            command("time set noon"); command("weather clear"); command("clear @p"); command("effect clear @p");
            command("tp @p ~40 ~ ~");
            keyConflicts(mc);
            mc.gui.hud.getChat().clearMessages(false);
            hudWas = mc.gui.hud.isHidden();
            // The checkerboard QA skin (3D Skin Layers draws its outer layer as cubes, they must follow the dance) and a test cape.
            try {
                Path png = game.resolve(".lads-qa-emote-skin.png");
                Files.write(png, com.thelads.core.client.QaSkin.png());
                com.thelads.core.v26_2.feature.LocalSkins.load(png.toString(), false, false);
            } catch (Exception failure) { LOGGER.warn("Lads emote capture: no QA skin", failure); }
        });
        at(1200, () -> {
            try {
                var image = new com.mojang.blaze3d.platform.NativeImage(64, 32, true);
                for (int x = 0; x < 64; x++) for (int y = 0; y < 32; y++) image.setPixel(x, y, com.thelads.core.client.WavyCape.qaPixel(x, y));
                var cape = net.minecraft.resources.Identifier.fromNamespaceAndPath("theladscore", "qa_emote_cape");
                mc.getTextureManager().register(cape, new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Lads QA cape", image));
                var selected = com.thelads.core.v26_2.feature.LocalSkins.class.getDeclaredField("selected");
                selected.setAccessible(true);
                var skin = mc.player.getSkin();
                selected.set(null, new net.minecraft.world.entity.player.PlayerSkin(skin.body(), new net.minecraft.core.ClientAsset.ResourceTexture(cape, cape), null, skin.model(), false));
            } catch (Exception failure) { LOGGER.warn("Lads emote capture: no test cape", failure); }
        });
        at(1500, () -> {
            mc.player.setYRot(180); mc.player.setYHeadRot(180); mc.player.setYBodyRot(180); mc.player.setXRot(0);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        // The wheel: open with the pointer on Groove (the top slot), photographed, then the key released over it.
        at(2500, () -> {
            mc.setScreenAndShow(new EmoteWheelScreen26());
            EmoteWheelScreen26.qaPointer = new int[]{mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2 - EmoteWheel.OUTER + 20};
        });
        shot(2900, "wheel");
        at(3100, () -> {
            if (mc.gui.screen() instanceof EmoteWheelScreen26 wheel) wheel.keyReleased(new KeyEvent(EmoteAnim26.KEY.getDefaultKey().getValue(), 0, 0));
            EmoteWheelScreen26.qaPointer = null;
            hud(mc, true);
            Emotes.Playing playing = Emotes.get(self);
            check(playing != null && EmoteLibrary.GROOVE.equals(playing.id()) && mc.gui.screen() == null, "releasing the key over Groove closes the wheel and plays it");
        });
        // One loop in third person, 16 frames, after the blend-in.
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
        at(inv, () -> { hud(mc, false); mc.setScreenAndShow(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)); });
        shot(inv + 400, "inventory");
        at(inv + 600, () -> mc.setScreenAndShow(null));
        // First person: one loop looking ahead, half a loop looking 25 degrees down.
        long fp = inv + 900;
        at(fp, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        for (int i = 0; i < 16; i++) shot(fp + 400 + Math.round(i * groove.periodMs / 16), String.format(Locale.ROOT, "fp-%02d", i));
        long fpDown = fp + 400 + (long) groove.periodMs + 100;
        at(fpDown, () -> mc.player.setXRot(25));
        for (int i = 0; i < 8; i++) shot(fpDown + 200 + Math.round(i * groove.periodMs / 8), String.format(Locale.ROOT, "fpdown-%02d", i));
        // Walking mid-dance in third person: a frame every 50 ms across the blend-out.
        long stop3p = fpDown + 200 + (long) groove.periodMs + 300;
        at(stop3p - 400, () -> { mc.player.setXRot(0); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); hud(mc, true); });
        walk(mc, self, stop3p, "stop3p");
        // The same in first person: the arms go down, the hand comes back up.
        long stopFp = stop3p + 2500;
        at(stopFp - 1500, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); hud(mc, false); Emotes.playLocal(self, EmoteLibrary.GROOVE); });
        walk(mc, self, stopFp, "stopfp");
        // A sword in hand moves with the arm.
        long sword = stopFp + 2500;
        at(sword, () -> { command("give @p minecraft:iron_sword"); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); hud(mc, true); });
        at(sword + 300, () -> Emotes.playLocal(self, EmoteLibrary.GROOVE));
        shot(sword + 300 + 1100, "3p-sword-a");
        shot(sword + 300 + 1100 + Math.round(groove.periodMs / 4), "3p-sword-b");
        // First person with the held items: each moves with its dancing hand, as in third person.
        at(sword + 2000, () -> { command("item replace entity @p weapon.offhand with minecraft:shield"); mc.options.setCameraType(CameraType.FIRST_PERSON); hud(mc, false); });
        for (int i = 0; i < 4; i++) shot(sword + 2400 + Math.round(i * groove.periodMs / 4), "fp-items-" + i);
        at(sword + 2400 + (long) groove.periodMs, () -> Emotes.stopLocal(self));
        // Another player (client-side) dancing next to us, then moving: it stops here without a network message.
        long other = sword + 2400 + (long) groove.periodMs + 600;
        at(other, () -> {
            command("clear @p");
            remote = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "LadsDancer"));
            remote.setId(-1911);
            double yaw = Math.toRadians(mc.player.getYRot());
            remote.snapTo(mc.player.getX() - Math.cos(yaw) * 1.6, mc.player.getY(), mc.player.getZ() - Math.sin(yaw) * 1.6, mc.player.getYRot(), 0);
            remote.setYHeadRot(mc.player.getYRot()); remote.setYBodyRot(mc.player.getYRot());
            mc.level.addEntity(remote);
            Emotes.onRemote(remote.getUUID(), EmoteLibrary.GROOVE, 700);
            Emotes.playLocal(self, EmoteLibrary.GROOVE);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); hud(mc, true);
        });
        shot(other + 1200, "remote-a");
        shot(other + 1200 + Math.round(groove.periodMs / 4), "remote-b");
        // Cosmetics "Show other players' emotes" off: the other player stands at once, we keep dancing.
        at(other + 2000, () -> cosmetics().showOthers.set(false));
        shot(other + 2300, "remote-hidden");
        at(other + 2400, () -> {
            check(EmoteAnimator.pose(remote.getUUID(), true, remote.getX(), remote.getY(), remote.getZ()) == null
                && EmoteAnimator.pose(self, false, 0, 0, 0) != null, "\"Show other players' emotes\" off: the other player is not posed, we still dance");
            cosmetics().showOthers.set(true);
        });
        at(other + 2800, () -> remote.snapTo(remote.getX() + 1, remote.getY(), remote.getZ(), remote.getYRot(), 0));
        at(other + 2900, () -> {
            Emotes.Playing p = Emotes.get(remote.getUUID());
            check(p == null || p.stopping(), "another player who moves blends its emote out here before any network stop");
        });
        shot(other + 3000, "remote-moved");
        at(other + 3600, () -> {
            check(Emotes.get(remote.getUUID()) == null, "its blend-out finished and it left Emotes");
            remote.discard();
            Emotes.stopLocal(self);
        });
        // Cosmetics host screen (a) and pause menu (b), Groove key simulate (c), close screens (d)
        long cos = other + 4200;
        at(cos, () -> {
            hud(mc, false);
            mc.setScreenAndShow(new CosmeticsScreen26(null));
        });
        shot(cos + 300, "cosmetics-screen");
        at(cos + 600, () -> mc.setScreenAndShow(new net.minecraft.client.gui.screens.PauseScreen(true)));
        shot(cos + 900, "pause-menu");
        at(cos + 1200, () -> mc.setScreenAndShow(null));
        at(cos + 1400, () -> {
            KeyMapping grooveKey = EmoteAnim26.key(EmoteLibrary.GROOVE);
            if (grooveKey != null) grooveKey.clickCount++;
        });
        at(cos + 1700, () -> {
            Emotes.Playing p = Emotes.get(self);
            check(p != null && EmoteLibrary.GROOVE.equals(p.id()) && !p.stopping(), "groove key starts groove");
            KeyMapping grooveKey = EmoteAnim26.key(EmoteLibrary.GROOVE);
            if (grooveKey != null) grooveKey.clickCount++;
        });
        at(cos + 2000, () -> {
            Emotes.Playing p = Emotes.get(self);
            check(p != null && p.stopping(), "groove key again stops groove");
            mc.setScreenAndShow(null);
        });
        at(cos + 2500, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            hud(mc, hudWas);
            com.thelads.core.v26_2.feature.LocalSkins.reset();
            done = true;
            com.thelads.core.client.qa.QaProgress.done();
            LOGGER.info("Lads emote capture END: {} passed, {} failed; frames in {}", passed, failed, out);
        });
    }

    /** Walk forward at {@code at}: the emote must blend out; every frame for 600 ms is photographed with its weight logged. */
    private static void walk(Minecraft mc, UUID self, long at, String name) {
        at(at - 1, () -> { stopWeights.clear(); stoppedSeen = false; });
        at(at, () -> { mc.options.keyUp.setDown(true); walkAt = System.currentTimeMillis(); burst = name; burstFrame = 0; });
        at(at + 600, () -> burst = null);
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
            check(smooth && biggest < 0.45f && Emotes.get(self) == null, name + ": the pose blended out over " + stopWeights.size()
                + " frames, no cut (largest step between frames " + biggest + "), and the emote left Emotes");
        });
    }

    private static com.thelads.core.modules.CosmeticsModule cosmetics() {
        return (com.thelads.core.modules.CosmeticsModule) com.thelads.core.config.ModuleManager.getInstance().getModule("Cosmetics");
    }

    /** The HUD hidden (F1) for clean third-person frames. */
    private static void hud(Minecraft mc, boolean hidden) { if (mc.gui.hud.isHidden() != hidden) mc.gui.hud.toggle(); }

    private static void command(String command) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void keyConflicts(Minecraft mc) {
        String key = EmoteAnim26.KEY.saveString();
        StringBuilder same = new StringBuilder();
        boolean clash = false;
        java.util.Set<String> used = new java.util.TreeSet<>();
        for (KeyMapping mapping : mc.options.keyMappings) {
            used.add(mapping.saveString());
            if (mapping != EmoteAnim26.KEY && mapping.saveString().equals(key)) same.append(mapping.getName()).append(' ');
            if (com.thelads.core.client.cosmetics.anim.EmoteKeys.GIVE_WAY.contains(mapping.getName()) && mapping.same(EmoteAnim26.KEY)) clash = true;
        }
        StringBuilder free = new StringBuilder();
        check(!clash, "no other mod's key shares B with Emotes any more (Xaero's New Waypoint gave way once)");
        for (char c = 'a'; c <= 'z'; c++) if (!used.contains("key.keyboard." + c)) free.append(c);
        LOGGER.info("Lads emote capture keys: Emotes = {}; also bound there: [{}]; free letters: {}", key, same.toString().trim(), free);
    }

    private static void check(boolean ok, String what) {
        com.thelads.core.client.qa.QaProgress.label(what);
        if (!ok) com.thelads.core.client.qa.QaProgress.result(com.thelads.core.client.qa.QaProgress.Result.FAIL);
        if (ok) passed++; else failed++;
        LOGGER.info("Lads emote capture {}: {}", ok ? "PASS" : "FAIL", what);
    }

    /** Each completed frame (NativeWorldVerification.renderedFrame): due steps, then a requested screenshot (or every frame of a burst). */
    public static void frame(RenderTarget target, Path game) {
        if (!busy()) return;
        long now = System.currentTimeMillis() - startedAt;
        while (!STEPS.isEmpty() && STEPS.firstKey() <= now) {
            long due = STEPS.firstKey();
            for (Runnable step : STEPS.pollFirstEntry().getValue()) {
                com.thelads.core.client.qa.QaProgress.step(String.format(Locale.ROOT, "t+%.1fs", due / 1000.0));
                step.run();
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Emotes.Playing playing = Emotes.get(mc.player.getUUID());
        if (walkAt > 0 && playing != null && playing.stopping()) stoppedSeen = true;
        String name;
        if (burst != null) {
            name = String.format(Locale.ROOT, "%s-%02d", burst, burstFrame++);
            EmotePose pose = EmoteAnimator.pose(mc.player.getUUID(), false, 0, 0, 0);
            float weight = pose == null ? 0 : pose.weight;
            stopWeights.add(weight);
            LOGGER.info("Lads emote capture frame {}: {} ms after the walk key, weight {}", name, System.currentTimeMillis() - walkAt, String.format(Locale.ROOT, "%.3f", weight));
        } else if ((name = shots.poll()) == null) return;
        Path file = out.resolve(name + ".png");
        net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (Exception failure) {
                LOGGER.error("Lads emote capture {} failed", name, failure);
            }
        });
    }
}
