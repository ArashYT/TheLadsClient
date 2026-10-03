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
    private static int chatCaptureStep;
    private static long chatCaptureAt;
    private static Screen menuScreen, previousScreen;
    private static String captureKind = "menu";
    private static NativeHudEditorProbe hudProbe;
    private static long menuOpenedAt, menuFirstFrame;
    private static int menuFrames;
    private static boolean menuCaptureStarted;
    private static volatile boolean menuCaptureFinished;
    private static Throwable menuCaptureFailure;
    private static Path menuOutput;
    /** U4: the Lads title screen is captured once, after the title checks and before the QA world opens. */
    private static int titleChildren = -1;
    private static long titleChildrenFrame;
    private static boolean titleCaptureStarted, titleCaptured;
    /** U4: the 26.x menu capture chain; the menu request starts at "pause" and ends with the Lads menu and its mods view. */
    private static final java.util.Set<String> CHAIN = java.util.Set.of("pause", "essential", "essential-settings", "packs", "controls", "menu", "chat");
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
                if (!titleCaptured) {
                    if (now - titleSince > 30_000_000_000L) throw new IllegalStateException("The Lads title screen frame was not captured within 30 seconds");
                    return;
                }
                openOrCreate(mc, now);
            } else if (mc.level != null && mc.player != null) {
                // Remove only Minecraft's ordinary pause screen. Never accept confirmation, error or upgrade dialogs.
                // The menu access probe keeps its own pause menu open across rendered frames (U4 pause layout checks).
                if (mc.screen != null && mc.screen.getClass() == PauseScreen.class && menuScreen == null && !NativeMenuAccessProbe.drives(mc.screen))
                    mc.setScreen(null);
                if (worldReady() && !readyLogged) {
                    readyLogged = true;
                    captureAfter = now + 15_000_000_000L;
                    LOGGER.info("Lads auto-world QA READY: existing local world loaded, alive, unpaused and screen-free; focus not asserted");
                }
            }
            // After the in-world probes: they reset the banner and restore the settings it changes.
            // One scene capture at a time: each poses the player and the HUD its own way.
            boolean captureReady = readyLogged && worldReady() && menuScreen == null && mc.screen == null && NativeHudProbe.finished();
            KillBannerCapture.tick(gameDirectory, captureReady && !OldAnimationsCapture.busy() && !ZoomCapture.busy());
            OldAnimationsCapture.tick(gameDirectory, captureReady && !KillBannerCapture.busy() && !ZoomCapture.busy());
            ZoomCapture.tick(gameDirectory, captureReady && !KillBannerCapture.busy() && !OldAnimationsCapture.busy());
            if (opened && !readyLogged && now - openedAt > 90_000_000_000L)
                throw new IllegalStateException("QA world did not become ready within 90 seconds; screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
            Path menuRequest = gameDirectory.resolve(".lads-qa-capture-menu");
            Path hudRequest = gameDirectory.resolve(".lads-qa-capture-hud");
            if (menuScreen == null && readyLogged && worldReady()
                && (Files.isRegularFile(menuRequest, LinkOption.NOFOLLOW_LINKS) || Files.isRegularFile(hudRequest, LinkOption.NOFOLLOW_LINKS))) {
                boolean hud = Files.isRegularFile(hudRequest, LinkOption.NOFOLLOW_LINKS);
                Files.delete(hud ? hudRequest : menuRequest);
                previousScreen = mc.screen;
                captureKind = hud ? "HUD" : "pause";
                if (hud) {
                    hudProbe = new NativeHudEditorProbe();
                    menuScreen = hudProbe.open();
                } else menuScreen = new PauseScreen(true);
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
        renderTitleCapture(target);
        NativeHudProbe.frame(target);
        chatCapture(target);
        KillBannerCapture.frame(target, gameDirectory);
        OldAnimationsCapture.frame(target, gameDirectory);
        ZoomCapture.frame(target, gameDirectory);
        if (!worldReady() || !readyLogged || captureStarted || System.nanoTime() < captureAfter) return;
        captureStarted = true;
        chatCaptureAt = System.nanoTime() + 3_000_000_000L;
        try {
            Path output = screenshot("native-world-");
            try (NativeImage image = Screenshot.takeScreenshot(target)) { image.writeToFile(output); }
            LOGGER.info("Lads world capture END: 1 passed, 0 failed; actual completed game frame at {}", output);
        } catch (Exception failure) { fail("world screenshot", failure); }
    }
    /** A new PNG path in the checked QA screenshots folder (the U3 HUD probe's frames). */
    static Path qaScreenshot(String prefix) throws IOException { return screenshot(prefix); }
    /** Chat module QA: a gameplay frame, then one about 100 ms into a new message's slide-in; the HUD around chat must not move. */
    private static void chatCapture(RenderTarget target) {
        Minecraft mc = Minecraft.getInstance();
        if (!captureStarted || chatCaptureStep >= 2 || System.nanoTime() < chatCaptureAt || menuScreen != null || mc.screen != null || !worldReady()) return;
        String name = chatCaptureStep++ == 0 ? "chat-before" : "chat-arriving";
        try {
            Path output = screenshot("native-" + name + "-");
            try (NativeImage image = Screenshot.takeScreenshot(target)) { image.writeToFile(output); }
            LOGGER.info("Lads {} capture END: actual completed game frame at {}", name, output);
        } catch (Exception failure) { fail(name + " capture", failure); }
        if (chatCaptureStep == 1) {
            mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("Lads chat QA: only this new line slides in"));
            chatCaptureAt = System.nanoTime() + 100_000_000L;
        }
    }
    /** The Lads title screen as players see it (26.x native-title), once the title checks ran and before the world opens. */
    private static void renderTitleCapture(RenderTarget target) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || failed || opened || titleCaptureStarted || titleSince == 0 || System.nanoTime() - titleSince < 3_000_000_000L
            || !NativeMenuAccessProbe.titleChecked() || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        // The layout is stable: no widget keeps being re-added (FancyMenu/Drippy re-add missing tools on every frame).
        if (titleChildren < 0) { titleChildren = mc.screen.children().size(); titleChildrenFrame = frames; return; }
        if (frames < titleChildrenFrame + 3) return;
        titleCaptureStarted = true;
        try {
            if (mc.screen.children().size() != titleChildren)
                throw new IllegalStateException("The Lads title keeps changing: " + titleChildren + " -> " + mc.screen.children().size() + " children in 3 frames");
            // The Essential overlap fix holds after the title was re-opened by the title checks (Essential re-adds its menu).
            if (NativeMenuAccessProbe.essentialOverlayShown(mc.screen))
                throw new IllegalStateException("Essential's menu layer is drawn over the Lads title screen");
            Path output = screenshot("native-title-");
            try (NativeImage image = Screenshot.takeScreenshot(target)) { image.writeToFile(output); }
            LOGGER.info("Lads title capture END: 3 passed, 0 failed; a stable Lads title with no Essential menu layer over it; actual completed title frame at {}", output);
            titleCaptured = true;
        } catch (Exception failure) { fail("title capture", failure); }
    }
    private static Path screenshot(String prefix) throws IOException {
        Path folder = gameDirectory.resolve("screenshots");
        Files.createDirectories(folder);
        if (!folder.toRealPath().startsWith(gameDirectory)) throw new IOException("QA screenshot folder is outside the isolated game directory");
        return folder.resolve(prefix + System.currentTimeMillis() + ".png");
    }
    private static void updateMenuCapture(Minecraft mc, long now) {
        if (menuCaptureFinished) { finishMenuCapture(mc, menuCaptureFailure); return; }
        // Essential opens its settings screen on its own schedule after the relocated Lads action was pressed.
        if ("essential-settings".equals(captureKind) && menuScreen instanceof net.minecraft.client.gui.screens.PauseScreen && mc.screen != menuScreen
            && mc.screen != null && mc.screen.getClass().getName().startsWith("gg.essential.")) essentialOpened(mc, now);
        if (hudProbe != null) hudProbe.tick();
        if (mc.level == null || mc.player == null || mc.player.isDeadOrDying() || mc.screen != menuScreen) {
            finishMenuCapture(mc, new IllegalStateException("The requested QA menu was replaced before capture by " + (mc.screen == null ? "no screen" : mc.screen.getClass().getName())));
        } else if (now - menuOpenedAt > 30_000_000_000L) {
            finishMenuCapture(mc, new IllegalStateException("The requested QA menu did not finish rendering/readback within 30 seconds"));
        }
    }
    private static void renderMenuCapture(RenderTarget target) {
        Minecraft mc = Minecraft.getInstance();
        // worldReady intentionally requires screen == null; this path instead requires our exact real menu.
        if (!active() || failed || menuScreen == null || menuCaptureStarted || mc.screen != menuScreen
            || mc.getOverlay() != null || mc.level == null || mc.player == null || mc.player.isDeadOrDying()) return;
        if ("essential-settings".equals(captureKind) && menuScreen instanceof net.minecraft.client.gui.screens.PauseScreen) return;
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
        if (failure == null && CHAIN.contains(captureKind)) {
            // The 26.x chain's frames that exist here (U4): pause, Essential & extras and Essential's relocated Settings,
            // packs, controls, then the Lads menu and its Installed mods view.
            if ("menu".equals(captureKind))
                LOGGER.info("Lads menu capture END: 1 passed, 0 failed; {} completed frames; actual framebuffer at {}", menuFrames, menuOutput);
            else LOGGER.info("Lads {} capture END: 1 passed, 0 failed; {} completed frames; actual framebuffer at {}", captureKind, menuFrames, menuOutput);
            boolean essential = FabricLoader.getInstance().isModLoaded("essential");
            try {
                Screen next = switch (captureKind) {
                    case "pause" -> {
                        // Essential's actions are a row above the account name; its settings open from there.
                        if (!essential) yield packsScreen(mc);
                        var settings = button(menuScreen, "Essential");
                        if (!(settings instanceof com.thelads.core.v1_21_1.gui.CompactButton121))
                            throw new IllegalStateException("Essential's settings action missing from the pause menu row");
                        settings.onPress();
                        yield menuScreen;
                    }
                    case "essential-settings" -> packsScreen(mc);
                    case "packs" -> new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(null, mc.options);
                    case "controls" -> new LadsSettingsScreen121(null);
                    case "menu" -> { var view = new LadsSettingsScreen121(null); view.ui().openModule("Chat"); yield view; }
                    default -> { var view = new LadsSettingsScreen121(null); view.ui().openMods(); yield view; }
                };
                captureKind = switch (captureKind) {
                    case "pause" -> essential ? "essential-settings" : "packs";
                    case "essential-settings" -> "packs";
                    case "packs" -> "controls";
                    case "controls" -> "menu";
                    case "menu" -> "chat";
                    default -> "mods";
                };
                menuOpenedAt = System.nanoTime(); menuFirstFrame = 0; menuFrames = 0;
                menuCaptureStarted = false; menuCaptureFinished = false; menuCaptureFailure = null; menuOutput = null;
                if ("essential-settings".equals(captureKind)) {
                    menuScreen = next;
                    if (mc.screen != next && mc.screen != null && mc.screen.getClass().getName().startsWith("gg.essential.")) essentialOpened(mc, menuOpenedAt);
                } else {
                    // Controls is swapped for the native Lads controls screen on the way in: capture what is really shown.
                    mc.setScreen(next);
                    menuScreen = mc.screen;
                }
                return;
            } catch (Exception chainFailure) { failure = chainFailure; }
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
    /** The relocated "Essential settings" Lads button opened Essential's own screen: capture that screen next (26.x). */
    private static void essentialOpened(Minecraft mc, long now) {
        menuScreen = mc.screen; menuOpenedAt = now;
        LOGGER.info("Lads Essential action probe END: 1 passed, 0 failed; pause row's Essential action opened {}", menuScreen.getClass().getName());
    }
    private static Screen packsScreen(Minecraft mc) {
        return new net.minecraft.client.gui.screens.packs.PackSelectionScreen(mc.getResourcePackRepository(), repository -> {},
            mc.getResourcePackDirectory(), net.minecraft.network.chat.Component.literal("Resource packs"));
    }
    private static net.minecraft.client.gui.components.Button button(Screen screen, String label) {
        for (var child : screen.children())
            if (child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(label)) return button;
        return null;
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
