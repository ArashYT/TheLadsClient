package com.thelads.core.v26_2.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.bridge.LadsGameBridge.VoiceChatState;
import com.thelads.core.client.bridge.LadsGameBridge.VoiceMember;
import com.thelads.core.client.hud.HudGroupLayout;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-hudflicker" from the harness's LADS_VERIFY_CAPTURE_HUDFLICKER): does any HUD drawing
 * flicker under the HUD FPS cap? In two scenes that do not overlap each other's elements, "hud" (Lads HUD elements, the paper doll,
 * Jade on a crafting table, Xaero's minimap, action bar, scoreboard, chat with heads, subtitles, effects, Item Physics' throw bar,
 * voice chat sample) and "f3" (F3, title, subtitle, boss bar, a kill banner, toasts, an Essential notification, Flashback's
 * recording toast, the Lads HUD, chat, scoreboard, minimap, throw bar), in peaceful with no vignette, it saves runs of
 * consecutive frames to screenshots/hudflicker: the HUD hidden (reference), the cap off (control), the cap on at 10 FPS and the HUD
 * hidden again (the view stayed still), with the elements' rectangles and the HUD's cost per frame (hudflicker.json).
 * artifacts/1.7.2/hudfps/flicker.py checks that every element is in every frame. Then the pace of HUD animations: Jade's fade-in and
 * fade-out, the spyglass zoom-in, the vignette darkening at nightfall and the Lads FPS counter's smoothing, each timed with the cap
 * off, at 30 and at 10 FPS (from the moment it starts until a HUD build shows its end), with how often the HUD was built meanwhile;
 * a second of a kill banner, of a subtitle fading, and of a HUD where only numbers change (the FPS counter and an effect timer), with
 * the HUD's builds per second, the builds in which something moved or faded, and the HUD's cost (hudflicker.json "pace"). Everything
 * is put back.
 */
final class HudFlickerCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String[] MODULES = {"FPS", "Coordinates", "Keystrokes", "CPS", "Paperdoll", "ArmorHUD", "Potion Effects", "Scoreboard",
        "BossBar", "Voice Chat", "Voice Chat Group", "Minimap", "Jade", "KillBanner", "Item Physics", "Chat Heads", "Raised", "Autohide"};
    private static final String[] PHASES = {"hud", "f3"};
    private static final int FRAMES = 36, HIDDEN = 4, CAP = 10;
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(20_000L);
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final Map<Module, Boolean> ENABLED = new LinkedHashMap<>();
    private static final AtomicInteger WRITING = new AtomicInteger();
    private static int step = -1, burst, taken, saved, failed;
    private static long due, measureFrom, measureTo;
    private static String name;
    private static JsonObject report;
    private static boolean capWas, subtitlesWas, vignetteWas;
    private static String difficultyWas;
    private static int limitWas, measureFrames, builds0, replays0;
    private static long nanos0, measureStart;
    private static ItemStack handWas = ItemStack.EMPTY, headWas = ItemStack.EMPTY, feetWas = ItemStack.EMPTY;
    private static BlockPos below;
    private static BlockState belowWas;
    /** The floor put under the table where there was air: taken away again. */
    private static final List<BlockPos> FLOOR = new java.util.ArrayList<>();
    private static double pinX, pinY, pinZ, homeX, homeY, homeZ, fovEffectWas;
    private static float pinYaw;
    private static String gameModeWas;
    private static Path gameDir;
    // Pace: how long HUD animations take with the cap off, at 30 and at 10 FPS, from the moment each starts until a frame that built
    // the HUD shows its end. With the cap they should take as long as without, give or take one HUD frame.
    private static final int[] PACE_CAPS = {0, 30, 10};
    private static final String[] PACE = {"counters", "jade-in", "jade-out", "spyglass", "vignette", "fps-smoothing", "kill-banner", "subtitle"};
    private static final int PACE_REPEATS = 2;
    private static int pace = -1, paceStage, paceBuilds, paceBuildsAtStart, paceChangesAtStart, paceFrames;
    private static long paceReady, paceStart, paceDeadline, dayTimeWas, paceNanosAtStart;
    private static float pinPitch = 90;
    private static boolean jadeAnimationWas;

    private HudFlickerCapture() {}

    static boolean busy() { return step >= 0 && step < 99; }

    static void tick(Path game, boolean ready) {
        if (busy()) { pin(Minecraft.getInstance()); return; }
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-hudflicker");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads HUD flicker capture FAILED: request", failure); return; }
        gameDir = game;
        Minecraft mc = Minecraft.getInstance();
        for (String module : MODULES) {
            Module m = NativeQualityOfLife.module(module);
            if (m == null) continue;
            ENABLED.put(m, m.isEnabled());
            for (Option option : m.getOptions()) OPTIONS.put(option, option.save().deepCopy());
            if (!module.equals("Autohide")) m.getOptions().forEach(Option::reset); // defaults: the paper doll at its HUD position
            m.setEnabled(!module.equals("Autohide"));
        }
        if (NativeQualityOfLife.module("Paperdoll") != null) ((BoolOption) NativeQualityOfLife.module("Paperdoll").getOption("Always Display")).set(true);
        if (NativeQualityOfLife.module("Item Physics") instanceof com.thelads.core.modules.ItemPhysicsModule physics) physics.charged.set(true);
        if (NativeQualityOfLife.module("KillBanner") instanceof com.thelads.core.modules.KillBannerModule banner) banner.duration.setValue(5);
        capWas = HudSettings.getInstance().isHudFpsCapEnabled();
        limitWas = HudSettings.getInstance().getHudFpsLimit();
        subtitlesWas = mc.options.showSubtitles().get();
        mc.options.showSubtitles().set(true);
        vignetteWas = mc.options.vignette().get(); // a HUD layer over the whole screen that fades at its own pace: off
        mc.options.vignette().set(false);
        difficultyWas = mc.level.getDifficulty().getSerializedName(); // no mobs moving below the camera
        VoiceChatIntegration.qa = new VoiceChatState("voicechat:icons/microphone",
            List.of(new VoiceMember("Steve", "8667ba71-b85a-4004-af54-457a9734eed7", true, false),
                new VoiceMember("Alex", "ec561538-f3fd-461d-aff5-086b22154bce", false, true)));
        // In survival (health, food, AppleSkin), standing still on a crafting table 30 blocks above the ground over a stone floor,
        // looking straight down at it (Jade's tooltip): a background that stays the same in every frame (no water, kelp, mobs or
        // particles, no FOV effects); an empty hand, so the throw never fires. Table and floor go and the player goes back afterwards.
        homeX = mc.player.getX();
        homeY = mc.player.getY();
        homeZ = mc.player.getZ();
        int x = net.minecraft.util.Mth.floor(homeX), z = net.minecraft.util.Mth.floor(homeZ);
        int y = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) + 30;
        pinX = x + 0.5;
        pinY = y;
        pinZ = z + 0.5;
        pinYaw = mc.player.getYRot();
        pin(mc);
        fovEffectWas = mc.options.fovEffectScale().get();
        mc.options.fovEffectScale().set(0.0);
        gameModeWas = mc.gameMode.getPlayerMode().getName();
        below = new BlockPos(x, y - 1, z);
        handWas = mc.player.getMainHandItem().copy();
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            belowWas = player.level().getBlockState(below);
            player.level().setBlockAndUpdate(below, Blocks.CRAFTING_TABLE.defaultBlockState());
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                BlockPos floor = below.offset(dx, -1, dz);
                if (!player.level().getBlockState(floor).isAir()) continue;
                player.level().setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
                FLOOR.add(floor);
            }
            player.teleportTo(pinX, pinY, pinZ);
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            headWas = player.getItemBySlot(EquipmentSlot.HEAD).copy(); // something for the Armor HUD to show
            feetWas = player.getItemBySlot(EquipmentSlot.FEET).copy();
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.IRON_BOOTS));
        });
        for (String command : new String[] {"scoreboard objectives add ladsflicker dummy {\"text\":\"Flicker QA\",\"color\":\"gold\"}",
            "scoreboard objectives setdisplay sidebar ladsflicker", "scoreboard players set Alpha ladsflicker 2", "scoreboard players set Beta ladsflicker 1",
            "effect give @a minecraft:luck 600 0 true", "gamemode survival @a", "difficulty peaceful"}) command(command);
        LOGGER.info("Lads HUD flicker capture BEGIN: {} frames per run, cap {} FPS, phases {}", FRAMES, CAP, String.join(",", PHASES));
        step = 0;
        due = System.nanoTime() + 2_000_000_000L;
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame): the measurement window, then a run of frames. */
    static void frame(RenderTarget target, Path game) {
        if (!busy()) return;
        long now = System.nanoTime();
        if (pace >= 0) { pace(now); return; }
        if (measureFrom > 0 && now >= measureFrom) {
            if (measureStart == 0) { measureStart = now; measureFrames = 0; builds0 = HudCapture.qaBuilds; replays0 = HudCapture.qaReplays; nanos0 = HudCapture.qaNanos; }
            else measureFrames++;
            if (now >= measureTo) { measured(now); measureFrom = 0; }
            return;
        }
        if (now < due) return;
        if (burst == 0) { next(); return; }
        if (taken == 0) rects();
        int index = taken++;
        String file = name + "-" + (index < 10 ? "0" : "") + index + ".png";
        if (taken >= burst) burst = 0;
        try {
            Path folder = game.resolve("screenshots").resolve("hudflicker");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve(file);
            WRITING.incrementAndGet();
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> Util.ioPool().execute(() -> {
                try (image) { image.writeToFile(output); saved++; }
                catch (Exception failure) { failed++; LOGGER.error("Lads HUD flicker capture FAILED: {}", file, failure); }
                finally { WRITING.decrementAndGet(); }
            }));
        } catch (Exception failure) {
            failed++;
            WRITING.decrementAndGet();
            LOGGER.error("Lads HUD flicker capture FAILED: {}", file, failure);
        }
    }

    private static void next() {
        Minecraft mc = Minecraft.getInstance();
        try {
            int phase = step / 4;
            if (phase >= PHASES.length) { startPace(mc); return; }
            String p = PHASES[phase];
            switch (step++ % 4) {
                case 0 -> { // the HUD hidden: the reference every element is compared with
                    if (p.equals("f3")) { // the second scene: what would cover the first one's elements
                        for (String command : new String[] {"effect clear @a minecraft:luck", "bossbar add lads:flicker \"QA Boss\"",
                            "bossbar set lads:flicker players @a", "bossbar set lads:flicker value 60"}) command(command);
                        NativeQualityOfLife.module("Jade").setEnabled(false);
                        mc.gui.hud.clearTitles();
                    }
                    mc.debugEntries.setOverlayVisible(false);
                    mc.gui.toastManager().clear();
                    if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                    HudSettings.getInstance().setHudFpsCapEnabled(false);
                    run(p + "-hidden", HIDDEN, 800);
                }
                case 1 -> { // the cap off: every element on every frame (the control)
                    if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                    mc.debugEntries.setOverlayVisible(p.equals("f3"));
                    HudSettings.getInstance().setHudFpsCapEnabled(false);
                    if (p.equals("f3")) startRecording(); // Flashback's recording toast, live through both runs
                    // Chat once per scene, on its own (lines fade 10 s after they came; new ones would move the old ones).
                    mc.gui.hud.getChat().clearMessages(false);
                    for (int i = 1; i <= 3; i++) mc.player.connection.sendChat("Flicker QA chat " + i);
                    refresh(mc, p);
                    measure(p + "-off");
                }
                case 2 -> { // the cap on
                    HudSettings.getInstance().setHudFpsCapEnabled(true);
                    HudSettings.getInstance().setHudFpsLimit(CAP);
                    refresh(mc, p);
                    measure(p + "-on");
                }
                default -> { // the HUD hidden again: the view did not move (flicker.py compares it with the first)
                    mc.debugEntries.setOverlayVisible(false);
                    mc.gui.toastManager().clear();
                    if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                    HudSettings.getInstance().setHudFpsCapEnabled(false);
                    run(p + "-after", HIDDEN, 800);
                }
            }
        } catch (Throwable failure) {
            failed++;
            LOGGER.error("Lads HUD flicker capture FAILED: step {}", step, failure);
            finish();
        }
    }

    /** Elements that time out are shown again before each run: the scene's subtitles and action bar, or titles, toast, kill banner, Essential. */
    private static void refresh(Minecraft mc, String scene) {
        if (scene.equals("f3")) {
            mc.gui.hud.setTimes(0, 1200, 0);
            mc.gui.hud.setTitle(Component.literal("QA Title"));
            mc.gui.hud.setSubtitle(Component.literal("QA Subtitle"));
            SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST, Component.literal("QA toast"), Component.literal("HUD FPS cap flicker check"));
            NativeKillBanner.bindCurrent();
            NativeKillBanner.timeline().clear();
            NativeKillBanner.trigger(1, false);
            essentialNotification();
            return;
        }
        mc.gui.hud.setOverlayMessage(Component.literal("QA Action Bar"), false);
        subtitle(mc, true);
    }

    /** A new "Item plops" subtitle (it fades to grey over 3 s), or none left on screen. */
    private static void subtitle(Minecraft mc, boolean show) {
        try {
            var field = mc.gui.hud.getClass().getDeclaredField("subtitleOverlay");
            field.setAccessible(true);
            var overlay = (net.minecraft.client.gui.components.SubtitleOverlay) field.get(mc.gui.hud);
            if (!show) {
                var list = overlay.getClass().getDeclaredField("subtitles");
                list.setAccessible(true);
                ((java.util.List<?>) list.get(overlay)).clear();
                return;
            }
            var sound = new SimpleSoundInstance(SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 1, 1, RandomSource.create(),
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
            overlay.onPlaySound(sound, mc.getSoundManager().getSoundEvent(SoundEvents.ITEM_PICKUP.location()), 16);
        } catch (ReflectiveOperationException | RuntimeException failure) { LOGGER.warn("Lads HUD flicker capture: no subtitle", failure); }
    }

    private static void run(String run, int frames, long settleMs) {
        name = run;
        burst = frames;
        taken = 0;
        due = System.nanoTime() + settleMs * 1_000_000L;
    }

    /** A second of frames for the HUD's cost, after half a second to settle, then the run. */
    private static void measure(String run) {
        long now = System.nanoTime();
        measureStart = 0;
        measureFrom = now + 500_000_000L;
        measureTo = measureFrom + 1_000_000_000L;
        run(run, FRAMES, 1600);
    }

    private static void measured(long now) {
        double seconds = (now - measureStart) / 1e9;
        int builds = HudCapture.qaBuilds - builds0, replays = HudCapture.qaReplays - replays0;
        double hudMicros = measureFrames == 0 ? 0 : (HudCapture.qaNanos - nanos0) / 1e3 / Math.max(1, builds + replays);
        String line = String.format(java.util.Locale.ROOT, "%.1f FPS, frame %.2f ms, HUD %.1f us per frame (%d builds, %d replays)",
            measureFrames / seconds, seconds * 1000 / Math.max(1, measureFrames), hudMicros, builds, replays);
        LOGGER.info("Lads HUD flicker capture {} cost: {}", name, line);
        cost().addProperty(name, line);
    }

    private static JsonObject cost() {
        if (report == null) report = new JsonObject();
        if (!report.has("cost")) report.add("cost", new JsonObject());
        return report.getAsJsonObject("cost");
    }

    /** The first frame of a run: where each element is, in pixels, for flicker.py (named rows; anything else is found by itself). */
    private static void rects() {
        if (!name.endsWith("-off")) return;
        Minecraft mc = Minecraft.getInstance();
        int s = mc.getWindow().getGuiScale(), w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        JsonObject rects = new JsonObject();
        var adapter = new GuiGraphicsExtractorLadsAdapter(new GuiGraphicsExtractor(mc, new GuiRenderState(), 0, 0), mc.font);
        int lift = adapter.hotbarLift();
        for (var element : HudManager.getInstance().getElements()) {
            if (!element.isEnabled() || !element.isAvailable() || !name.startsWith("f3") && element.getModuleName().equals("BossBar")) continue;
            var bounds = element.measureBounds(adapter, false);
            var placed = HudGroupLayout.translate(bounds, HudGroupLayout.clampDelta(bounds, 0, 0, w, h));
            rect(rects, "Lads " + element.getModuleName(), placed.x(), placed.y(), placed.width(), placed.height(), s);
        }
        if (snownee.jade.overlay.OverlayRenderer.shown) {
            var r = snownee.jade.overlay.OverlayRenderer.animation.rect;
            rect(rects, "Jade", (int) r.getX(), (int) r.getY(), (int) Math.ceil(r.getWidth()), (int) Math.ceil(r.getHeight()), s);
        }
        mc.gameRenderer.gameRenderState().guiRenderState.forEachPictureInPicture(picture -> {
            var b = picture.bounds();
            if (b != null) rect(rects, "Picture " + picture.getClass().getSimpleName(), b.left(), b.top(), b.width(), b.height(), s);
        });
        int title = mc.font.width("QA Title"), subtitle = mc.font.width("QA Subtitle"), action = mc.font.width("QA Action Bar");
        rect(rects, "Crosshair", w / 2 - 8, h / 2 - 8, 16, 16, s);
        rect(rects, "Item Physics throw bar", w / 2 - 8, h / 2 + 9, 16, 2, s);
        boolean f3 = name.startsWith("f3");
        if (f3) {
            rect(rects, "Title", w / 2 - title * 2 - 4, h / 2 - 42, title * 4 + 8, 40, s);
            rect(rects, "Subtitle", w / 2 - subtitle - 4, h / 2 + 8, subtitle * 2 + 8, 22, s);
            rect(rects, "Toasts", w - 160, 0, 160, 64, s);
        } else {
            rect(rects, "Action bar", w / 2 - action / 2 - 4, h - 76 - lift, action + 8, 16, s);
            rect(rects, "Subtitles", w - 150, h - 54, 150, 24, s);
            rect(rects, "Effects", w - 80, 0, 80, 28, s);
        }
        rect(rects, "Hotbar", w / 2 - 91, h - 22 - lift, 182, 22, s);
        rect(rects, "Health, armour, food, XP", w / 2 - 91, h - 52 - lift, 182, 30, s);
        rect(rects, "Chat", 0, h - 48 - lift - 3 * 9, Math.min(w / 2, ChatComponent.getWidth(mc.options.chatWidth().get()) + 24), 3 * 9 + 10, s);
        if (f3) { rect(rects, "F3 left", 0, 0, w / 2, h / 2, s); rect(rects, "F3 right", w / 2, 0, w / 2, h / 2, s); }
        JsonObject phase = new JsonObject();
        phase.addProperty("guiScale", s);
        phase.addProperty("width", mc.getWindow().getWidth());
        phase.addProperty("height", mc.getWindow().getHeight());
        phase.add("rects", rects);
        if (report == null) report = new JsonObject();
        report.add(name.substring(0, name.indexOf('-')), phase);
        write();
    }

    private static void rect(JsonObject rects, String name, int x, int y, int w, int h, int scale) {
        if (w <= 0 || h <= 0) return;
        JsonArray a = new JsonArray();
        for (int v : new int[] {x * scale, y * scale, w * scale, h * scale}) a.add(v);
        String key = name;
        for (int i = 2; rects.has(key); i++) key = name + " " + i;
        rects.add(key, a);
    }

    private static void write() {
        try { Files.writeString(gameDir.resolve("screenshots").resolve("hudflicker").resolve("hudflicker.json"), report.toString()); }
        catch (Exception failure) { failed++; LOGGER.error("Lads HUD flicker capture FAILED: report", failure); }
    }

    private static void finish() {
        step = 99;
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyDrop.setDown(false);
        if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        mc.debugEntries.setOverlayVisible(false);
        mc.gui.hud.clearTitles();
        mc.gui.hud.resetTitleTimes();
        cancelRecording();
        if (report != null) write();
        VoiceChatIntegration.qa = null;
        NativeKillBanner.timeline().clear();
        OPTIONS.forEach(Option::load);
        ENABLED.forEach(Module::setEnabled);
        HudSettings.getInstance().setHudFpsCapEnabled(capWas);
        HudSettings.getInstance().setHudFpsLimit(limitWas);
        mc.options.showSubtitles().set(subtitlesWas);
        mc.options.vignette().set(vignetteWas);
        mc.options.fovEffectScale().set(fovEffectWas);
        mc.options.keyUse.setDown(false);
        if (pace >= 0) {
            snownee.jade.api.config.IWailaConfig.get().overlay().setAnimation(jadeAnimationWas);
            command("time set " + dayTimeWas);
        }
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        ItemStack hand = handWas;
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            if (belowWas != null) player.level().setBlockAndUpdate(below, belowWas);
            for (BlockPos floor : FLOOR) player.level().setBlockAndUpdate(floor, Blocks.AIR.defaultBlockState());
            player.teleportTo(homeX, homeY, homeZ);
            player.setItemSlot(EquipmentSlot.MAINHAND, hand);
            player.setItemSlot(EquipmentSlot.HEAD, headWas);
            player.setItemSlot(EquipmentSlot.FEET, feetWas);
        });
        for (String command : new String[] {"bossbar remove lads:flicker", "scoreboard objectives remove ladsflicker", "effect clear @a minecraft:luck",
            "gamemode " + gameModeWas + " @a", "difficulty " + difficultyWas}) command(command);
        // The last frames are still being written: END once they are on disk.
        Thread waiter = new Thread(() -> {
            long until = System.nanoTime() + 30_000_000_000L;
            while (WRITING.get() > 0 && System.nanoTime() < until) { try { Thread.sleep(50); } catch (InterruptedException e) { return; } }
            LOGGER.info("Lads HUD flicker capture END: {} passed, {} failed; frames in screenshots/hudflicker", saved, failed + WRITING.get());
        }, "Lads HUD flicker capture");
        waiter.setDaemon(true);
        waiter.start();
    }

    private static void startPace(Minecraft mc) {
        if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        mc.debugEntries.setOverlayVisible(false);
        mc.gui.hud.clearTitles();
        NativeQualityOfLife.module("Jade").setEnabled(true);
        var overlay = snownee.jade.api.config.IWailaConfig.get().overlay();
        jadeAnimationWas = overlay.getAnimation();
        overlay.setAnimation(true); // Jade's default
        mc.options.vignette().set(true); // the game's default
        dayTimeWas = Math.floorMod(mc.level.getDefaultClockTime(), 24000L);
        command("effect give @a minecraft:luck 600 0 true"); // an effect timer on screen in every trial (cleared again at the end)
        report().add("pace", new JsonObject());
        pace = 0;
        paceStage = 0;
        LOGGER.info("Lads HUD pace capture BEGIN: {} with the cap off, at 30 and at 10 FPS, {} times each", String.join(", ", PACE), PACE_REPEATS);
    }

    private static JsonObject report() {
        if (report == null) report = new JsonObject();
        return report;
    }

    /** Each frame of the pace runs: set the trial up, wait until it is ready, start it, then time it to its end. */
    private static void pace(long now) {
        Minecraft mc = Minecraft.getInstance();
        int perCap = PACE.length * PACE_REPEATS;
        if (pace >= PACE_CAPS.length * perCap) { finish(); return; }
        int cap = PACE_CAPS[pace / perCap];
        String anim = PACE[pace % PACE.length];
        boolean built = HudCapture.qaBuilds != paceBuilds;
        paceBuilds = HudCapture.qaBuilds;
        var jade = snownee.jade.overlay.OverlayRenderer.animation;
        boolean jadeTarget = snownee.jade.JadeClient.tickHandler().rootElement != null;
        try {
            switch (paceStage) {
                case 0 -> { // set up: the animation at rest at its start
                    HudSettings.getInstance().setHudFpsCapEnabled(cap > 0);
                    if (cap > 0) HudSettings.getInstance().setHudFpsLimit(cap);
                    mc.options.keyUse.setDown(false);
                    pinPitch = anim.equals("jade-out") ? 90 : -90; // up at the sky, or down at the crafting table
                    hand(anim.equals("spyglass") ? new ItemStack(net.minecraft.world.item.Items.SPYGLASS) : ItemStack.EMPTY);
                    boolean still = anim.equals("counters") || anim.equals("kill-banner") || anim.equals("subtitle");
                    if (anim.equals("vignette") || anim.equals("counters")) command("time set day");
                    if (anim.equals("counters")) mc.gui.hud.vignetteBrightness = 0; // settled at daylight (it eases 1% a tick, a slow animation)
                    if (still) pinPitch = 90; // Jade on the table, still
                    NativeKillBanner.timeline().clear(); // the last trial's banner (5 s) would still be animating
                    mc.gui.hud.getChat().clearMessages(false); // and its commands' chat lines would fade out (an animation) 10 s later
                    subtitle(mc, false); // and a subtitle (the spyglass's, the last trial's) fade for 3 s
                    paceReady = now + 1_500_000_000L;
                    paceDeadline = now + 15_000_000_000L;
                    paceStage = 1;
                }
                case 1 -> { // ready: settled at the start value (or give up after 15 s)
                    boolean ready = switch (anim) {
                        case "jade-in" -> !jadeTarget && jade.showHideAlpha == 0;
                        case "jade-out" -> jadeTarget && jade.showHideAlpha >= 1;
                        case "spyglass" -> !mc.player.isScoping() && scope(mc) == 0.5f;
                        case "kill-banner", "counters", "subtitle" -> jadeTarget && jade.showHideAlpha >= 1;
                        default -> true;
                    };
                    if (now < paceReady || !ready && now < paceDeadline) return;
                    switch (anim) { // start
                        case "jade-in" -> pinPitch = 90;
                        case "jade-out" -> pinPitch = -90;
                        case "spyglass" -> mc.options.keyUse.setDown(true);
                        case "vignette" -> { mc.gui.hud.vignetteBrightness = 0; command("time set midnight"); }
                        case "kill-banner" -> { NativeKillBanner.bindCurrent(); NativeKillBanner.timeline().clear(); NativeKillBanner.trigger(1, false); }
                        case "counters" -> {}
                        case "subtitle" -> subtitle(mc, true);
                        default -> fps(0);
                    }
                    paceStart = switch (anim) { case "fps-smoothing", "kill-banner", "counters", "subtitle" -> now; default -> 0; };
                    paceBuildsAtStart = HudCapture.qaBuilds;
                    paceChangesAtStart = changes();
                    paceNanosAtStart = HudCapture.qaNanos;
                    paceFrames = 0;
                    paceDeadline = now + 12_000_000_000L;
                    paceStage = 2;
                }
                default -> { // timing: from the frame it started to the first HUD build that shows its end
                    if (paceStart == 0 && switch (anim) {
                        case "jade-in" -> jadeTarget;
                        case "jade-out" -> !jadeTarget;
                        case "spyglass" -> mc.player.isScoping();
                        default -> mc.gui.hud.vignetteBrightness > 0.005f;
                    }) { paceStart = now; paceBuildsAtStart = HudCapture.qaBuilds; paceChangesAtStart = changes(); paceNanosAtStart = HudCapture.qaNanos; paceFrames = 0; }
                    if (paceStart > 0) paceFrames++;
                    boolean end = paceStart > 0 && switch (anim) {
                        case "jade-in" -> built && jade.showHideAlpha >= 1;
                        case "jade-out" -> built && jade.showHideAlpha < 0.1f;
                        case "spyglass" -> built && scope(mc) >= 1.125f - 0.00625f; // 99% of the way from 0.5
                        case "vignette" -> built && mc.gui.hud.vignetteBrightness >= 0.4f;
                        case "kill-banner", "counters", "subtitle" -> now - paceStart >= 1_000_000_000L; // a second of it
                        default -> built && fpsDone();
                    };
                    if (!end && now < paceDeadline) return;
                    String key = (cap == 0 ? "cap off" : "cap " + cap) + " " + anim;
                    double seconds = (now - paceStart) / 1e9;
                    int builds = HudCapture.qaBuilds - paceBuildsAtStart, changes = changes() < 0 ? -1 : changes() - paceChangesAtStart;
                    String result = end ? String.format(java.util.Locale.ROOT, "%.0f ms, %d HUD builds (%.0f a second, %s moved or faded something), %d frames, HUD %.0f us per frame",
                        seconds * 1000, builds, builds / Math.max(seconds, 1e-3), changes < 0 ? "?" : String.valueOf(changes), paceFrames,
                        (HudCapture.qaNanos - paceNanosAtStart) / 1e3 / Math.max(1, paceFrames))
                        : "did not end within 12 s" + (paceStart == 0 ? " (did not start)" : "");
                    JsonObject results = report().getAsJsonObject("pace");
                    if (!results.has(key)) results.add(key, new JsonArray());
                    results.getAsJsonArray(key).add(result);
                    LOGGER.info("Lads HUD pace capture {}: {}", key, result);
                    if (!end) failed++;
                    mc.options.keyUse.setDown(false);
                    NativeKillBanner.timeline().clear();
                    pace++;
                    paceStage = 0;
                }
            }
        } catch (Exception failure) {
            failed++;
            LOGGER.error("Lads HUD flicker capture FAILED: pace {}", anim, failure);
            pace++;
            paceStage = 0;
        }
    }

    /** HudFrameCap.changes where this build has it (builds in which something moved, resized, faded or changed colour), or -1. */
    private static int changes() {
        try { return com.thelads.core.client.hud.HudFrameCap.class.getField("changes").getInt(null); }
        catch (ReflectiveOperationException absent) { return -1; }
    }

    private static void hand(ItemStack item) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        var id = Minecraft.getInstance().player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null && !ItemStack.matches(player.getMainHandItem(), item)) player.setItemSlot(EquipmentSlot.MAINHAND, item.copy());
        });
    }

    /** Vanilla's spyglass zoom (Hud.scopeScale): from 0.5 toward 1.125 while scoping. */
    private static float scope(Minecraft mc) throws ReflectiveOperationException {
        var field = net.minecraft.client.gui.Hud.class.getDeclaredField("scopeScale");
        field.setAccessible(true);
        return field.getFloat(mc.gui.hud);
    }

    /** The Lads FPS counter's smoothed value (FPSHudElement.displayed), set, or whether it is within 10% of its target. */
    private static void fps(double value) throws ReflectiveOperationException { fpsField("displayed").setDouble(fpsElement(), value); }

    private static boolean fpsDone() throws ReflectiveOperationException {
        double target = fpsField("target").getDouble(fpsElement()), displayed = fpsField("displayed").getDouble(fpsElement());
        return target > 0 && displayed >= 0.9 * target;
    }

    private static Object fpsElement() {
        for (var element : HudManager.getInstance().getElements()) if (element instanceof com.thelads.core.client.hud.FPSHudElement) return element;
        throw new IllegalStateException("no FPS element");
    }

    private static java.lang.reflect.Field fpsField(String name) throws ReflectiveOperationException {
        var field = com.thelads.core.client.hud.FPSHudElement.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** Every tick of the capture: the player stays where and as it was put, and Item Physics' throw bar keeps charging. */
    private static void pin(Minecraft mc) {
        mc.player.setPos(pinX, pinY, pinZ);
        mc.player.setDeltaMovement(0, 0, 0);
        mc.player.setYRot(pinYaw);
        mc.player.setXRot(pinPitch);
        mc.player.setOldPosAndRot();
        mc.options.keyDrop.setDown(true);
    }

    private static void startRecording() {
        try { com.moulberry.flashback.Flashback.startRecordingReplay(); }
        catch (Throwable absent) { LOGGER.info("Lads HUD flicker capture: Flashback recording not started ({})", absent.toString()); }
    }

    private static void cancelRecording() {
        try { if (com.moulberry.flashback.Flashback.RECORDER != null) com.moulberry.flashback.Flashback.cancelRecordingReplay(); }
        catch (Throwable absent) { LOGGER.info("Lads HUD flicker capture: Flashback recording not cancelled ({})", absent.toString()); }
    }

    /** Essential's notification, where Essential is loaded (reflection: Essential is not a compile dependency). */
    private static void essentialNotification() {
        try {
            Object notifications = Class.forName("gg.essential.api.EssentialAPI").getMethod("getNotifications").invoke(null);
            notifications.getClass().getMethod("push", String.class, String.class).invoke(notifications, "QA notification", "HUD FPS cap flicker check");
        } catch (Throwable absent) { LOGGER.info("Lads HUD flicker capture: no Essential notification ({})", absent.toString()); }
    }

    private static void command(String command) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }
}
