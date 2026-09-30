package com.thelads.core.v1_21_1.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.shared.QaWorldGuard;
import com.thelads.core.v1_21_1.gui.LadsSettingsScreen121;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in game-API world verification (-Dthelads.verifyAutoWorld), the 26.x runtime on 1.21.1. Runs only in
 * artifacts/verification/1.21.1-title, on its own save, which it creates (superflat, peaceful) on first use: 1.21.x never
 * opens the 26.x QA worlds, a real world, or a save written by a newer game. Same markers and request files as 26.x.
 */
public final class NativeWorldVerification {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String VERSION = "1.21.1", SAVE = QaWorldGuard.saveName(VERSION);
    private static boolean initialized, verified, failed, opened, readyLogged, closing, originalPause, syntheticInput;
    private static long titleSince, openedAt, nextStopCheck, captureAfter, frames;
    private static Path gameDirectory;
    private static boolean captureStarted;
    private static Screen menuScreen, previousScreen;
    private static String captureKind = "menu";
    private static NativeHudEditorProbe hudProbe;
    private static long menuOpenedAt, menuFirstFrame;
    private static int menuFrames;
    private static boolean menuCaptureStarted;
    private static volatile boolean menuCaptureFinished;
    private static Throwable menuCaptureFailure;
    private static Path menuOutput;
    private NativeWorldVerification() {}

    /** Client init: nothing is registered unless the QA flag is set. */
    public static void register() {
        if (!Boolean.getBoolean("thelads.verifyAutoWorld")) return;
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> initialize());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> close());
    }

    private static void initialize() {
        if (initialized) return;
        initialized = true;
        try {
            gameDirectory = QaWorldGuard.gameDirectory(FabricLoader.getInstance().getGameDir(), VERSION);
            verified = true;
            originalPause = Minecraft.getInstance().options.pauseOnLostFocus;
            Minecraft.getInstance().options.pauseOnLostFocus = false;
            ClientTickEvents.START_CLIENT_TICK.register(mc -> tick());
            LOGGER.info("Lads auto-world QA BEGIN: isolated save '{}'; game APIs only; no input/focus verification", SAVE);
        } catch (Exception failure) { fail("directory validation", failure); }
    }

    private static void tick() {
        if (!verified || closing) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        // Stop remains available after a world/probe error and before any title screen appears.
        if (now >= nextStopCheck) {
            nextStopCheck = now + 250_000_000L;
            if (Files.isRegularFile(gameDirectory.resolve(".lads-qa-stop"), LinkOption.NOFOLLOW_LINKS)) {
                LOGGER.info("Lads auto-world QA STOP: isolated graceful stop request received");
                close();
                mc.stop();
                return;
            }
        }
        // Screenshot completion is handled on a client tick, never inside the renderer/readback callback.
        if (menuScreen != null) {
            try { updateMenuCapture(mc, now); }
            catch (Exception failure) { finishMenuCapture(mc, failure); }
        }
        if (failed) return;
        mc.options.pauseOnLostFocus = false;
        try {
            if (!opened) {
                if (mc.level != null) throw new IllegalStateException("A world was loaded before the isolated QA request");
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null || !mc.isGameLoadFinished()) { titleSince = 0; return; }
                if (titleSince == 0) titleSince = now;
                if (now - titleSince >= 2_000_000_000L) NativeMenuAccessProbe.title();
                if (now - titleSince < 5_000_000_000L) return;
                openOrCreate(mc, now);
            } else if (mc.level != null && mc.player != null) {
                // Remove only Minecraft's ordinary pause screen. Never accept confirmation, error or upgrade dialogs.
                if (mc.screen != null && mc.screen.getClass() == PauseScreen.class && menuScreen == null) mc.setScreen(null);
                if (worldReady() && !readyLogged) {
                    readyLogged = true;
                    captureAfter = now + 15_000_000_000L;
                    LOGGER.info("Lads auto-world QA READY: existing local world loaded, alive, unpaused and screen-free; focus not asserted");
                }
            }
            if (opened && !readyLogged && now - openedAt > 90_000_000_000L)
                throw new IllegalStateException("QA world did not become ready within 90 seconds; screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
            Path menuRequest = gameDirectory.resolve(".lads-qa-capture-menu");
            Path hudRequest = gameDirectory.resolve(".lads-qa-capture-hud");
            if (menuScreen == null && readyLogged && worldReady()
                && (Files.isRegularFile(menuRequest, LinkOption.NOFOLLOW_LINKS) || Files.isRegularFile(hudRequest, LinkOption.NOFOLLOW_LINKS))) {
                boolean hud = Files.isRegularFile(hudRequest, LinkOption.NOFOLLOW_LINKS);
                Files.delete(hud ? hudRequest : menuRequest);
                previousScreen = mc.screen;
                captureKind = hud ? "HUD" : "menu";
                if (hud) {
                    hudProbe = new NativeHudEditorProbe();
                    menuScreen = hudProbe.open();
                } else menuScreen = new LadsSettingsScreen121(null);
                menuOpenedAt = now; menuFirstFrame = 0; menuFrames = 0;
                menuCaptureStarted = false; menuCaptureFinished = false; menuCaptureFailure = null; menuOutput = null;
                mc.setScreen(menuScreen);
                LOGGER.info("Lads {} capture BEGIN: explicit isolated request; real screen and completed framebuffer", captureKind);
            }
        } catch (Exception failure) { fail("world startup", failure); }
    }

    /** Opens this version's QA save, or creates it in the checked saves folder on the first run. */
    private static void openOrCreate(Minecraft mc, long now) throws IOException {
        Path saves = QaWorldGuard.saves(gameDirectory, gameDirectory.resolve("saves"));
        if (!mc.getLevelSource().getBaseDir().toRealPath().equals(saves)) throw new IOException("Minecraft's world folder is not the checked QA saves folder");
        opened = true; openedAt = now;
        if (!Files.exists(saves.resolve(SAVE), LinkOption.NOFOLLOW_LINKS)) {
            LOGGER.info("Lads auto-world QA CREATE: {} (new isolated superflat save for this version)", SAVE);
            var settings = new LevelSettings(SAVE, GameType.SURVIVAL, false, Difficulty.PEACEFUL, false,
                new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(SAVE, settings, new WorldOptions(135L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            return;
        }
        Path world = QaWorldGuard.existingSave(saves, SAVE);
        QaWorldGuard.requireOpenable(NbtIo.readCompressed(world.resolve("level.dat"), NbtAccounter.unlimitedHeap())
            .getCompound("Data").getInt("DataVersion"), SharedConstants.WORLD_VERSION);
        LOGGER.info("Lads auto-world QA OPEN: {}", SAVE);
        mc.createWorldOpenFlows().openWorld(SAVE, () -> fail("world open cancelled or returned to menu", null));
    }

    public static boolean active() { return verified && !closing; }
    /** Lads input rules act only while the window has focus; synthetic QA events stand in for it, inside the verified sandbox only. */
    public static boolean windowActive() { return Minecraft.getInstance().isWindowActive() || syntheticInput && active(); }
    static void syntheticInput(boolean on) { syntheticInput = on; }
    /** Completed frames so far; probes wait for new ones because Dynamic FPS may render an unfocused window at 1 FPS. */
    static long frames() { return frames; }

    /** Capture a completed game frame, including GUI, through Minecraft's own readback (synchronous on 1.21.1). */
    public static void renderedFrame(RenderTarget target) {
        if (!verified) return;
        frames++;
        renderMenuCapture(target);
        if (!worldReady() || !readyLogged || captureStarted || System.nanoTime() < captureAfter) return;
        captureStarted = true;
        try {
            Path output = screenshot("native-world-");
            try (NativeImage image = Screenshot.takeScreenshot(target)) { image.writeToFile(output); }
            LOGGER.info("Lads world capture END: 1 passed, 0 failed; actual completed game frame at {}", output);
        } catch (Exception failure) { fail("world screenshot", failure); }
    }
    private static Path screenshot(String prefix) throws IOException {
        Path folder = gameDirectory.resolve("screenshots");
        Files.createDirectories(folder);
        if (!folder.toRealPath().startsWith(gameDirectory)) throw new IOException("QA screenshot folder is outside the isolated game directory");
        return folder.resolve(prefix + System.currentTimeMillis() + ".png");
    }
    private static void updateMenuCapture(Minecraft mc, long now) {
        if (menuCaptureFinished) { finishMenuCapture(mc, menuCaptureFailure); return; }
        if (hudProbe != null) hudProbe.tick();
        if (mc.level == null || mc.player == null || mc.player.isDeadOrDying() || mc.screen != menuScreen) {
            finishMenuCapture(mc, new IllegalStateException("The requested QA menu was replaced before capture"));
        } else if (now - menuOpenedAt > 30_000_000_000L) {
            finishMenuCapture(mc, new IllegalStateException("The requested QA menu did not finish rendering/readback within 30 seconds"));
        }
    }
    private static void renderMenuCapture(RenderTarget target) {
        Minecraft mc = Minecraft.getInstance();
        // worldReady intentionally requires screen == null; this path instead requires our exact real menu.
        if (!active() || failed || menuScreen == null || menuCaptureStarted || mc.screen != menuScreen
            || mc.getOverlay() != null || mc.level == null || mc.player == null || mc.player.isDeadOrDying()) return;
        long now = System.nanoTime();
        if (menuFirstFrame == 0) menuFirstFrame = now;
        menuFrames++;
        if (hudProbe != null && !hudProbe.readyForCapture()) return;
        if (menuFrames < 2 || now - menuFirstFrame < 1_500_000_000L) return;
        menuCaptureStarted = true;
        try {
            menuOutput = screenshot("native-" + captureKind.toLowerCase(java.util.Locale.ROOT) + "-");
            try (NativeImage image = Screenshot.takeScreenshot(target)) { image.writeToFile(menuOutput); }
        } catch (Exception failure) { menuCaptureFailure = failure; }
        menuCaptureFinished = true;
    }
    private static void finishMenuCapture(Minecraft mc, Throwable failure) {
        if (menuScreen == null) return;
        if (failure == null && "mods".equals(captureKind)
            && !(menuScreen instanceof LadsSettingsScreen121 mods && mods.ui().isModsViewOpen()))
            failure = new IllegalStateException("The Installed mods view was not open when its frame was captured");
        if (failure == null && "menu".equals(captureKind)) {
            // The 26.x chain's first and last frames that exist here: the Lads menu, then its Installed mods view.
            LOGGER.info("Lads menu capture END: 1 passed, 0 failed; {} completed frames; actual framebuffer at {}", menuFrames, menuOutput);
            var view = new LadsSettingsScreen121(null);
            view.ui().openMods();
            menuScreen = view; captureKind = "mods";
            menuOpenedAt = System.nanoTime(); menuFirstFrame = 0; menuFrames = 0;
            menuCaptureStarted = false; menuCaptureFinished = false; menuCaptureFailure = null; menuOutput = null;
            mc.setScreen(menuScreen);
            return;
        }
        if (mc.screen == menuScreen) mc.setScreen(previousScreen);
        menuScreen = null; previousScreen = null;
        if (hudProbe != null) {
            try { hudProbe.close(); }
            catch (Exception cleanupFailure) { if (failure == null) failure = cleanupFailure; else failure.addSuppressed(cleanupFailure); }
            finally { hudProbe = null; }
        }
        if (failure == null) {
            LOGGER.info("Lads {} capture END: 1 passed, 0 failed; {} completed frames; actual framebuffer at {}",
                "mods".equals(captureKind) ? "mods view" : captureKind, menuFrames, menuOutput);
        } else {
            LOGGER.error("Lads " + captureKind + " capture FAILED", failure);
            fail("menu screenshot", failure);
        }
    }
    public static boolean worldReady() {
        Minecraft mc = Minecraft.getInstance();
        return active() && opened && !failed && mc != null && mc.level != null && mc.player != null
            && !mc.player.isDeadOrDying() && mc.screen == null && mc.getOverlay() == null && !mc.isPaused();
    }
    /** Used around Options.save so the QA-only pause override cannot reach options.txt. */
    public static boolean originalPauseOnLostFocus() { return originalPause; }
    public static void close() {
        if (!verified || closing) return;
        closing = true;
        syntheticInput = false;
        NativeMenuAccessProbe.restore();
        Minecraft mc = Minecraft.getInstance();
        if (menuScreen != null && mc.screen == menuScreen) mc.setScreen(previousScreen);
        menuScreen = null; previousScreen = null;
        if (hudProbe != null) { try { hudProbe.close(); } finally { hudProbe = null; } }
        mc.options.pauseOnLostFocus = originalPause;
    }
    private static void fail(String stage, Throwable failure) {
        failed = true;
        LOGGER.error("Lads auto-world QA FAILED: " + stage, failure);
    }
}
