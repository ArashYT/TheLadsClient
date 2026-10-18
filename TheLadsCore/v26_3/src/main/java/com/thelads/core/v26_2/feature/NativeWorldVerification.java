package com.thelads.core.v26_2.feature;

import com.thelads.core.shared.SharedContentPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in game-API world verification. Never opens a save outside the repository's isolated QA directory. */
public final class NativeWorldVerification {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String SAVE = "Client QA 26_3";
    private static boolean initialized, verified, failed, opened, readyLogged, closing, originalPause, syntheticInput;
    private static long titleSince, openedAt, nextStopCheck;
    private static Path gameDirectory;
    private static long captureAfter, slimesClearedAt;
    private static boolean captureStarted;
    private static int chatCaptureStep;
    private static long chatCaptureAt;
    private static boolean titleCaptured, moreCaptured;
    /** The title screen and its More screen photographed before the world opens (verify133, or LADS_VERIFY_CAPTURE_TITLE). */
    private static final boolean TITLE_CAPTURE = Boolean.getBoolean("thelads.verify133") || Boolean.getBoolean("thelads.verifyTitleCapture");
    private static int titleTrip; // verify133: 1 More is open, 2 back on the title, 3 captured again
    /** verifyVersionSwitch (with the title capture): the title's Switch Versions pressed, its list and a version's confirmation photographed, Relaunch Now pressed. */
    private static final boolean VERSION_SWITCH = Boolean.getBoolean("thelads.verifyVersionSwitch");
    private static int versionTrip; // 0 not started, 1 list open, 2 list photographed, 3 confirmation open, 4 photographed, 5 done
    private static long versionTripAt;
    private static long titleTripAt;
    private static Screen menuScreen;
    private static String captureKind = "menu";
    private static NativeHudEditorProbe hudProbe;
    private static Screen previousScreen;
    private static long menuOpenedAt, menuFirstFrame;
    private static int menuFrames;
    private static boolean menuCaptureStarted;
    private static volatile boolean menuCaptureFinished;
    private static Throwable menuCaptureFailure;
    private static Path menuOutput;
    private NativeWorldVerification() {}

    public static void initialize() {
        if (initialized || !Boolean.getBoolean("thelads.verifyAutoWorld")) return;
        initialized = true;
        try {
            gameDirectory = checkedGameDirectory(FabricLoader.getInstance().getGameDir());
            verified = true;
            originalPause = Minecraft.getInstance().options.pauseOnLostFocus;
            Minecraft.getInstance().options.pauseOnLostFocus = false;
            ClientTickEvents.START_CLIENT_TICK.register(mc -> tick());
            LOGGER.info("Lads auto-world QA BEGIN: isolated save '{}'; game APIs only; no input/focus verification", SAVE);
        } catch (Exception failure) { fail("directory validation", failure); }
    }

    static Path checkedGameDirectory(Path candidate) throws IOException {
        Path game = candidate.toRealPath();
        Path verification = game.getParent();
        Path artifacts = verification == null ? null : verification.getParent();
        Path repository = artifacts == null ? null : artifacts.getParent();
        if (!game.getFileName().toString().equals("26.3-title") || verification == null
            || !verification.getFileName().toString().equals("verification") || artifacts == null
            || !artifacts.getFileName().toString().equals("artifacts") || repository == null
            || !Files.isRegularFile(repository.resolve("TheLadsCore/settings.gradle"))) {
            throw new IOException("Auto-world QA requires artifacts/verification/26.3-title with its repository marker");
        }
        Path expected = repository.resolve("artifacts/verification/26.3-title").toRealPath();
        if (!expected.equals(game)) throw new IOException("QA game directory resolves outside its expected location");
        return game;
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
            if (!opened && titleCaptured && titleTrip < 2 && now >= titleTripAt) titleRoundTrip(mc, now);
            if (VERSION_SWITCH && !opened && titleTrip == 3 && versionTrip < 5 && now >= versionTripAt) versionSwitchTrip(mc, now);
            if (!opened) {
                if (mc.level != null) throw new IllegalStateException("A world was loaded before the isolated QA request");
                boolean title = mc.gui.screen() instanceof TitleScreen
                    || mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("LadsTitleScreen26");
                if (!title || mc.gui.overlay() != null || !mc.isGameLoadFinished()) { titleSince = 0; return; }
                if(!SkinLoadProbe.tick())return;
                if (titleSince == 0) titleSince = now;
                if (now - titleSince < 5_000_000_000L) return;
                Files.createDirectories(gameDirectory.resolve("saves"));
                Path saves = gameDirectory.resolve("saves").toRealPath();
                if (!Files.isRegularFile(saves.resolve(SAVE).resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) {
                    // A fresh sandbox has no QA world yet: make a superflat one (survival, normal difficulty, cheats on; its display name is the
                    // "Client QA 26.2" the clumps probe expects) and open it as it is made.
                    if (!(saves.startsWith(gameDirectory) || sandboxSaves(saves))) throw new IOException("QA saves directory resolves outside the isolated QA directory");
                    opened = true; openedAt = now;
                    LOGGER.info("Lads auto-world QA CREATE: {} (superflat, seed 135)", SAVE);
                    var settings = new net.minecraft.world.level.LevelSettings("Client QA 26.2", net.minecraft.world.level.GameType.SURVIVAL,
                        new net.minecraft.world.level.LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.NORMAL, false, true), true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(SAVE, settings, new net.minecraft.world.level.levelgen.WorldOptions(135L, false, false),
                        registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                            .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.gui.screen());
                    return;
                }
                Path world = saves.resolve(SAVE).toRealPath();
                if (!(saves.startsWith(gameDirectory) || sandboxSaves(saves)) || !world.getParent().equals(saves)
                    || !Files.isRegularFile(world.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Existing isolated QA save is missing or resolves outside the QA saves directory");
                }
                opened = true; openedAt = now;
                LOGGER.info("Lads auto-world QA OPEN: {}", SAVE);
                mc.createWorldOpenFlows().openWorld(SAVE, () -> fail("world open cancelled or returned to menu", null));
            } else if (mc.level != null && mc.player != null) {
                // A QA run that ended with its player dead reopens on the death screen: respawn, as the 1.8.9 self-test does.
                // Only until the world is first ready: later deaths belong to the captures (SprintCapture checks its death screen).
                if (!readyLogged && mc.player.isDeadOrDying() && mc.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen) {
                    mc.player.respawn();
                    mc.setScreenAndShow(null);
                }
                // Remove only Minecraft's ordinary pause screen. Never accept confirmation, error or upgrade dialogs.
                if (mc.gui.screen() != null && mc.gui.screen().getClass() == PauseScreen.class && menuScreen == null && !FixesCapture.busy()) mc.setScreenAndShow(null);
                if (worldReady() && !readyLogged) {
                    readyLogged = true;
                    NativeImprovementsProbe.prepareCapture();
                    captureAfter = now + 15_000_000_000L;
                    chatCaptureAt = now + 7_000_000_000L;
                    LOGGER.info("Lads auto-world QA READY: existing local world loaded, alive, unpaused and screen-free; focus not asserted");
                }
            }
            // The QA world is a Normal superflat (the probes need hostile mobs), where slimes spawn at any light and killed QA players
            // mid-run (owner, 2026-10-10): none is ever kept. No probe uses slimes.
            if (readyLogged && now - slimesClearedAt > 500_000_000L && mc.getSingleplayerServer() instanceof net.minecraft.server.MinecraftServer server) {
                slimesClearedAt = now;
                server.execute(() -> {
                    for (var level : server.getAllLevels())
                        for (var slime : level.getEntities(net.minecraft.world.entity.EntityTypes.SLIME, entity -> true)) slime.discard();
                });
            }
            boolean captureReady = readyLogged && worldReady() && menuScreen == null && mc.gui.screen() == null;
            // In-world captures run one at a time: each may tick while it is the one running or none is.
            KillBannerCapture.tick(gameDirectory, captureReady && (KillBannerCapture.busy() || !captureBusy()));
            KillStreakCapture.tick(gameDirectory, captureReady && (KillStreakCapture.busy() || !captureBusy()));
            KillBanner191Capture.tick(gameDirectory, captureReady && !captureBusy());
            OldAnimationsCapture.tick(gameDirectory, captureReady && (OldAnimationsCapture.busy() || !captureBusy()));
            Anim172Capture.tick(gameDirectory, captureReady && (Anim172Capture.busy() || !captureBusy()));
            ZoomCapture.tick(gameDirectory, captureReady && (ZoomCapture.busy() || !captureBusy()));
            SkinLayersCapture.tick(gameDirectory, captureReady && (SkinLayersCapture.busy() || !captureBusy()));
            SprintCapture.tick(gameDirectory, captureReady && (SprintCapture.busy() || !captureBusy()));
            Hud170Capture.tick(gameDirectory, captureReady && (Hud170Capture.busy() || !captureBusy()));
            HudFlickerCapture.tick(gameDirectory, captureReady && (HudFlickerCapture.busy() || !captureBusy()));
            UiCapture.tick(gameDirectory, captureReady && (UiCapture.busy() || !captureBusy()));
            ItemPhysicsCapture.tick(gameDirectory, captureReady && (ItemPhysicsCapture.busy() || !captureBusy()));
            InventoryCapture.tick(gameDirectory, captureReady && (InventoryCapture.busy() || !captureBusy()));
            ItemsCapture.tick(gameDirectory, captureReady && (ItemsCapture.busy() || !captureBusy()));
            CheatsProbe.tick(gameDirectory, captureReady);
            KeepUiProbe26.tick(captureReady && !captureBusy());
            SoundBreakProbe26.tick(captureReady && !captureBusy());
            HudOverrideCapture26.tick(captureReady && !captureBusy());
            Anim1910Capture26.tick(captureReady && !captureBusy());
            com.thelads.core.v26_2.cosmetics.anim.EmoteCapture26.tick(gameDirectory, captureReady && !captureBusy()); // LADS_QA_EMOTE=1
            Anim1911Capture26.tick(captureReady && !captureBusy());
            ServerFeaturesCapture.tick(gameDirectory, captureReady && (ServerFeaturesCapture.busy() || !captureBusy()));
            // After the Chat module's own capture, so its message does not land among the heads, and after the render scale probe,
            // whose world frames its chat screen would interrupt.
            ChatHeadsCapture.tick(captureReady && chatCaptureStep >= 2 && !NativeRenderScaleProbe.pending() && (ChatHeadsCapture.busy() || !captureBusy()));
            F3FovCapture.tick(gameDirectory, captureReady && (F3FovCapture.busy() || !captureBusy()));
            RaisedDollCapture.tick(gameDirectory, captureReady && (RaisedDollCapture.busy() || !captureBusy()));
            HudLayoutCapture.tick(gameDirectory, captureReady && (HudLayoutCapture.busy() || !captureBusy()));
            MouseTweaksCapture.tick(gameDirectory, captureReady && (MouseTweaksCapture.busy() || !captureBusy()));
            ResolutionCapture.tick(gameDirectory, captureReady && (ResolutionCapture.busy() || !captureBusy()));
            DynamicLightsCapture.tick(gameDirectory, captureReady && (DynamicLightsCapture.busy() || !captureBusy()));
            ParticleCoreCapture.tick(gameDirectory, captureReady && (ParticleCoreCapture.busy() || !captureBusy()));
            SoundsCapture.tick(gameDirectory, captureReady && (SoundsCapture.busy() || !captureBusy()));
            HypixelCapture.tick(gameDirectory, captureReady && (HypixelCapture.busy() || !captureBusy()));
            ActionBarCapture.tick(gameDirectory, captureReady && (ActionBarCapture.busy() || !captureBusy()));
            WorldCapture.tick(gameDirectory, captureReady && (WorldCapture.busy() || !captureBusy()));
            FixesCapture.tick(gameDirectory, captureReady && !captureBusy());
            com.thelads.core.v26_2.feature.food.AppleSkinSyncCapture.tick(gameDirectory, captureReady && (com.thelads.core.v26_2.feature.food.AppleSkinSyncCapture.busy() || !captureBusy()));
            barRuns();
            if (opened && !readyLogged && now - openedAt > 90_000_000_000L)
                throw new IllegalStateException("QA world did not become ready within 90 seconds; screen=" + (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName()));
            Path menuRequest = gameDirectory.resolve(".lads-qa-capture-menu");
            Path hudRequest = gameDirectory.resolve(".lads-qa-capture-hud");
            // A menu or HUD capture opens a screen, which zooms out and stops item use: never while a world capture runs.
            if (menuScreen == null && readyLogged && worldReady()
                && !captureBusy()
                && !com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots.NativeScreenshotsProbe.running()
                && (Files.isRegularFile(menuRequest, LinkOption.NOFOLLOW_LINKS)
                    || Files.isRegularFile(hudRequest, LinkOption.NOFOLLOW_LINKS))) {
                boolean hud = Files.isRegularFile(hudRequest, LinkOption.NOFOLLOW_LINKS);
                Files.delete(hud ? hudRequest : menuRequest);
                // Superflat spawns slimes; a QA player slain while the screens are photographed stalls the run: peaceful from here on (the probes before need hostile mobs).
                if (mc.getConnection() != null) mc.getConnection().sendCommand("difficulty peaceful");
                previousScreen = mc.gui.screen();
                captureKind = hud ? "HUD" : "pause";
                if (hud) {
                    hudProbe = new NativeHudEditorProbe();
                    menuScreen = hudProbe.open();
                } else menuScreen = new net.minecraft.client.gui.screens.PauseScreen(true);
                menuOpenedAt = now; menuFirstFrame = 0; menuFrames = 0;
                menuCaptureStarted = false; menuCaptureFinished = false; menuCaptureFailure = null; menuOutput = null;
                mc.setScreenAndShow(menuScreen);
                LOGGER.info("Lads {} capture BEGIN: explicit isolated request; real screen and completed framebuffer", captureKind);
            }
        } catch (Exception failure) { fail("world startup", failure); }
    }

    /** verify133: after the title capture, More and back again; the title is captured once more (Essential stays in its row). */
    private static void titleRoundTrip(Minecraft mc, long now) {
        if (titleTrip == 0) {
            titleTrip = 3;
            if (mc.gui.screen() instanceof TitleScreen screen) for (var child : screen.children())
                if (child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals("More...")) {
                    button.onPress(null);
                    titleTrip = 1;
                    titleTripAt = now + 1_500_000_000L;
                    LOGGER.info("Lads title round trip: More opened");
                    return;
                }
            LOGGER.info("Lads title round trip skipped: no More button");
        } else if (mc.gui.screen() != null) {
            mc.gui.screen().onClose();
            titleTrip = 2;
            titleTripAt = now + 2_500_000_000L;
        }
    }

    /** verifyVersionSwitch: Switch Versions on the title, the first listed version, then Relaunch Now (the launcher's request is written and the game quits). */
    private static void versionSwitchTrip(Minecraft mc, long now) {
        Screen screen = mc.gui.screen();
        String label = switch (versionTrip) { case 0 -> "Switch Versions"; case 2 -> "Lads Client "; case 4 -> "Relaunch Now"; default -> null; };
        if (label == null || screen == null) return;
        for (var child : screen.children()) {
            if (!(child instanceof net.minecraft.client.gui.components.Button button) || !button.visible) continue;
            String text = button.getMessage().getString();
            if (versionTrip == 2 ? !text.startsWith(label) : !text.equals(label)) continue;
            LOGGER.info("Lads version switch: pressing '{}' on {}", text, screen.getClass().getSimpleName());
            versionTrip++;
            versionTripAt = now + 2_500_000_000L;
            button.onPress(null);
            if (versionTrip == 5) LOGGER.info("Lads version switch END: Relaunch Now pressed; request file written={}", Files.isRegularFile(gameDirectory.resolve("lads_next_version.json")));
            return;
        }
        if (versionTrip == 0) { versionTrip = 5; LOGGER.info("Lads version switch skipped: no Switch Versions button on {}", screen.getClass().getName()); }
    }

    /** QA: a screen's visible buttons as "label@x,y wxh", for the capture log. */
    private static String titleLabels(Screen screen) {
        if (screen == null) return "[]";
        return screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible)
            .map(c -> (net.minecraft.client.gui.components.AbstractWidget) c)
            .map(w -> w.getMessage().getString() + "@" + w.getX() + "," + w.getY() + " " + w.getWidth() + "x" + w.getHeight()).toList().toString();
    }

    /** QA: the virtual pointer (never the OS cursor) over a widget, so the next frames show it hovered. */
    private static void qaPoint(Minecraft mc, net.minecraft.client.gui.components.AbstractWidget widget) {
        try {
            var window = mc.getWindow();
            var x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc.mouseHandler, (widget.getX() + widget.getWidth() / 2.0) * window.getScreenWidth() / window.getGuiScaledWidth());
            y.setDouble(mc.mouseHandler, (widget.getY() + widget.getHeight() / 2.0) * window.getScreenHeight() / window.getGuiScaledHeight());
        } catch (ReflectiveOperationException failure) { LOGGER.warn("QA pointer unavailable", failure); }
    }

    /** Saves linked by the launcher to the shared folder count only when that folder is a sandbox under artifacts/verification. */
    private static boolean sandboxSaves(Path saves) throws IOException {
        return SharedContentPaths.redirectedInside(gameDirectory.getParent()) && Files.isDirectory(SharedContentPaths.savesDir())
            && saves.equals(SharedContentPaths.savesDir().toRealPath());
    }
    public static boolean active() { return verified && !closing; }
    /** Lads input rules act only while the window has focus; synthetic QA events stand in for it, inside the verified sandbox only. */
    public static boolean windowActive() { return Minecraft.getInstance().isWindowActive() || syntheticInput && active(); }
    static void syntheticInput(boolean on) { syntheticInput = on; }
    /** A requested QA menu or HUD capture owns the screen. */
    public static boolean menuCaptureActive() { return menuScreen != null; }
    /** Capture a completed game frame, including GUI, through Minecraft's own GPU readback. */
    public static void renderedFrame(com.mojang.blaze3d.pipeline.RenderTarget target) {
        if (hudProbe != null && active() && !failed) hudProbe.frame(target, gameDirectory);
        if (SoundsCapture.busy()) SoundsCapture.frame(target, gameDirectory);
        if (HypixelCapture.busy()) HypixelCapture.frame(target, gameDirectory);
        if (ActionBarCapture.busy()) ActionBarCapture.frame(target, gameDirectory);
        if (HudOverrideCapture26.busy()) HudOverrideCapture26.frame(target, gameDirectory);
        if (Anim1910Capture26.busy()) Anim1910Capture26.frame(target, gameDirectory);
        if (com.thelads.core.v26_2.cosmetics.anim.EmoteCapture26.busy()) com.thelads.core.v26_2.cosmetics.anim.EmoteCapture26.frame(target, gameDirectory);
        if (Anim1911Capture26.busy()) Anim1911Capture26.frame(target, gameDirectory);
        if (FixesCapture.busy()) FixesCapture.frame(target);
        renderMenuCapture(target);
        if(TITLE_CAPTURE&&active()&&!opened&&!titleCaptured&&titleSince>0
            &&System.nanoTime()-titleSince>2_000_000_000L&&Minecraft.getInstance().gui.screen() instanceof TitleScreen&&Minecraft.getInstance().gui.overlay()==null) {
            titleCaptured=true;
            try {
                Path folder=gameDirectory.resolve("screenshots");Files.createDirectories(folder);
                if(!folder.toRealPath().startsWith(gameDirectory))throw new IOException("Unsafe QA screenshot directory");
                Path output=folder.resolve("native-title-"+System.currentTimeMillis()+".png");
                String labels=titleLabels(Minecraft.getInstance().gui.screen());
                net.minecraft.client.Screenshot.takeScreenshot(target,image->{try{image.writeToFile(output);LOGGER.info("Lads title capture END: 1 passed, 0 failed; buttons {}; actual completed framebuffer at {}",labels,output);}catch(Exception e){fail("title capture",e);}finally{image.close();}});
                titleTripAt=System.nanoTime()+500_000_000L;
            }catch(Exception e){fail("title capture",e);}
        }
        if(titleTrip==1&&!moreCaptured&&System.nanoTime()>=titleTripAt-700_000_000L
            &&Minecraft.getInstance().gui.screen() instanceof com.thelads.core.v26_2.gui.TitleExtrasScreen26 more&&Minecraft.getInstance().gui.overlay()==null) {
            moreCaptured=true;
            try {
                Path output=gameDirectory.resolve("screenshots").resolve("native-title-more-"+System.currentTimeMillis()+".png");
                String labels=titleLabels(more);
                net.minecraft.client.Screenshot.takeScreenshot(target,image->{try{image.writeToFile(output);LOGGER.info("Lads title More capture END: 1 passed, 0 failed; buttons {}; {}",labels,output);}catch(Exception e){fail("title More capture",e);}finally{image.close();}});
            }catch(Exception e){fail("title More capture",e);}
        }
        if((versionTrip==1||versionTrip==3)&&System.nanoTime()>=versionTripAt-700_000_000L
            &&Minecraft.getInstance().gui.screen() instanceof com.thelads.core.v26_2.gui.VersionSwitchScreen26 list&&Minecraft.getInstance().gui.overlay()==null) {
            String kind=versionTrip==1?"list":"confirm";
            versionTrip++;
            try {
                Path output=gameDirectory.resolve("screenshots").resolve("native-version-"+kind+"-"+System.currentTimeMillis()+".png");
                Files.createDirectories(output.getParent());
                String labels=titleLabels(list);
                net.minecraft.client.Screenshot.takeScreenshot(target,image->{try{image.writeToFile(output);LOGGER.info("Lads version switch {} capture END: 1 passed, 0 failed; buttons {}; {}",kind,labels,output);}catch(Exception e){fail("version switch capture",e);}finally{image.close();}});
                versionTripAt=System.nanoTime()+1_000_000_000L;
            }catch(Exception e){fail("version switch capture",e);}
        }
        if(titleTrip==2&&System.nanoTime()>=titleTripAt&&Minecraft.getInstance().gui.screen() instanceof TitleScreen&&Minecraft.getInstance().gui.overlay()==null) {
            titleTrip=3;
            try {
                Path output=gameDirectory.resolve("screenshots").resolve("native-title-after-more-"+System.currentTimeMillis()+".png");
                net.minecraft.client.Screenshot.takeScreenshot(target,image->{try{image.writeToFile(output);LOGGER.info("Lads title round trip capture END: {}",output);}catch(Exception e){fail("title round trip capture",e);}finally{image.close();}});
            }catch(Exception e){fail("title round trip capture",e);}
        }
        chatCapture(target);
        KillBannerCapture.frame(target, gameDirectory);
        KillStreakCapture.frame(target, gameDirectory);
        KillBanner191Capture.frame(target, gameDirectory);
        OldAnimationsCapture.frame(target, gameDirectory);
        Anim172Capture.frame(target, gameDirectory);
        ZoomCapture.frame(target, gameDirectory);
        SkinLayersCapture.frame(target, gameDirectory);
        SprintCapture.frame(target, gameDirectory);
        Hud170Capture.frame(target, gameDirectory);
        HudFlickerCapture.frame(target, gameDirectory);
        UiCapture.frame(target, gameDirectory);
        ItemPhysicsCapture.frame(target, gameDirectory);
        InventoryCapture.frame(target, gameDirectory);
        ItemsCapture.frame(target, gameDirectory);
        ServerFeaturesCapture.frame(target, gameDirectory);
        ChatHeadsCapture.frame(target, gameDirectory);
        F3FovCapture.frame(target, gameDirectory);
        RaisedDollCapture.frame(target, gameDirectory);
        HudLayoutCapture.frame(target, gameDirectory);
        MouseTweaksCapture.frame(target, gameDirectory);
        ResolutionCapture.frame(target, gameDirectory);
        com.thelads.core.v26_2.feature.food.AppleSkinSyncCapture.frame(target, gameDirectory);
        DynamicLightsCapture.frame(target, gameDirectory);
        ParticleCoreCapture.frame(target, gameDirectory);
        WorldCapture.frame(target, gameDirectory);
        if (!worldReady() || !readyLogged || captureStarted || System.nanoTime() < captureAfter || ChatHeadsCapture.pending()) return;
        captureStarted = true;
        try {
            Path folder = gameDirectory.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(gameDirectory)) throw new IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve("native-world-" + System.currentTimeMillis() + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try {
                    image.writeToFile(output);
                    LOGGER.info("Lads world capture END: 1 passed, 0 failed; actual completed game frame at {}", output);
                } catch (Exception failure) { fail("world screenshot", failure); }
                finally { image.close(); Minecraft.getInstance().execute(NativeImprovementsProbe::restoreCapture); }
            });
        } catch (Exception failure) { fail("world screenshot", failure); }
    }
    /** Chat module QA: a gameplay frame, then one about 100 ms into a new message's slide-in; the HUD around chat must not move. */
    private static void chatCapture(com.mojang.blaze3d.pipeline.RenderTarget target) {
        var mc = Minecraft.getInstance();
        if (!readyLogged || chatCaptureAt == 0 || chatCaptureStep >= 2) return;
        // Wait out a closing menu's fade so both frames show plain gameplay.
        if (menuScreen != null || mc.gui.screen() != null) { chatCaptureAt = Math.max(chatCaptureAt, System.nanoTime() + 1_500_000_000L); return; }
        if (System.nanoTime() < chatCaptureAt || !worldReady()) return;
        String name = chatCaptureStep++ == 0 ? "chat-before" : "chat-arriving";
        try {
            Path output = gameDirectory.resolve("screenshots").resolve("native-" + name + "-" + System.currentTimeMillis() + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); LOGGER.info("Lads {} capture END: actual completed game frame at {}", name, output); }
                catch (Exception failure) { fail(name + " capture", failure); }
                finally { image.close(); }
            });
        } catch (Exception failure) { fail(name + " capture", failure); }
        if (chatCaptureStep == 1) {
            mc.gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("Lads chat QA: only this new line slides in"));
            chatCaptureAt = System.nanoTime() + 100_000_000L;
        }
    }
    private static void updateMenuCapture(Minecraft mc, long now) {
        if (menuCaptureFinished) { finishMenuCapture(mc, menuCaptureFailure); return; }
        if (hudProbe != null) hudProbe.tick();
        if (mc.level == null || mc.player == null || mc.player.isDeadOrDying()
            || mc.gui.screen() != menuScreen && (hudProbe == null || !hudProbe.inGame())) {
            finishMenuCapture(mc, new IllegalStateException("The requested QA menu was replaced before capture by " + (mc.gui.screen() == null ? "no screen" : mc.gui.screen().getClass().getName())));
        } else if (now - menuOpenedAt > 30_000_000_000L) {
            finishMenuCapture(mc, new IllegalStateException("The requested QA menu did not finish rendering/readback within 30 seconds"));
        }
    }
    private static void renderMenuCapture(com.mojang.blaze3d.pipeline.RenderTarget target) {
        Minecraft mc = Minecraft.getInstance();
        // worldReady intentionally requires screen == null; this path instead requires our exact real menu.
        if (!active() || failed || menuScreen == null || menuCaptureStarted || mc.gui.screen() != menuScreen
            || mc.gui.overlay() != null || mc.level == null || mc.player == null || mc.player.isDeadOrDying()) return;
        long now = System.nanoTime();
        if (menuFirstFrame == 0) menuFirstFrame = now;
        menuFrames++;
        // The pause frame shows a hovered button: lifted and glowing.
        if ("pause".equals(captureKind) && menuFrames == 2) for (var child : menuScreen.children())
            if (child instanceof net.minecraft.client.gui.components.Button button
                && button.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("menu.returnToGame").getString()))
                qaPoint(mc, button);
        if (hudProbe != null && !hudProbe.readyForCapture()) return;
        if (menuFrames < 2 || now - menuFirstFrame < 1_500_000_000L) return;
        menuCaptureStarted = true;
        try {
            Path folder = gameDirectory.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(gameDirectory)) throw new IOException("QA screenshot folder is outside the isolated game directory");
            menuOutput = folder.resolve("native-" + captureKind.toLowerCase(java.util.Locale.ROOT) + "-" + System.currentTimeMillis() + ".png");
            Path output = menuOutput;
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); }
                catch (Exception failure) { menuCaptureFailure = failure; }
                finally { image.close(); menuCaptureFinished = true; }
            });
        } catch (Exception failure) { menuCaptureFailure = failure; menuCaptureFinished = true; }
    }
    private static void finishMenuCapture(Minecraft mc, Throwable failure) {
        if (menuScreen == null) return;
        if (failure == null && "mods".equals(captureKind)
            && !(menuScreen instanceof com.thelads.core.v26_2.gui.LadsSettingsScreen26 mods && mods.isModsViewOpen()))
            failure = new IllegalStateException("The Installed mods view was not open when its frame was captured");
        if(failure==null && java.util.Set.of("pause","colors","actions","chat","skin-changer","skin","packs","controls","worlds","folders","menu").contains(captureKind)){
            if ("menu".equals(captureKind))
                LOGGER.info("Lads menu capture END: 1 passed, 0 failed; {} completed frames; actual framebuffer at {}", menuFrames, menuOutput);
            else LOGGER.info("Lads {} capture END: actual framebuffer at {}",captureKind,menuOutput);
            menuScreen=switch(captureKind){
                case "pause"->new net.minecraft.client.gui.screens.options.SkinCustomizationScreen(null,mc.options);
                case "skin-changer"->new net.minecraft.client.gui.screens.packs.PackSelectionScreen(mc.getResourcePackRepository(),repository->{},mc.getResourcePackDirectory(),net.minecraft.network.chat.Component.literal("Resource packs"));
                case "skin"->new com.thelads.core.v26_2.gui.SkinChangerScreen(null);
                case "packs"->new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(null,mc.options);
                case "controls"->new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(null);
                case "worlds"->new com.thelads.core.v26_2.gui.WorldSourcesScreen(null);
                case "folders"->new com.thelads.core.v26_2.gui.LadsSettingsScreen26(null);
                case "menu"->{var view=new com.thelads.core.v26_2.gui.LadsSettingsScreen26(null);view.openGlobalColors();yield view;}
                case "colors"->{var view=new com.thelads.core.v26_2.gui.LadsSettingsScreen26(null);view.openModule("Paperdoll");view.openDisplayActions();yield view;}
                case "actions"->{var view=new com.thelads.core.v26_2.gui.LadsSettingsScreen26(null);view.openModule("Chat");yield view;}
                default->{ var view=new com.thelads.core.v26_2.gui.LadsSettingsScreen26(null); view.openMods(); yield view; }
            };
            captureKind=switch(captureKind){case "pause"->"skin";case "skin"->"skin-changer";case "skin-changer"->"packs";case "packs"->"controls";case "controls"->"worlds";case "worlds"->"folders";case "folders"->"menu";case "menu"->"colors";case "colors"->"actions";case "actions"->"chat";default->"mods";};
            menuOpenedAt=System.nanoTime();menuFirstFrame=0;menuFrames=0;
            menuCaptureStarted=false;menuCaptureFinished=false;menuCaptureFailure=null;menuOutput=null;
            mc.setScreenAndShow(menuScreen);menuScreen=mc.gui.screen();return;
        }
        if (mc.gui.screen() == menuScreen) mc.setScreenAndShow(previousScreen);
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
            LOGGER.error("Lads menu capture FAILED", failure);
            LOGGER.error("Lads mods view capture FAILED", failure);
            fail("menu screenshot", failure);
        }
    }
    /** True while any in-world QA capture is running. */
    /** The captures that do not report their own steps to the QA progress bar: it shows them as a run, name only, while they are busy. */
    private static final java.util.Map<String, java.util.function.BooleanSupplier> BAR_RUNS = new java.util.LinkedHashMap<>();
    static {
        BAR_RUNS.put("HUD info", HudInfoCapture::busy); BAR_RUNS.put("kill banner", KillBannerCapture::busy); BAR_RUNS.put("kill streak", KillStreakCapture::busy);
        BAR_RUNS.put("kill banner 1.9.1", KillBanner191Capture::busy); BAR_RUNS.put("old animations", OldAnimationsCapture::busy); BAR_RUNS.put("anim 1.7.2", Anim172Capture::busy);
        BAR_RUNS.put("zoom", ZoomCapture::busy); BAR_RUNS.put("server features", ServerFeaturesCapture::busy); BAR_RUNS.put("chat heads", ChatHeadsCapture::busy);
        BAR_RUNS.put("F3 and FOV", F3FovCapture::busy); BAR_RUNS.put("raised and paper doll", RaisedDollCapture::busy); BAR_RUNS.put("HUD layout", HudLayoutCapture::busy);
        BAR_RUNS.put("mouse tweaks", MouseTweaksCapture::busy); BAR_RUNS.put("resolution", ResolutionCapture::busy); BAR_RUNS.put("dynamic lights", DynamicLightsCapture::busy);
        BAR_RUNS.put("AppleSkin sync", com.thelads.core.v26_2.feature.food.AppleSkinSyncCapture::busy); BAR_RUNS.put("skin layers", SkinLayersCapture::busy);
        BAR_RUNS.put("sprint", SprintCapture::busy); BAR_RUNS.put("HUD 1.7.0", Hud170Capture::busy); BAR_RUNS.put("HUD flicker", HudFlickerCapture::busy);
        BAR_RUNS.put("inventory", InventoryCapture::busy); BAR_RUNS.put("particle core", ParticleCoreCapture::busy); BAR_RUNS.put("sounds", SoundsCapture::busy);
        BAR_RUNS.put("Hypixel", HypixelCapture::busy); BAR_RUNS.put("action bar", ActionBarCapture::busy); BAR_RUNS.put("world", WorldCapture::busy);
        BAR_RUNS.put("items", ItemsCapture::busy); BAR_RUNS.put("fixes", FixesCapture::busy);
    }
    private static String barPrev;
    /** Once per tick: a capture that became busy and reported no run of its own gets a bar run until it is not busy any more. */
    private static void barRuns() {
        if (!com.thelads.core.client.qa.QaProgress.on()) return;
        String busy = null;
        for (var run : BAR_RUNS.entrySet()) if (run.getValue().getAsBoolean()) { busy = run.getKey(); break; }
        if (busy != null && !busy.equals(barPrev) && !com.thelads.core.client.qa.QaProgress.running()) {
            com.thelads.core.client.qa.QaProgress.begin(busy, 0);
            com.thelads.core.client.qa.QaProgress.step("running");
        } else if (busy == null && barPrev != null && barPrev.equals(com.thelads.core.client.qa.QaProgress.run())) com.thelads.core.client.qa.QaProgress.done();
        barPrev = busy;
    }
    static boolean captureBusy() {
        return HudInfoCapture.busy() || KillBannerCapture.busy() || KillStreakCapture.busy() || KillBanner191Capture.busy() || OldAnimationsCapture.busy() || Anim172Capture.busy() || ZoomCapture.busy() || ServerFeaturesCapture.busy() ||
            ChatHeadsCapture.busy() || F3FovCapture.busy() || RaisedDollCapture.busy() || HudLayoutCapture.busy() || MouseTweaksCapture.busy() ||
            ResolutionCapture.busy() || DynamicLightsCapture.busy() || com.thelads.core.v26_2.feature.food.AppleSkinSyncCapture.busy() ||
            SkinLayersCapture.busy() || SprintCapture.busy() || Hud170Capture.busy() || HudFlickerCapture.busy() || UiCapture.busy() || ItemPhysicsCapture.busy() || InventoryCapture.busy() || ParticleCoreCapture.busy() || SoundsCapture.busy() || HypixelCapture.busy()
            || ActionBarCapture.busy()
            || WorldCapture.busy()
            || ItemsCapture.busy()
            || FixesCapture.busy()
            || com.thelads.core.v26_2.cosmetics.anim.EmoteCapture26.busy();
    }
    public static boolean worldReady() {
        Minecraft mc = Minecraft.getInstance();
        return active() && opened && !failed && mc != null && mc.level != null && mc.player != null
            && !mc.player.isDeadOrDying() && mc.gui.screen() == null && mc.gui.overlay() == null && !mc.isPaused();
    }
    /** Used around Options.save so the QA-only pause override cannot reach options.txt. */
    public static boolean originalPauseOnLostFocus() { return originalPause; }
    public static void close() {
        SkinLoadProbe.close();
        if (!verified || closing) return;
        closing = true;
        syntheticInput = false;
        Minecraft mc = Minecraft.getInstance();
        if (menuScreen != null && mc.gui.screen() == menuScreen) mc.setScreenAndShow(previousScreen);
        menuScreen = null; previousScreen = null;
        if (hudProbe != null) { try { hudProbe.close(); } finally { hudProbe = null; } }
        mc.options.pauseOnLostFocus = originalPause;
    }
    private static void fail(String stage, Throwable failure) {
        failed = true;
        LOGGER.error("Lads auto-world QA FAILED: " + stage, failure);
    }
}
