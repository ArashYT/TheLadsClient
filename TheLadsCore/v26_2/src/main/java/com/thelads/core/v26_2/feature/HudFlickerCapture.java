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
 * flicker under the HUD FPS cap? With everything this QA world can put on the HUD (Lads HUD elements, Jade on a crafting table,
 * Xaero's minimap, title, subtitle, action bar, boss bar, scoreboard, chat with heads, subtitles, effects, Item Physics' throw bar,
 * a kill banner, voice chat sample, toasts, an Essential notification, Flashback recording), then again with F3, it saves runs of
 * consecutive frames to screenshots/hudflicker: the HUD hidden (reference), the cap off (control) and the cap on at 10 FPS, each
 * with the elements' rectangles and the HUD's cost per frame (hudflicker-*.json). artifacts/1.7.2/hudfps/flicker.py checks that every
 * element is in every frame. Everything is put back.
 */
final class HudFlickerCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String[] MODULES = {"FPS", "Coordinates", "Keystrokes", "CPS", "Paperdoll", "ArmorHUD", "Potion Effects", "Scoreboard",
        "BossBar", "Voice Chat", "Voice Chat Group", "Minimap", "Jade", "KillBanner", "Item Physics", "Chat Heads", "Autohide"};
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
    private static boolean capWas, subtitlesWas;
    private static int limitWas, measureFrames, builds0, replays0;
    private static long nanos0, measureStart;
    private static ItemStack handWas = ItemStack.EMPTY;
    private static BlockPos below;
    private static BlockState belowWas;
    private static Path gameDir;

    private HudFlickerCapture() {}

    static boolean busy() { return step >= 0 && step < 99; }

    static void tick(Path game, boolean ready) {
        if (busy()) { Minecraft.getInstance().options.keyDrop.setDown(true); return; } // keeps Item Physics' throw bar charging
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
            m.setEnabled(!module.equals("Autohide"));
        }
        if (NativeQualityOfLife.module("Item Physics") instanceof com.thelads.core.modules.ItemPhysicsModule physics) physics.charged.set(true);
        if (NativeQualityOfLife.module("KillBanner") instanceof com.thelads.core.modules.KillBannerModule banner) banner.duration.setValue(5);
        capWas = HudSettings.getInstance().isHudFpsCapEnabled();
        limitWas = HudSettings.getInstance().getHudFpsLimit();
        subtitlesWas = mc.options.showSubtitles().get();
        mc.options.showSubtitles().set(true);
        VoiceChatIntegration.qa = new VoiceChatState("voicechat:icons/microphone",
            List.of(new VoiceMember("Steve", "8667ba71-b85a-4004-af54-457a9734eed7", true, false),
                new VoiceMember("Alex", "ec561538-f3fd-461d-aff5-086b22154bce", false, true)));
        // Still, flying, looking straight down at a crafting table (Jade's tooltip); an empty hand, so the throw never fires.
        mc.player.getAbilities().flying = true;
        mc.player.setDeltaMovement(0, 0, 0);
        mc.player.setXRot(90);
        below = mc.player.blockPosition().below();
        handWas = mc.player.getMainHandItem().copy();
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            belowWas = player.level().getBlockState(below);
            player.level().setBlockAndUpdate(below, Blocks.CRAFTING_TABLE.defaultBlockState());
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        });
        for (String command : new String[] {"scoreboard objectives add ladsflicker dummy {\"text\":\"Flicker QA\",\"color\":\"gold\"}",
            "scoreboard objectives setdisplay sidebar ladsflicker", "scoreboard players set Alpha ladsflicker 2", "scoreboard players set Beta ladsflicker 1",
            "bossbar add lads:flicker \"QA Boss\"", "bossbar set lads:flicker players @a", "bossbar set lads:flicker value 60",
            "effect give @a minecraft:luck 600 0 true"}) command(command);
        startRecording();
        LOGGER.info("Lads HUD flicker capture BEGIN: {} frames per run, cap {} FPS, phases {}", FRAMES, CAP, String.join(",", PHASES));
        step = 0;
        due = System.nanoTime() + 2_000_000_000L;
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame): the measurement window, then a run of frames. */
    static void frame(RenderTarget target, Path game) {
        if (!busy()) return;
        long now = System.nanoTime();
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
            int phase = step / 3;
            if (phase >= PHASES.length) { finish(); return; }
            String p = PHASES[phase];
            switch (step++ % 3) {
                case 0 -> { // the HUD hidden: the reference every element is compared with
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
                    refresh(mc);
                    measure(p + "-off");
                }
                default -> { // the cap on
                    HudSettings.getInstance().setHudFpsCapEnabled(true);
                    HudSettings.getInstance().setHudFpsLimit(CAP);
                    refresh(mc);
                    measure(p + "-on");
                }
            }
        } catch (Throwable failure) {
            failed++;
            LOGGER.error("Lads HUD flicker capture FAILED: step {}", step, failure);
            finish();
        }
    }

    /** Elements that time out are shown again before each run: chat, subtitles, titles, action bar, toasts, kill banner, Essential. */
    private static void refresh(Minecraft mc) {
        for (int i = 1; i <= 3; i++) mc.player.connection.sendChat("Flicker QA chat " + i);
        mc.gui.hud.setTimes(0, 1200, 0);
        mc.gui.hud.setTitle(Component.literal("QA Title"));
        mc.gui.hud.setSubtitle(Component.literal("QA Subtitle"));
        mc.gui.hud.setOverlayMessage(Component.literal("QA Action Bar"), false);
        try {
            var field = mc.gui.hud.getClass().getDeclaredField("subtitleOverlay");
            field.setAccessible(true);
            var overlay = (net.minecraft.client.gui.components.SubtitleOverlay) field.get(mc.gui.hud);
            var sound = new SimpleSoundInstance(SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 1, 1, RandomSource.create(),
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
            overlay.onPlaySound(sound, mc.getSoundManager().getSoundEvent(SoundEvents.ITEM_PICKUP.location()), 16);
        } catch (ReflectiveOperationException | RuntimeException failure) { LOGGER.warn("Lads HUD flicker capture: no subtitle", failure); }
        SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST, Component.literal("QA toast"), Component.literal("HUD FPS cap flicker check"));
        NativeKillBanner.bindCurrent();
        NativeKillBanner.timeline().clear();
        NativeKillBanner.trigger(1, false);
        essentialNotification();
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
            if (!element.isEnabled() || !element.isAvailable()) continue;
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
        rect(rects, "Title", w / 2 - title * 2 - 4, h / 2 - 42, title * 4 + 8, 40, s);
        rect(rects, "Subtitle", w / 2 - subtitle - 4, h / 2 + 8, subtitle * 2 + 8, 22, s);
        rect(rects, "Action bar", w / 2 - action / 2 - 4, h - 76 - lift, action + 8, 16, s);
        rect(rects, "Hotbar", w / 2 - 91, h - 22 - lift, 182, 22, s);
        rect(rects, "Health, armour, food, XP", w / 2 - 91, h - 52 - lift, 182, 30, s);
        rect(rects, "Chat", 0, h - 48 - lift - 3 * 9, Math.min(w / 2, ChatComponent.getWidth(mc.options.chatWidth().get()) + 24), 3 * 9 + 10, s);
        rect(rects, "Subtitles", w - 150, h - 54, 150, 24, s);
        rect(rects, "Toasts and effects", w - 160, 0, 160, 32, s);
        if (name.startsWith("f3")) { rect(rects, "F3 left", 0, 0, w / 2, h / 2, s); rect(rects, "F3 right", w / 2, 0, w / 2, h / 2, s); }
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
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        ItemStack hand = handWas;
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            if (belowWas != null) player.level().setBlockAndUpdate(below, belowWas);
            player.setItemSlot(EquipmentSlot.MAINHAND, hand);
        });
        for (String command : new String[] {"bossbar remove lads:flicker", "scoreboard objectives remove ladsflicker", "effect clear @a minecraft:luck"}) command(command);
        // The last frames are still being written: END once they are on disk.
        Thread waiter = new Thread(() -> {
            long until = System.nanoTime() + 30_000_000_000L;
            while (WRITING.get() > 0 && System.nanoTime() < until) { try { Thread.sleep(50); } catch (InterruptedException e) { return; } }
            LOGGER.info("Lads HUD flicker capture END: {} passed, {} failed; frames in screenshots/hudflicker", saved, failed + WRITING.get());
        }, "Lads HUD flicker capture");
        waiter.setDaemon(true);
        waiter.start();
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
