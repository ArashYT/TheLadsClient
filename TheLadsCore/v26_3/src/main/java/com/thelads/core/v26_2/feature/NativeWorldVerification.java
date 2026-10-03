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
    private static long captureAfter;
    private static boolean captureStarted;
    private static int chatCaptureStep;
    private static long chatCaptureAt;
    private static boolean titleCaptured;
    private static int titleTrip; // verify133: 1 More is open, 2 back on the title, 3 captured again
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
            if (!opened) {
                if (mc.level != null) throw new IllegalStateException("A world was loaded before the isolated QA request");
                boolean title = mc.gui.screen() instanceof TitleScreen
                    || mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("LadsTitleScreen26");
                if (!title || mc.gui.overlay() != null || !mc.isGameLoadFinished()) { titleSince = 0; return; }
                if(!SkinLoadProbe.tick())return;
                if (titleSince == 0) titleSince = now;
                if (now - titleSince < 5_000_000_000L) return;
                Path saves = gameDirectory.resolve("saves").toRealPath();
                Path world = saves.resolve(SAVE).toRealPath();
                if (!(saves.startsWith(gameDirectory) || sandboxSaves(saves)) || !world.getParent().equals(saves)
                    || !Files.isRegularFile(world.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Existing isolated QA save is missing or resolves outside the QA saves directory");
                }
                opened = true; openedAt = now;
                LOGGER.info("Lads auto-world QA OPEN: {}", SAVE);
                mc.createWorldOpenFlows().openWorld(SAVE, () -> fail("world open cancelled or returned to menu", null));
            } else if (mc.level != null && mc.player != null) {
                // Remove only Minecraft's ordinary pause screen. Never accept confirmation, error or upgrade dialogs.
                if (mc.gui.screen() != null && mc.gui.screen().getClass() == PauseScreen.class && menuScreen == null) mc.setScreenAndShow(null);
                if (worldReady() && !readyLogged) {
                    readyLogged = true;
                    NativeImprovementsProbe.prepareCapture();
                    captureAfter = now + 15_000_000_000L;
                    chatCaptureAt = now + 7_000_000_000L;
                    LOGGER.info("Lads auto-world QA READY: existing local world loaded, alive, unpaused and screen-free; focus not asserted");
                }
            }
            boolean captureReady = readyLogged && worldReady() && menuScreen == null && mc.gui.screen() == null;
            KillBannerCapture.tick(gameDirectory, captureReady && !OldAnimationsCapture.busy() && !ZoomCapture.busy());
            OldAnimationsCapture.tick(gameDirectory, captureReady && !KillBannerCapture.busy() && !ZoomCapture.busy());
            ZoomCapture.tick(gameDirectory, captureReady && !KillBannerCapture.busy() && !OldAnimationsCapture.busy());
            if (opened && !readyLogged && now - openedAt > 90_000_000_000L)
                throw new IllegalStateException("QA world did not become ready within 90 seconds; screen=" + (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName()));
            Path menuRequest = gameDirectory.resolve(".lads-qa-capture-menu");
            Path hudRequest = gameDirectory.resolve(".lads-qa-capture-hud");
            // A menu or HUD capture opens a screen, which zooms out and stops item use: never while a world capture runs.
            if (menuScreen == null && readyLogged && worldReady()
                && !KillBannerCapture.busy() && !OldAnimationsCapture.busy() && !ZoomCapture.busy()
                && !com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots.NativeScreenshotsProbe.running()
                && (Files.isRegularFile(menuRequest, LinkOption.NOFOLLOW_LINKS)
                    || Files.isRegularFile(hudRequest, LinkOption.NOFOLLOW_LINKS))) {
                boolean hud = Files.isRegularFile(hudRequest, LinkOption.NOFOLLOW_LINKS);
                Files.delete(hud ? hudRequest : menuRequest);
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
        renderMenuCapture(target);
        if(Boolean.getBoolean("thelads.verify133")&&active()&&!opened&&!titleCaptured&&titleSince>0
            &&System.nanoTime()-titleSince>2_000_000_000L&&Minecraft.getInstance().gui.screen() instanceof TitleScreen&&Minecraft.getInstance().gui.overlay()==null) {
            titleCaptured=true;
            try {
                Path folder=gameDirectory.resolve("screenshots");Files.createDirectories(folder);
                if(!folder.toRealPath().startsWith(gameDirectory))throw new IOException("Unsafe QA screenshot directory");
                Path output=folder.resolve("native-title-"+System.currentTimeMillis()+".png");
                net.minecraft.client.Screenshot.takeScreenshot(target,image->{try{image.writeToFile(output);LOGGER.info("Lads title capture END: actual completed framebuffer at {}",output);}catch(Exception e){fail("title capture",e);}finally{image.close();}});
                titleTripAt=System.nanoTime()+500_000_000L;
            }catch(Exception e){fail("title capture",e);}
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
        OldAnimationsCapture.frame(target, gameDirectory);
        ZoomCapture.frame(target, gameDirectory);
        if (!worldReady() || !readyLogged || captureStarted || System.nanoTime() < captureAfter) return;
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
        if("essential-settings".equals(captureKind) && menuScreen instanceof PauseScreen
            &&mc.gui.screen()!=menuScreen&&mc.gui.screen()!=null&&mc.gui.screen().getClass().getName().startsWith("gg.essential.")){
            menuScreen=mc.gui.screen();menuOpenedAt=now;
            LOGGER.info("Lads Essential action probe END: 1 passed, 0 failed; pause row's Essential action opened {}",menuScreen.getClass().getName());
        }
        if (hudProbe != null) hudProbe.tick();
        if (mc.level == null || mc.player == null || mc.player.isDeadOrDying()
            || mc.gui.screen() != menuScreen) {
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
        if("essential-settings".equals(captureKind)&&menuScreen instanceof PauseScreen)return;
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
        if(failure==null && java.util.Set.of("pause","essential-settings","colors","actions","chat","skin-changer","skin","packs","controls","worlds","folders","menu").contains(captureKind)){
            if ("menu".equals(captureKind))
                LOGGER.info("Lads menu capture END: 1 passed, 0 failed; {} completed frames; actual framebuffer at {}", menuFrames, menuOutput);
            else LOGGER.info("Lads {} capture END: actual framebuffer at {}",captureKind,menuOutput);
            menuScreen=switch(captureKind){
                case "pause"->{
                    // Essential's actions are a row above the account name; its settings open from there.
                    var settings=menuScreen.children().stream().filter(c->c instanceof com.thelads.core.v26_2.gui.CompactButton26 b&&b.getMessage().getString().equals("Essential")).map(c->(net.minecraft.client.gui.components.Button)c).findFirst();
                    if(settings.isPresent())settings.get().onPress(null);
                    else if(FabricLoader.getInstance().isModLoaded("essential"))throw new IllegalStateException("Essential's settings action missing from the pause menu row");
                    yield menuScreen;
                }
                case "essential-settings"->new net.minecraft.client.gui.screens.options.SkinCustomizationScreen(null,mc.options);
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
            captureKind=switch(captureKind){case "pause"->"essential-settings";case "essential-settings"->"skin";case "skin"->"skin-changer";case "skin-changer"->"packs";case "packs"->"controls";case "controls"->"worlds";case "worlds"->"folders";case "folders"->"menu";case "menu"->"colors";case "colors"->"actions";case "actions"->"chat";default->"mods";};
            menuOpenedAt=System.nanoTime();menuFirstFrame=0;menuFrames=0;
            menuCaptureStarted=false;menuCaptureFinished=false;menuCaptureFailure=null;menuOutput=null;
            if(!"essential-settings".equals(captureKind))mc.setScreenAndShow(menuScreen);menuScreen=mc.gui.screen();return;
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
            fail("menu screenshot", failure);
        }
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
