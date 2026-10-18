package com.thelads.core.v1_8_9.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.qa.QaProgress;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.mods.CoreCatalogExporter;
import com.thelads.core.v1_8_9.adapter.VanillaGameBridge189;
import com.thelads.core.v1_8_9.gui.LadsPauseButton;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import com.thelads.core.v1_8_9.gui.LadsTitleScreen189;
import com.thelads.core.v1_8_9.gui.TitleExtrasScreen189;
import com.thelads.core.v1_8_9.gui.VersionSwitchScreen189;
import com.thelads.core.v1_8_9.log.Log4jServiceProvider;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * QA only (-Dthelads.verify189Core=true, sandbox game folders only): the C1 checks through 1.8.9's real input paths.
 * Synthetic events go into LWJGL's own keyboard and mouse queues, so GuiScreen.handleInput and Minecraft.runTick read them
 * like typed ones: Right Shift on the title screen, typing in the Lads menu, Right Shift in a QA world, Escape to the pause
 * menu and a click on its "Lads Client" button. Then the launcher catalog is checked and HudProbe runs the C2 HUD checks in
 * the same QA world. Screenshots: game/lads-qa/screenshots.
 */
public final class CoreProbe {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final String WORLD = "Client QA 1_8_9";
    interface Step { boolean run(Minecraft mc) throws Exception; }
    private static final List<Step> STEPS = new java.util.ArrayList<>(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::ladsTitle,
        CoreProbe::titleMore, CoreProbe::backupPrompt, CoreProbe::backupWarning, CoreProbe::backupDone, CoreProbe::title,
        CoreProbe::ofDefaults,
        CoreProbe::menuAtTitle, CoreProbe::menuRendered, CoreProbe::searchClicked, CoreProbe::typed, CoreProbe::erased, CoreProbe::editingLeft,
        CoreProbe::closedToTitle, CoreProbe::worldReady, CoreProbe::fastClouds, KeepUiProbe189::run, CoreProbe::bridgeInWorld, CoreProbe::toastInWorld, CoreProbe::menuInWorld, CoreProbe::installedMods, CoreProbe::closedToGame,
        CoreProbe::pauseMenu, CoreProbe::pauseMultiplayer, CoreProbe::multiplayerList, CoreProbe::pauseClicked, CoreProbe::menuFromPause, CoreProbe::closedToPause, CoreProbe::menuKeyAtPause,
        CoreProbe::catalog));
    /** -Dthelads.verify189Focus: only the title screen and Probe170Misc's steps of that name (with Essential as players have it). */
    private static final String FOCUS = System.getProperty("thelads.verify189Focus");
    static {
        // Focused QA paths, each straight into the QA world: LADS_VERIFY_189_ONLY (the harness passes it as -Dthelads.verify189Only)
        // =itemphysics (Probe170ItemPhysics), =170 (Toggle Sprint & Sneak, then the HUD checks), =raised (Raised and the paper
        // doll, RaisedDollProbe189), =hudflicker (runs of frames with the HUD FPS cap off and on, Probe172HudFlicker), =perf (the 1.7.3
        // benchmark scenes in a fresh perf world, Probe173Perf), =cull (Entity Culling in a crowd behind a wall: views off and on, an
        // audited fast-motion run and uncapped frame times, Probe173Cull), =anim (swing while using items and damage tilt, Probe172Anim),
        // =ui (the reset questions and Fullbright's slider, Probe172Ui), =xhair (the Crosshair Tweaks page and editor previews), =title (the title screen at every GUI scale) or =leave (only the
        // final leave, the 1.7.0 freeze regression check);
        // -Dthelads.verifyChatHeads=true (ChatHeadsProbe189) and -Dthelads.verify189F3Fov=true (Probe170F3Fov).
        String only = System.getProperty("thelads.verify189Only", System.getenv("LADS_VERIFY_189_ONLY"));
        boolean chatHeadsOnly = Boolean.getBoolean("thelads.verifyChatHeads"), f3FovOnly = Boolean.getBoolean("thelads.verify189F3Fov");
        if (FOCUS != null) {
            STEPS.clear();
            STEPS.add(CoreProbe::titleShown);
            STEPS.add(CoreProbe::focused);
            STEPS.addAll(Probe170Misc.steps(FOCUS));
        } else if ("title".equals(only)) { // only the title screen's layout at every GUI scale, and its More, then Switch Versions
            STEPS.subList(3, STEPS.size()).clear();
            STEPS.add(CoreProbe::versionSwitch);
        } else if ("packs".equals(only)) {
            STEPS.clear();
            STEPS.add(CoreProbe::titleShown);
            STEPS.add(Probe193Packs189::runTitlePacks);
            STEPS.add(CoreProbe::quickWorld);
            STEPS.add(CoreProbe::worldReady);
            STEPS.add(Probe193Packs189::runInGamePacks);
        } else if ("packs_relaunch".equals(only)) {
            STEPS.clear();
            STEPS.add(CoreProbe::titleShown);
            STEPS.add(Probe193Packs189::runRelaunchPacks);
        } else {
        if ("itemphysics".equals(only)) {
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::focusedWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe170ItemPhysics.STEPS);
        } else if ("170".equals(only) || "170hud".equals(only)) { // 170hud: the HUD checks only (no synthetic input)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::focusedWorld, CoreProbe::worldReady));
            if ("170".equals(only)) STEPS.addAll(Probe170Sprint.STEPS);
            STEPS.addAll(Probe170Hud.STEPS);
        } else if ("rx".equals(only)) { // 1.7.3 rendering tweaks: A/B frame times, visual parity, per-call costs (Probe173Rx)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::focusedWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe173Rx.STEPS);
        } else if ("render185".equals(only)) { // 1.8.5 built-in render remakes off and on (RenderProbe189)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::focusedWorld, CoreProbe::worldReady));
            STEPS.addAll(RenderProbe189.STEPS);
        } else if ("cull".equals(only)) {
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::focusedWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe173Cull.STEPS);
        } else if ("particles".equals(only)) { // Particle Core in a heavy particle scene (ProbeParticleCore189)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::focusedWorld, CoreProbe::worldReady));
            STEPS.addAll(ProbeParticleCore189.STEPS);
        } else if ("anim".equals(only)) {
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::focusedWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe172Anim.STEPS);
        } else if ("keybinds".equals(only)) {
            STEPS.clear();
            STEPS.add(CoreProbe::titleShown);
            if (!"storm".equals(Probe172Keybinds.PHASE)) STEPS.addAll(Arrays.<Step>asList(CoreProbe::focusedWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe172Keybinds.steps());
        } else if ("hudflicker".equals(only)) {
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::focusedWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe172HudFlicker.STEPS);
        } else if ("perf".equals(only) || "players".equals(only)) {
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, Probe173Perf::open, CoreProbe::worldReady));
            STEPS.addAll(Probe173Perf.STEPS);
        } else if ("unfocusedcap".equals(only)) {
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe173Cap.STEPS);
        } else if ("input".equals(only)) { // 1.8.5 input-latency pacing: FPS and input-to-GPU-done latency (ProbeInput189)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady));
            STEPS.addAll(ProbeInput189.STEPS);
        } else if ("quick".equals(only)) {
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe173Quick.STEPS);
        } else if ("ui".equals(only)) {
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::focusedWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe172Ui.STEPS);
        } else if ("xhair".equals(only)) { // the Crosshair Tweaks settings page and drawing editor with their live previews (ProbeCrosshairPreview189)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::focusedWorld, CoreProbe::worldReady));
            STEPS.addAll(ProbeCrosshairPreview189.STEPS);
        } else if ("ofdefaults".equals(only)) {
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::ofDefaults));
        } else if ("ebe".equals(only)) { // built-in baked block entities: frames and frame times (BakedBlockEntitiesProbe189)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady,
                com.thelads.core.v1_8_9.feature.ebe.BakedBlockEntitiesProbe189::run));
        } else if ("headshot".equals(only)) { // Kill Banner headshots through the crosshair (Probe172KillStreak.headshots)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe172KillStreak.headshots());
        } else if ("hudlayout".equals(only)) { // 1.9.0 HUD layout across GUI scales, docked HUDs, the hotbar snap (HudLayoutProbe189)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady, HudLayoutProbe189::run));
        } else if ("skin".equals(only)) { // 1.9.1 Skin Customization and Skin changer: from the title screen, then from the pause menu (Probe191Skin)
            STEPS.clear();
            STEPS.add(CoreProbe::titleShown);
            STEPS.addAll(Probe191Skin.TITLE);
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::focusedWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe191Skin.WORLD);
        } else if ("kb191".equals(only)) { // 1.9.1 kill banners: Hypixel lines, a real kill, the sound sliders, loudness (Probe191KillBanner)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady, Probe191KillBanner::run));
        } else if ("items".equals(only)) { // 1.9.1 items: block entity items drawn everywhere, hotbar keys in inventories, tooltips fitted (ItemsProbe189)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, ItemsProbe189::title, CoreProbe::quickWorld, CoreProbe::worldReady, ItemsProbe189::run));
        } else if ("sounds".equals(only)) { // 1.9.0 uploaded sounds: Combo Sounds and Kill Banner "Your sounds" (Probe190Sounds)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady, Probe190Sounds::run));
        } else if ("soundbreak".equals(only)) { // 1.9.10 block break and hit sounds followed while they play (SoundBreakProbe189)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady, SoundBreakProbe189::run));
        } else if ("parity1910".equals(only)) { // 1.9.10 StackRefill and SurvivalTweaks ports in the QA world (ParityProbe189)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady, ParityProbe189::run));
        } else if ("emote".equals(only)) { // 1.9.11 emotes: wheel, third and first person, stop by walking, another player (EmoteProbe189)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady,
                com.thelads.core.v1_8_9.cosmetics.anim.EmoteProbe189::run));
        } else if ("parity1911".equals(only)) { // 1.9.11 Jade, Minimap (Xaero) and Dynamic FPS on 1.8.9 (Probe1911Parity189)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady, Probe1911Parity189::run));
        } else if ("hypixel".equals(only)) { // 1.9.1 Hypixel modules and real ping in the QA world, counted as Hypixel (Probe191Hypixel)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady, Probe191Hypixel::run));
        } else if ("actionbar".equals(only)) { // 1.9.1 Action Bar module and kill banners above the action bar (Probe191ActionBar)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady, Probe191ActionBar::run));
        } else if ("world191".equals(only)) { // 1.9.1 Wavy Capes and TNT Timer (Probe191World)
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe191World.STEPS);
        } else if ("fixes".equals(only) || "dialogs".equals(only)) { // 1.9.1 fixes lane (Probe191Fixes); "fixes" ends out of the world
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady));
            STEPS.addAll("fixes".equals(only) ? Probe191Fixes.fixes() : Probe191Fixes.dialogs());
        } else if ("raised".equals(only) || "leave".equals(only)) {
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady));
            if ("raised".equals(only)) STEPS.addAll(RaisedDollProbe189.STEPS);
        } else if (chatHeadsOnly || f3FovOnly) {
            STEPS.subList(1, STEPS.size()).clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::focusTitle, CoreProbe::openWorld, CoreProbe::worldReady));
            if (chatHeadsOnly) STEPS.addAll(ChatHeadsProbe189.STEPS);
            if (f3FovOnly) STEPS.addAll(Probe170F3Fov.STEPS);
        } else {
            STEPS.addAll(Probe170Skin.STEPS);
            STEPS.addAll(Probe160s.STEPS);
            STEPS.addAll(HudProbe.STEPS);
            STEPS.addAll(Probe145.STEPS);
            STEPS.addAll(Probe150e.STEPS);
            STEPS.addAll(Probe170r.STEPS);
            STEPS.addAll(Probe150.STEPS);
            STEPS.addAll(ProbeServer170.STEPS);
            STEPS.addAll(Probe151.STEPS);
            STEPS.addAll(Probe160.STEPS);
            STEPS.addAll(Probe170Sprint.STEPS);
            STEPS.addAll(Probe170Hud.STEPS);
            STEPS.addAll(RaisedDollProbe189.STEPS);
            STEPS.addAll(MouseTweaksProbe189.STEPS);
            STEPS.addAll(LightsProbe189.STEPS);
            STEPS.addAll(ChatHeadsProbe189.STEPS);
            STEPS.addAll(Probe170F3Fov.STEPS);
            STEPS.addAll(Probe145.PACING);
        }
        if (!"fixes".equals(only) && !"ofdefaults".equals(only)) {
            STEPS.add(CoreProbe::leaveWorld);
            STEPS.add(CoreProbe::leftWorld);
        }
        }
    }
    private static int step, passed, delay, waited, barStep = -1, barWaits;
    private static boolean barHolding, barShotDone;
    private static boolean finished, pauseOnLostFocus;
    private static GuiScreen title, pause;
    private static LadsSettingsScreen189 menu;
    private CoreProbe() {}

    /** Every client tick (END); one step at a time, each after the previous one's screens had frames to render. */
    public static void tick() {
        String barShot = QaProgress.finalShot(); // QA progress bar: its final state, photographed a moment after the run is done
        if (barShot != null) { screenshot(Minecraft.getMinecraft(), barShot); barShotDone = true; LOG.info("Lads QA bar final state: lads-qa/screenshots/{}.png", barShot); }
        if (barHolding) { // the run is done; its END marker waits for the bar's final-state photo (the harness stops the game at the marker)
            if (barShotDone || ++barWaits > 100) { barHolding = false; end(); }
            return;
        }
        if (finished) return;
        if (delay > 0) { delay--; return; }
        try {
            // QA progress bar: one bar step per probe step (its label follows the last check it passed).
            if (barStep < 0) QaProgress.begin("1.8.9 " + (FOCUS != null ? "focus " + FOCUS : System.getProperty("thelads.verify189Only", System.getenv("LADS_VERIFY_189_ONLY")) != null
                ? System.getProperty("thelads.verify189Only", System.getenv("LADS_VERIFY_189_ONLY")) : "core probe"), STEPS.size());
            if (barStep != step) QaProgress.step("step " + (step + 1));
            barStep = step;
            if (!STEPS.get(step).run(Minecraft.getMinecraft())) {
                if (++waited > 2400) throw new IllegalStateException("step " + (step + 1) + " did not complete within 120 s");
                return;
            }
            waited = 0;
            if (++step < STEPS.size()) return;
            if (QaProgress.on() && QaProgress.running()) { QaProgress.done(); barHolding = true; return; }
            end();
        } catch (Throwable failure) {
            finish();
            QaProgress.result(QaProgress.Result.FAIL);
            QaProgress.done();
            LOG.error("Lads 1.8.9 core probe FAILED after {} checks", passed, failure);
        }
    }

    private static void end() {
        finish();
        QaProgress.done();
        LOG.info("Lads 1.8.9 core probe END: {} passed, 0 failed; {}", passed, FOCUS != null ? "focused on " + FOCUS
            : "menu key through GuiScreen.handleInput and runTick, pause-menu button, 1.8.9 bridge in a QA world, launcher catalog, "
            + "HUD through RenderGameOverlayEvent and the HUD editor");
    }

    private static void finish() {
        finished = true;
        HudProbe.stop();
        Probe172Ui.stop();
        ProbeCrosshairPreview189.stop();
        Probe151.stop();
        Probe160.stop();
        ChatHeadsProbe189.stop(Minecraft.getMinecraft());
        MouseTweaksProbe189.stop(Minecraft.getMinecraft());
        LightsProbe189.stop();
        Probe160s.stop();
        Probe170Skin.stop();
        Probe191Skin.stop();
        Probe170Sprint.stop();
        Probe170Hud.stop();
        Probe170ItemPhysics.stop();
        ProbeServer170.stop();
        RaisedDollProbe189.stop();
        Probe172Anim.stop();
        Probe172Keybinds.stop();
        ProbeParticleCore189.stop();
        if (Minecraft.getMinecraft().gameSettings != null && title != null) Minecraft.getMinecraft().gameSettings.pauseOnLostFocus = pauseOnLostFocus;
    }

    private static boolean titleShown(Minecraft mc) {
        return mc.currentScreen instanceof GuiMainMenu && after(40);
    }

    private static boolean ofDefaults(Minecraft mc) throws Exception {
        try {
            Class.forName("Config");
        } catch (ClassNotFoundException noOptiFine) { // Ionium (the opt-in renderer) switches OptiFine off
            LOG.info("Lads 1.8.9 core probe: OptiFine defaults skipped: OptiFine is not loaded");
            return true;
        }
        Probe192OfDefaults.run(mc);
        return true;
    }

    /** GUI scales the title layout is checked at in this window: 1, 2, 3, 4 and Auto (0), the QA run's own scale last. */
    private static boolean moreOpened;
    private static List<Integer> titleScales;
    private static int titleScale = -1;

    /**
     * The Lads title screen (LadsTitleScreen189): its main buttons laid out by TitleScreenTheme (Forge's Mods and Screenshots
     * among them), Switch beside the account name, the fullscreen toggle top right, none overlapping and all on the screen at
     * every GUI scale, and a click on More.
     */
    private static boolean ladsTitle(Minecraft mc) throws Exception {
        if (essentialModalOpen()) { // the pictures and the More click below need the title screen, not Essential's popup over it
            tap(Keyboard.KEY_ESCAPE, (char) 27); // Essential's modal takes the key (its own Escape handling closes it) before the screen sees it
            return retry(5);
        }
        if (titleShot) { // Essential's previews of the shots (they grow with the window) take clicks: wait them out before More
            if (System.nanoTime() - lastShot < QUIET_NANOS) return retry(10);
            GuiButton more = LadsTitleScreen189.INSTANCE.moreButton();
            click(more.xPosition + more.width / 2, more.yPosition + more.height / 2);
            return after(10);
        }
        if (titleScales == null) {
            titleScales = new java.util.ArrayList<>(Arrays.asList(1, 2, 3, 4, 0));
            titleScales.remove(Integer.valueOf(mc.gameSettings.guiScale));
            titleScales.add(mc.gameSettings.guiScale);
        } else if (!titleSettling) checkTitleLayout(mc, titleScales.get(titleScale));
        if (!titleSettling && ++titleScale < titleScales.size()) {
            guiScale(mc, titleScales.get(titleScale));
            return retry(5); // frames lay the buttons out (and place Switch) at the new scale
        }
        titleSettling = true;
        if (System.nanoTime() - lastShot < QUIET_NANOS) return retry(10); // the picture below shows the title without the earlier shots' previews
        screenshot(mc, "c1-title");
        titleShot = true;
        return retry(10);
    }

    private static boolean titleSettling, titleShot;
    private static final long QUIET_NANOS = 12_000_000_000L;

    /** QA: the last shots' previews (Essential's, top right; they grow with the window) may still cover the top-right buttons. */
    static boolean previewsShowing() { return System.nanoTime() - lastShot < QUIET_NANOS; }

    /**
     * QA only: the sandbox's offline account makes Essential open "Account authentication failed" over the title screen, and that
     * popup takes every click. False when Essential is not loaded or its overlay cannot be read.
     */
    static boolean essentialModalOpen() {
        try {
            Class<?> overlays = Class.forName("gg.essential.gui.overlay.OverlayManagerImpl");
            return (Boolean) overlays.getMethod("hasOpenModals").invoke(overlays.getField("INSTANCE").get(null));
        } catch (ReflectiveOperationException | LinkageError absent) {
            return false;
        }
    }

    private static void guiScale(Minecraft mc, int scale) {
        mc.gameSettings.guiScale = scale;
        Screenshots189.clearPreview(); // the last shot's preview would cover the top-right buttons
        net.minecraft.client.gui.ScaledResolution scaled = new net.minecraft.client.gui.ScaledResolution(mc);
        mc.currentScreen.setWorldAndResolution(mc, scaled.getScaledWidth(), scaled.getScaledHeight());
    }

    private static void checkTitleLayout(Minecraft mc, int scale) {
        LadsTitleScreen189 lads = LadsTitleScreen189.INSTANCE;
        int w = mc.currentScreen.width, h = mc.currentScreen.height;
        String at = " at GUI scale " + (scale == 0 ? "Auto" : String.valueOf(scale)) + " (" + w + "x" + h + " in a " + mc.displayWidth + "x" + mc.displayHeight + " window)";
        screenshot(mc, "c1-title-gui" + scale); // before the checks: a failure keeps its picture
        check(lads.screen() != null && lads.screen() == mc.currentScreen, "the TitleScreen module put the Lads layout on the title screen" + at);
        List<String> labels = new java.util.ArrayList<>();
        for (GuiButton button : lads.mainButtons()) labels.add(button.displayString);
        List<String> expected = new java.util.ArrayList<>(Arrays.asList(I18n.format("menu.singleplayer"), I18n.format("menu.multiplayer"),
            "Lads Mods", I18n.format("fml.menu.mods"), "Cosmetics", I18n.format("menu.options")));
        if (com.thelads.core.v1_8_9.feature.Screenshots189.active()) expected.add("Screenshots");
        if (Loader.isModLoaded("replaymod")) expected.add("Replays");
        expected.addAll(Arrays.asList("More...", I18n.format("menu.quit"), "Switch Versions"));
        check(labels.equals(expected), "the main title actions in order " + labels + at);
        List<String> problems = new java.util.ArrayList<>();
        List<GuiButton> placed = lads.placedButtons();
        for (int i = 0; i < placed.size(); i++) {
            GuiButton button = placed.get(i);
            if (!button.visible || button.width < 8 || button.height < 8 || button.xPosition < 0 || button.yPosition < 0
                || button.xPosition + button.width > w || button.yPosition + button.height > h) problems.add(rect(button) + " off the screen");
            for (GuiButton other : placed.subList(i + 1, placed.size()))
                if (button.xPosition < other.xPosition + other.width && other.xPosition < button.xPosition + button.width
                    && button.yPosition < other.yPosition + other.height && other.yPosition < button.yPosition + button.height)
                    problems.add(rect(button) + " overlaps " + rect(other));
        }
        check(problems.isEmpty(), placed.size() + " Lads title buttons (main, Switch, fullscreen, language) on the screen without overlaps" + at + " " + problems);
        GuiButton switchButton = lads.switchButton(), fullscreen = lads.fullscreenButton();
        check("Switch".equals(switchButton.displayString) && switchButton.xPosition > 16 && switchButton.yPosition > h * 3 / 4
            && fullscreen.xPosition > w / 2 && fullscreen.yPosition < h / 8 && fullscreen.displayString.matches("Fullscreen|Windowed"),
            "Switch sits after the account name at the bottom left " + rect(switchButton) + ", fullscreen top right " + rect(fullscreen) + at);
    }

    private static String rect(GuiButton button) {
        return "'" + button.displayString + "' " + button.xPosition + "," + button.yPosition + " " + button.width + "x" + button.height;
    }

    private static int versionPhase;

    /** Switch Versions on the title: its list photographed, the first version picked and confirmed (Relaunch Now writes the launcher's request and quits). */
    private static boolean versionSwitch(Minecraft mc) throws Exception {
        if (versionPhase == 0) {
            if (!(mc.currentScreen instanceof GuiMainMenu)) return retry(10);
            GuiButton button = LadsTitleScreen189.INSTANCE.switchVersionButton();
            click(button.xPosition + button.width / 2, button.yPosition + button.height / 2);
            versionPhase = 1;
            return retry(10);
        }
        if (!(mc.currentScreen instanceof VersionSwitchScreen189)) return retry(10);
        VersionSwitchScreen189 screen = (VersionSwitchScreen189) mc.currentScreen;
        GuiButton button;
        if (versionPhase == 1) {
            check(screen.button("Lads Client ") != null, "a click on Switch Versions opened the list");
            screenshot(mc, "c1-version-switch");
            button = screen.button("Lads Client ");
            versionPhase = 2;
        } else if (versionPhase == 2) {
            screenshot(mc, "c1-version-switch-confirm");
            button = screen.button("Relaunch Now");
            check(button != null, "the confirmation offers Relaunch Now");
            versionPhase = 3;
        } else return true;
        click(button.xPosition + button.width / 2, button.yPosition + button.height / 2);
        return retry(10);
    }

    /** More, then its Language: the title screen's own Language button pressed from More (GuiLanguage, a list on the Lads backdrop). */
    private static boolean titleMore(Minecraft mc) throws Exception {
        if (mc.currentScreen instanceof TitleExtrasScreen189) {
            moreOpened = true;
            TitleExtrasScreen189 more = (TitleExtrasScreen189) mc.currentScreen;
            check(more.labels().containsAll(Arrays.asList("Language", "Realms")) && !more.labels().contains("Mods")
                    && !more.labels().contains("Accounts") && !more.labels().contains("Screenshots"),
                "a click on More opens the rest: Language and Realms; Mods, Screenshots and Accounts are on the title screen " + more.labels());
            screenshot(mc, "c1-title-more");
            GuiButton language = more.button("Language");
            click(language.xPosition + language.width / 2, language.yPosition + language.height / 2);
            return retry(10);
        }
        if (mc.currentScreen instanceof GuiLanguage) {
            check(LadsTitleScreen189.INSTANCE.screen() != null, "Language on More pressed the title screen's own Language button");
            screenshot(mc, "c1-title-language");
            // Not a synthetic click: GuiSlot selects the entry under the real cursor on any click (a language switch).
            mc.displayGuiScreen(LadsTitleScreen189.INSTANCE.screen());
            return retry(10);
        }
        check(moreOpened, "the click on More opened the More screen (screen now " + mc.currentScreen + ")");
        check(mc.currentScreen instanceof GuiMainMenu && "en_US".equals(mc.gameSettings.language), "back on the title screen, language unchanged");
        return after(10);
    }

    private static boolean title(Minecraft mc) throws Exception {
        if (!(mc.currentScreen instanceof GuiMainMenu)) {
            // The sandbox window opens on the user's desktop; a stray click can leave the title screen before QA starts.
            LOG.warn("Lads 1.8.9 core probe: {} replaced the title screen before the checks; reopening it", mc.currentScreen);
            mc.displayGuiScreen(new GuiMainMenu());
            delay = 20;
            return false;
        }
        title = mc.currentScreen;
        // An unfocused sandbox window must not open the pause menu by itself during the world steps.
        pauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
        mc.gameSettings.pauseOnLostFocus = false;
        org.slf4j.Logger slf4j = org.slf4j.LoggerFactory.getLogger("TheLadsCore");
        check(slf4j.getClass().getName().startsWith(Log4jServiceProvider.class.getName()), "common's SLF4J logging goes to Minecraft's log4j");
        slf4j.info("Lads 1.8.9 core probe: this line came through the bundled SLF4J");
        check(LadsGameBridge.get() instanceof VanillaGameBridge189 && !LadsGameBridge.get().isIngame(), "the 1.8.9 game bridge is active, no world yet");
        tap(Keyboard.KEY_RSHIFT, '\0');
        return true;
    }

    /** The focused self-test's start: no synthetic input, so the window needs no focus and Essential stays loaded. */
    private static boolean focused(Minecraft mc) {
        title = mc.currentScreen;
        pauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
        mc.gameSettings.pauseOnLostFocus = false;
        LOG.info("Lads 1.8.9 core probe: focused on {}", FOCUS);
        return after(20);
    }

    private static boolean menuAtTitle(Minecraft mc) {
        check(mc.currentScreen instanceof LadsSettingsScreen189 && ((LadsSettingsScreen189) mc.currentScreen).parent() == title,
            "Right Shift through GuiScreen.handleInput opens the Lads menu over the title screen");
        menu = (LadsSettingsScreen189) mc.currentScreen;
        return after(10);
    }

    private static boolean menuRendered(Minecraft mc) throws Exception {
        LadsSettingsScreen ui = menu.ui();
        LadsSettingsScreen.Rect search = ui.controlBounds("search");
        check(mc.currentScreen == menu && search != null, "the Lads menu rendered its controls through the 1.8.9 graphics adapter");
        boolean onlyBuiltIn = true;
        for (String name : ui.visibleModuleNames()) onlyBuiltIn &= ModuleSupport.isBuiltIn(name);
        check(onlyBuiltIn, "the menu lists only modules built in on 1.8.9 (" + ui.visibleModuleNames().size() + ")");
        screenshot(mc, "c1-lads-menu-title");
        click(search.x() + search.width() / 2, search.y() + search.height() / 2);
        return after(2);
    }

    private static boolean searchClicked(Minecraft mc) throws Exception {
        check(menu.ui().isEditingText(), "a mouse click through GuiScreen.handleMouseInput focuses the search field");
        tap(Keyboard.KEY_Z, 'z');
        tap(Keyboard.KEY_O, 'o');
        tap(Keyboard.KEY_O, 'o');
        tap(Keyboard.KEY_M, 'm');
        return after(2);
    }

    private static boolean typed(Minecraft mc) throws Exception {
        check("zoom".equals(menu.ui().getSearchQuery()), "typed characters reach the search field (" + menu.ui().getSearchQuery() + ")");
        tap(Keyboard.KEY_BACK, '\b');
        return after(2);
    }

    private static boolean erased(Minecraft mc) throws Exception {
        check("zoo".equals(menu.ui().getSearchQuery()), "Backspace (LWJGL 14 -> GLFW 259) edits the search");
        tap(Keyboard.KEY_ESCAPE, (char) 27);
        return after(2);
    }

    private static boolean editingLeft(Minecraft mc) throws Exception {
        check(!menu.ui().isEditingText() && mc.currentScreen == menu, "Escape leaves the search field and keeps the menu open");
        tap(Keyboard.KEY_RSHIFT, '\0');
        return after(2);
    }

    private static boolean closedToTitle(Minecraft mc) throws Exception {
        check(mc.currentScreen == title, "a second Right Shift closes the Lads menu back to its title-screen parent");
        return openWorld(mc);
    }

    /** LADS_VERIFY_189_ONLY: straight from the title screen into the QA world. */
    private static boolean focusedWorld(Minecraft mc) throws Exception {
        if (!(mc.currentScreen instanceof GuiMainMenu)) return retry(20);
        title = mc.currentScreen;
        pauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
        mc.gameSettings.pauseOnLostFocus = false;
        return openWorld(mc);
    }

    /** The focused run's title step: the title screen as found, kept from pausing the unfocused QA window. */
    private static boolean focusTitle(Minecraft mc) {
        title = mc.currentScreen;
        pauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
        mc.gameSettings.pauseOnLostFocus = false;
        return true;
    }

    private static boolean openWorld(Minecraft mc) throws Exception {
        Path game = mc.mcDataDir.toPath().toRealPath(), saves = Files.createDirectories(game.resolve("saves")).toRealPath();
        // Worlds are shared since 1.4.8: the saves link leads to the sandbox's shared folder, never a real one.
        check(inVerificationSandbox(game) && inVerificationSandbox(saves) && com.thelads.core.shared.SharedContentPaths.redirectEnabled()
            && saves.equals(com.thelads.core.shared.SharedContentPaths.savesDir().toRealPath()), "the QA world goes into the sandbox's shared saves (" + saves + ")");
        LOG.info("Lads 1.8.9 core probe: opening the QA world '{}' in {}", WORLD, saves);
        mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(0L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT));
        return true;
    }

    /** The --only path: the title step's unfocused-window guard, the sandbox check and the QA world. */
    private static boolean quickWorld(Minecraft mc) throws Exception {
        title = mc.currentScreen;
        pauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
        mc.gameSettings.pauseOnLostFocus = false;
        Path game = mc.mcDataDir.toPath().toRealPath(), saves = Files.createDirectories(game.resolve("saves")).toRealPath();
        check(inVerificationSandbox(game) && inVerificationSandbox(saves), "the QA world goes into the sandbox (" + saves + ")");
        mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(0L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT));
        return true;
    }

    private static boolean worldReady(Minecraft mc) {
        // A run that ended with the QA player dead reopens on the death screen: respawn and go on.
        if (mc.theWorld != null && mc.thePlayer != null && mc.currentScreen instanceof net.minecraft.client.gui.GuiGameOver) {
            mc.thePlayer.respawnPlayer();
            mc.displayGuiScreen(null);
            return false;
        }
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return false;
        // The QA world starts every run the same: no armour left over from an interrupted run.
        final java.util.UUID id = mc.thePlayer.getUniqueID();
        mc.getIntegratedServer().addScheduledTask(() -> {
            net.minecraft.entity.player.EntityPlayerMP player = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id);
            if (player != null) java.util.Arrays.fill(player.inventory.armorInventory, null);
        });
        return after(40);
    }

    /** Fast fancy clouds (1.9.13): with Ionium, LadsClouds189 draws the fancy clouds of the frames so far and never fell back. */
    private static boolean fastClouds(Minecraft mc) {
        boolean ionium = CoreProbe.class.getClassLoader().getResource("org/taumc/ionium/launch/IoniumTweaker.class") != null;
        if (!ionium || mc.gameSettings.shouldRenderClouds() != 2) {
            LOG.info("Lads 1.8.9 core probe: fast clouds skipped (Ionium {}, clouds {})", ionium, mc.gameSettings.shouldRenderClouds());
            return true;
        }
        check(com.thelads.core.v1_8_9.render.LadsClouds189.frames > 0 && !com.thelads.core.v1_8_9.render.LadsClouds189.failed()
            && com.thelads.core.v1_8_9.render.LadsClouds189.cloudPercent > 0 && com.thelads.core.v1_8_9.render.LadsClouds189.cloudPercent < 100,
            "fast clouds draw Ionium's fancy clouds (" + com.thelads.core.v1_8_9.render.LadsClouds189.frames + " frames, "
                + com.thelads.core.v1_8_9.render.LadsClouds189.cloudPercent + "% of the texture is cloud)");
        return true;
    }

    private static boolean bridgeInWorld(Minecraft mc) throws Exception {
        LadsGameBridge bridge = LadsGameBridge.get();
        check(bridge.isIngame() && bridge.hasPlayer(), "the bridge sees the QA world and its player");
        check(bridge.getPlayerX() == MathHelper.floor_double(mc.thePlayer.posX) && bridge.getPlayerY() == MathHelper.floor_double(mc.thePlayer.posY)
            && bridge.getPlayerZ() == MathHelper.floor_double(mc.thePlayer.posZ), "bridge coordinates are the player's block position");
        check("minecraft:overworld".equals(bridge.getDimensionId()) && !bridge.getBiomeName().isEmpty() && bridge.getBiomeId() == null,
            "dimension and biome (" + bridge.getBiomeName() + ", no namespaced id on 1.8.9)");
        check(bridge.getHealth() > 0 && bridge.getMaxHealth() == 20 && bridge.getFoodLevel() >= 0 && bridge.getFoodLevel() <= 20
            && bridge.getSaturation() >= 0 && bridge.getXpLevel() >= 0, "health, food, saturation (integrated server) and XP");
        check(Arrays.asList("north", "south", "east", "west").contains(bridge.getPlayerDirection()) && bridge.getYaw() >= 0 && bridge.getYaw() < 360
            && bridge.getGameTime().matches("\\d\\d:\\d\\d") && bridge.getFps() >= 0 && bridge.getPing() >= 0 && bridge.getSpeed() >= 0,
            "direction, yaw, time, FPS, ping and speed");
        check("Singleplayer".equals(bridge.getServerAddress()) && bridge.getArmor().isEmpty() && !bridge.getActiveResourcePacks().contains(null),
            "server address, armor and resource packs");
        LOG.info("Lads 1.8.9 core probe: bridge in the QA world: xyz {} {} {}, biome {}, {}, facing {} (yaw {}), time {}, day {}, health {}/{}, food {}, "
                + "saturation {}, fps {}, ping {}, speed {}, effects {}, packs {}, scoreboard {}", bridge.getPlayerX(), bridge.getPlayerY(),
            bridge.getPlayerZ(), bridge.getBiomeName(), bridge.getDimensionId(), bridge.getPlayerDirection(), bridge.getYaw(), bridge.getGameTime(),
            bridge.getDayCount(), bridge.getHealth(), bridge.getMaxHealth(), bridge.getFoodLevel(), bridge.getSaturation(), bridge.getFps(),
            bridge.getPing(), bridge.getSpeed(), bridge.getActivePotionEffects(), bridge.getActiveResourcePacks(), bridge.getScoreboard());
        mc.guiAchievement.displayAchievement(net.minecraft.stats.AchievementList.openInventory);
        return after(10);
    }

    private static boolean toastInWorld(Minecraft mc) throws Exception {
        screenshot(mc, "qa-1.8.9-toast-bar");
        tap(Keyboard.KEY_RSHIFT, '\0');
        return after(2);
    }

    private static boolean menuInWorld(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof LadsSettingsScreen189 && ((LadsSettingsScreen189) mc.currentScreen).parent() == null,
            "Right Shift in gameplay (runTick's keyboard loop) opens the Lads menu over the world");
        menu = (LadsSettingsScreen189) mc.currentScreen;
        return after(10);
    }

    /** The Installed mods view over the world: its entries in two columns, like the module cards (LadsSettingsScreen). */
    private static boolean installedMods(Minecraft mc) throws Exception {
        if (!menu.ui().isModsViewOpen()) {
            screenshot(mc, "c1-lads-menu-world");
            mc.guiAchievement.clearAchievements(); // the "Press E" hint would cover the menu's top-right buttons
            menu.ui().openMods();
            return retry(20);
        }
        check(mc.currentScreen == menu, "the Installed mods view opened in the Lads menu");
        screenshot(mc, "c1-installed-mods");
        return after(2);
    }

    private static boolean closedToGame(Minecraft mc) throws Exception {
        if (mc.currentScreen == menu) {
            tap(Keyboard.KEY_RSHIFT, '\0');
            return false;
        }
        check(mc.currentScreen == null, "Right Shift closes it back to gameplay");
        tap(Keyboard.KEY_ESCAPE, (char) 27);
        return after(2);
    }

    private static boolean pauseMenu(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof GuiIngameMenu, "Escape in the QA world opens the pause menu");
        pause = mc.currentScreen;
        GuiButton button = pause instanceof LadsPauseButton ? ((LadsPauseButton) pause).ladsButton() : null;
        check(button != null && "Lads Client".equals(button.displayString) && button.visible && button.enabled,
            "GuiIngameMenuMixin added the Lads Client button to the pause menu");
        mc.guiAchievement.clearAchievements(); // the "Press E" hint would cover the fullscreen toggle in the pause screenshot
        return after(10);
    }

    /** The grouped pause menu (PauseMenuLayout) and its Multiplayer button, which opens the server list over the world. */
    private static boolean pauseMultiplayer(Minecraft mc) throws Exception {
        GuiButton lads = ((LadsPauseButton) pause).ladsButton(), multiplayer = ((LadsPauseButton) pause).ladsMultiplayerButton();
        check(mc.currentScreen == pause && multiplayer != null && multiplayer.visible && multiplayer.width == lads.width
            && multiplayer.yPosition > lads.yPosition && multiplayer.xPosition < lads.xPosition && lads.yPosition + lads.height <= pause.height,
            "the pause menu is in groups: Options | Lads Client, then Multiplayer on the next row");
        Probe191Fixes.localServersOnly(); // the server list it opens pings every entry
        click(multiplayer.xPosition + multiplayer.width / 2, multiplayer.yPosition + multiplayer.height / 2);
        return after(10);
    }

    private static boolean multiplayerList(Minecraft mc) throws Exception {
        if (mc.currentScreen instanceof net.minecraft.client.gui.GuiMultiplayer) {
            check(mc.theWorld != null, "Multiplayer on the pause menu opens the server list over the QA world, which stays open");
            screenshot(mc, "c1-pause-multiplayer");
            click(mc.currentScreen.width / 2 + 4 + 76 + 75 / 2, mc.currentScreen.height - 28 + 10); // GuiMultiplayer's Cancel (createButtons)
            return retry(10);
        }
        check(mc.currentScreen == pause && mc.theWorld != null, "Cancel returns to the pause menu, still in the world");
        return after(2);
    }

    private static boolean pauseClicked(Minecraft mc) throws Exception {
        check(mc.currentScreen == pause, "the pause menu is still open");
        // Essential's previews of the shots so far cover the top right (the fullscreen toggle) for a few seconds.
        if (System.nanoTime() - lastShot < QUIET_NANOS) return retry(10);
        screenshot(mc, "c1-pause-menu");
        GuiButton button = ((LadsPauseButton) pause).ladsButton();
        click(button.xPosition + button.width / 2, button.yPosition + button.height / 2);
        return after(2);
    }

    private static boolean menuFromPause(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof LadsSettingsScreen189 && ((LadsSettingsScreen189) mc.currentScreen).parent() == pause,
            "a click on Lads Client (GuiScreen.mouseClicked -> actionPerformed) opens the Lads menu over the pause menu");
        menu = (LadsSettingsScreen189) mc.currentScreen;
        return after(10);
    }

    private static boolean closedToPause(Minecraft mc) throws Exception {
        if (mc.currentScreen == menu) {
            screenshot(mc, "c1-lads-menu-pause");
            tap(Keyboard.KEY_RSHIFT, '\0');
            return false;
        }
        check(mc.currentScreen == pause, "Right Shift closes it back to its pause-menu parent");
        tap(Keyboard.KEY_RSHIFT, '\0');
        return after(2);
    }

    private static boolean menuKeyAtPause(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof LadsSettingsScreen189 && ((LadsSettingsScreen189) mc.currentScreen).parent() == pause,
            "Right Shift on the pause menu opens the Lads menu over it");
        tap(Keyboard.KEY_RSHIFT, '\0');
        return after(2);
    }

    private static boolean catalog(Minecraft mc) throws Exception {
        check(mc.currentScreen == pause, "and closes it back to the pause menu");
        Path file = ClientPaths.getBaseDir().resolve(CoreCatalogExporter.FILE_NAME);
        check(Files.isRegularFile(file), "the launcher catalog was written to " + file);
        JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
        String core = Loader.instance().getIndexedModList().get("theladscore").getVersion();
        check("1.8.9".equals(root.get("minecraftVersion").getAsString()) && core.equals(root.get("coreVersion").getAsString()),
            "the catalog names Minecraft 1.8.9 and Core " + core);
        JsonArray modules = root.getAsJsonArray("modules");
        int builtIn = 0, unavailable = 0, pending = 0, external = 0;
        List<String> mismatched = new java.util.ArrayList<>(), builtInNames = new java.util.ArrayList<>();
        for (JsonElement element : modules) {
            JsonObject module = element.getAsJsonObject();
            String name = module.get("name").getAsString(), support = module.get("support").getAsString();
            if (!support.equals(ModuleSupport.support(name)) || module.get("toggleable").getAsBoolean() != ModuleSupport.isToggleable(name))
                mismatched.add(name + "=" + support);
            if (support.equals("builtIn")) { builtIn++; builtInNames.add(name); }
            else if (support.equals("unavailable")) unavailable++;
            else if (support.equals("pending")) pending++;
            else if (support.equals("external")) external++;
        }
        check(mismatched.isEmpty(), "every catalog row matches this game's registrations and switchability " + mismatched);
        java.util.Set<String> expected = new java.util.HashSet<>(Arrays.asList(NativeHud.MODULES));
        expected.addAll(Arrays.asList(com.thelads.core.v1_8_9.TheLadsCore189.GAMEPLAY_MODULES));
        // DynamicLights switches OptiFine's: unavailable while OptiFine is off (Ionium, the opt-in renderer).
        boolean optiFine = com.thelads.core.v1_8_9.feature.DynamicLights189.available();
        for (String[] module : com.thelads.core.v1_8_9.TheLadsCore189.LIMITED)
            if (optiFine || !module[0].equals("DynamicLights")) expected.add(module[0]);
        if (SkinLayers189.LOADED) expected.add("SkinLayers");
        expected.add(com.thelads.core.client.ParticleCore.MODULE); // ParticleCore189 (1.8.5)
        // 1.9.1: World189 (Wavy Capes, TNT Timer) and Hypixel189 (the Hypixel modules) register theirs as built in too.
        expected.addAll(Arrays.asList(com.thelads.core.modules.WavyCapesModule.NAME, com.thelads.core.modules.TntTimerModule.NAME,
            com.thelads.core.client.hypixel.HypixelMods.HYPIXEL_MODS));
        // 1.9.11: the Lads Jade panel (unless Waila is installed) and the Minimap switch over the pack's Xaero's Minimap.
        if (!Loader.isModLoaded("Waila")) expected.add(com.thelads.core.v1_8_9.feature.jade.Jade189.MODULE);
        if (Minimap189.integrated()) expected.add(Minimap189.NAME);
        java.util.Set<String> missing = new java.util.TreeSet<>(expected), extra = new java.util.TreeSet<>(builtInNames);
        missing.removeAll(builtInNames);
        extra.removeAll(expected);
        check(new java.util.HashSet<>(builtInNames).equals(expected) && builtInNames.size() == expected.size(),
            "exactly the HUD modules NativeHud draws and the native gameplay modules are built in " + builtInNames
            + (missing.isEmpty() && extra.isEmpty() ? "" : " (missing " + missing + ", not expected " + extra + ")"));
        check(modules.size() == ModuleManager.getInstance().getModules().size() && builtIn == expected.size() && unavailable == com.thelads.core.v1_8_9.TheLadsCore189.MOD_BACKED.length + (optiFine ? 5 : 6) /* + DynamicLights without OptiFine; DisableNarrator, Async, ShulkerBoxUtils, Voice Chat, Voice Chat Group */
            && pending == modules.size() - unavailable - builtIn - external, "catalog statuses: " + builtIn + " built in, " + unavailable + " unavailable, "
            + external + " external, " + pending + " pending");
        mc.displayGuiScreen(null); // Back to Game: the HUD checks run in gameplay
        return after(10);
    }

    private static volatile boolean serverBusy, raced;
    private static volatile int shutdownTick;
    private static IntegratedServer leaving;
    private static final List<java.util.concurrent.Future<?>> notRun = new java.util.ArrayList<>();
    private static long leftAfterMs;

    /**
     * Leaving while the world's server stops on its own (the regression check for the 1.7.0 freeze fix, IntegratedServerMixin).
     * loadWorld(null) queues "log every player out" for the server thread and waits for it (OptiFine only skips that task once
     * the server has stopped running). A server that stops right after that check, as when its owner's connection drops, leaves
     * its tick loop without running the task, and vanilla waited forever (Not Responding). Forced here on every run: a server
     * task keeps the server's task queue locked until this thread is blocked queuing its logout, then stops the server as a
     * dropped owner does (initiateShutdown on the server thread), lets the logout in and takes it back out of the queue, so it
     * never runs. Taking it out matters on a server that is behind: MinecraftServer.run runs the ticks it owes back to back
     * without looking at serverRunning, and the next of those would run the logout (the race lost, the fix never exercised:
     * the 1.8.5 QA runs failed that way under load). One server tick 300 ms late before the race makes every run a catch-up.
     */
    private static boolean leaveWorld(Minecraft mc) throws Exception {
        final IntegratedServer server = leaving = mc.getIntegratedServer();
        final Thread client = Thread.currentThread();
        final java.util.Queue<java.util.concurrent.FutureTask<?>> tasks = ReflectionHelper.getPrivateValue(net.minecraft.server.MinecraftServer.class,
            server, "futureTaskQueue", "field_175589_i");
        final java.util.concurrent.CountDownLatch late = new java.util.concurrent.CountDownLatch(1);
        server.addScheduledTask(() -> {
            try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            late.countDown();
        });
        check(late.await(10, java.util.concurrent.TimeUnit.SECONDS), "the QA world's server ran a tick 300 ms late (it is behind)");
        server.addScheduledTask(() -> {
            serverBusy = true;
            long end = System.nanoTime() + 10_000_000_000L;
            while (!(raced = queuingLogout(client)) && System.nanoTime() < end) LockSupport.parkNanos(1_000_000L);
            server.initiateShutdown();
            shutdownTick = server.getTickCounter();
            // This task runs under the queue's lock: waiting on it lets the blocked client queue its logout.
            synchronized (tasks) {
                end = System.nanoTime() + 10_000_000_000L;
                try {
                    while (tasks.isEmpty() && System.nanoTime() < end) tasks.wait(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                notRun.addAll(tasks);
                tasks.clear();
            }
        });
        long end = System.nanoTime() + 10_000_000_000L;
        while (!serverBusy && System.nanoTime() < end) LockSupport.parkNanos(1_000_000L);
        check(serverBusy, "the QA world's server is running a task (its task queue is locked)");
        long start = System.nanoTime();
        mc.loadWorld(null);
        leftAfterMs = (System.nanoTime() - start) / 1_000_000;
        mc.displayGuiScreen(new GuiMainMenu());
        return after(20);
    }

    /** The client thread blocked on the server's task queue inside IntegratedServer.initiateShutdown (queuing its logout). */
    private static boolean queuingLogout(Thread client) {
        if (client.getState() != Thread.State.BLOCKED) return false;
        for (StackTraceElement frame : client.getStackTrace()) if (frame.getClassName().equals(IntegratedServer.class.getName())) return true;
        return false;
    }

    private static boolean leftWorld(Minecraft mc) {
        boolean unrun = !notRun.isEmpty();
        for (java.util.concurrent.Future<?> task : notRun) unrun &= !task.isDone();
        LOG.info("Lads 1.8.9 core probe: leave race: client blocked queuing its logout {}, {} task(s) queued after the stop and never run {}, "
            + "{} server tick(s) after the stop", raced, notRun.size(), unrun, leaving.getTickCounter() - shutdownTick);
        check(raced && unrun, "the client queued its logout task just as the server stopped on its own, and the server never ran it (the freeze's race, forced)");
        check(leaving.isServerStopped() && mc.theWorld == null && mc.getIntegratedServer() == null && mc.currentScreen instanceof GuiMainMenu,
            "loadWorld(null) still returned (" + leftAfterMs + " ms) and the QA world closed back to the title screen");
        screenshot(mc, "c1-title-after-leave");
        return true;
    }

    private static final String NEWER = "Lads QA newer world", OLDER = "Lads QA 1_8_9 world";

    /** Shared worlds: a world a newer version saved asks for a backup before it opens; a 1.8.9 world opens directly. */
    private static boolean backupPrompt(Minecraft mc) throws Exception {
        File saves = WorldBackup189.savesDir(mc);
        world(saves, NEWER, true);
        world(saves, OLDER, false);
        check("26.3".equals(WorldBackup189.newerVersion(saves, NEWER)), "a world a newer version saved is recognised (DataVersion, version 26.3)");
        check(WorldBackup189.newerVersion(saves, OLDER) == null, "a 1.8.9 world needs no backup");
        check(!WorldBackup189.intercept(mc, OLDER, OLDER, null), "a 1.8.9 world opens without the prompt");
        java.util.concurrent.atomic.AtomicInteger copied = new java.util.concurrent.atomic.AtomicInteger();
        String copy = WorldBackup189.copy(saves, NEWER, copied);
        check(copy.equals(NEWER + WorldBackup189.SUFFIX) && new File(saves, copy + "/level.dat").isFile() && copied.get() >= 1,
            "the backup copies the world to '" + NEWER + " - 1.8.9'");
        mc.getSaveLoader().renameWorld(copy, NEWER + WorldBackup189.SUFFIX);
        check((NEWER + WorldBackup189.SUFFIX).equals(mc.getSaveLoader().getWorldInfo(copy).getWorldName()), "the copy is named '<name> - 1.8.9'");
        check(WorldBackup189.newerVersion(saves, NEWER) != null, "the original stays untouched");
        check(WorldBackup189.intercept(mc, NEWER, NEWER, null) && mc.currentScreen instanceof WorldBackup189.Prompt,
            "opening the newer world shows the backup prompt instead");
        return after(20);
    }

    private static boolean backupWarning(Minecraft mc) {
        check(mc.currentScreen instanceof WorldBackup189.Prompt, "the backup prompt is up");
        screenshot(mc, "w1-backup-prompt");
        mc.displayGuiScreen(new WorldBackup189.Warning(mc.currentScreen, title, NEWER, NEWER));
        return after(20);
    }

    private static boolean backupDone(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof WorldBackup189.Warning, "No shows the corruption warning");
        screenshot(mc, "w1-corruption-warning");
        File saves = WorldBackup189.savesDir(mc);
        for (String world : new String[] {NEWER, OLDER, NEWER + WorldBackup189.SUFFIX}) delete(new File(saves, world).toPath());
        mc.displayGuiScreen(new GuiMainMenu());
        return after(10);
    }

    private static void world(File saves, String name, boolean newer) throws Exception {
        net.minecraft.nbt.NBTTagCompound data = new net.minecraft.nbt.NBTTagCompound();
        data.setString("LevelName", name);
        data.setInteger("version", 19133);
        if (newer) {
            data.setInteger("DataVersion", 4671);
            net.minecraft.nbt.NBTTagCompound version = new net.minecraft.nbt.NBTTagCompound();
            version.setString("Name", "26.3");
            version.setInteger("Id", 4671);
            data.setTag("Version", version);
        }
        net.minecraft.nbt.NBTTagCompound root = new net.minecraft.nbt.NBTTagCompound();
        root.setTag("Data", data);
        File folder = new File(saves, name);
        check(folder.mkdirs() || folder.isDirectory(), "QA world folder " + folder);
        try (java.io.OutputStream out = new java.io.FileOutputStream(new File(folder, "level.dat"))) {
            net.minecraft.nbt.CompressedStreamTools.writeCompressed(root, out);
        }
    }

    /** Deletes a world folder the QA steps made, only inside the sandbox's saves. */
    static void deleteQaWorld(Minecraft mc, String name) throws java.io.IOException {
        Path saves = WorldBackup189.savesDir(mc).toPath().toRealPath();
        check(inVerificationSandbox(saves), "QA worlds are deleted only in the sandbox's saves (" + saves + ")");
        delete(saves.resolve(name));
    }

    private static void delete(Path folder) throws java.io.IOException {
        if (!Files.exists(folder)) return;
        try (java.util.stream.Stream<Path> walk = Files.walk(folder)) {
            for (Path path : (Iterable<Path>) walk.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(path);
        }
    }

    /** Only below &lt;repository&gt;/artifacts/verification, next to the repository's TheLadsCore/settings.gradle. */
    private static boolean inVerificationSandbox(Path game) {
        for (Path folder = game; folder != null && folder.getParent() != null && folder.getParent().getParent() != null; folder = folder.getParent())
            if (folder.getFileName().toString().equals("verification") && folder.getParent().getFileName().toString().equals("artifacts")
                && Files.isRegularFile(folder.getParent().getParent().resolve("TheLadsCore").resolve("settings.gradle"))) return true;
        return false;
    }

    private static long lastShot;

    static void screenshot(Minecraft mc, String name) {
        lastShot = System.nanoTime();
        File folder = new File(mc.mcDataDir, "lads-qa");
        new File(folder, "screenshots").mkdirs(); // ScreenShotHelper creates only the last folder
        ScreenShotHelper.saveScreenshot(folder, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    /**
     * A picture screenshot() saved, once its file is complete: Lads Screenshots writes it on its own thread, so right after the
     * call the file is missing or half written. Waits up to 10 s on the client thread (QA only).
     */
    static java.awt.image.BufferedImage readShot(Minecraft mc, String name) throws Exception {
        File file = new File(mc.mcDataDir, "lads-qa/screenshots/" + name + ".png");
        for (int attempt = 0; attempt < 100; attempt++) {
            java.awt.image.BufferedImage image = null;
            try { if (file.isFile()) image = javax.imageio.ImageIO.read(file); } catch (java.io.IOException partial) { /* still being written */ }
            if (image != null) return image;
            Thread.sleep(100);
        }
        throw new IllegalStateException("1.8.9 core QA: the screenshot " + file + " was not written in 10 s");
    }

    /** A key press and release, queued in LWJGL's keyboard buffer as Keyboard.poll() stores them. */
    static void tap(int key, char character) throws Exception {
        key(key, character, true);
        key(key, character, false);
    }

    /** One key event (a held key: press now, release later), queued as Keyboard.poll() stores it. */
    static void key(int key, char character, boolean down) throws Exception {
        if (PYLON) {
            pylonEvent("KeyboardSdl", "keycode", key, "state", (byte) (down ? 1 : 0), "character", (int) character, "nanos", System.nanoTime(), "repeat", false);
            return;
        }
        ByteBuffer events = queue(Keyboard.class);
        events.compact();
        events.putInt(key).put((byte) (down ? 1 : 0)).putInt(character).putLong(System.nanoTime()).put((byte) 0);
        events.flip();
    }

    /** One mouse-wheel event in gameplay (120 is one notch on Windows), queued as Mouse.poll() stores it. */
    static void wheel(int dwheel) throws Exception {
        if (PYLON) {
            pylonEvent("MouseSdl", "button", (byte) -1, "state", false, "x", (double) Mouse.getX(), "y", (double) Mouse.getY(), "wheelDelta", (double) dwheel, "nanos", System.nanoTime());
            return;
        }
        ByteBuffer events = queue(Mouse.class);
        events.compact();
        events.put((byte) -1).put((byte) 0).putInt(0).putInt(0).putInt(dwheel).putLong(System.nanoTime());
        events.flip();
    }

    /** A left click at a GUI position of the current screen. */
    static void click(int guiX, int guiY) throws Exception {
        mouse(0, true, guiX, guiY);
        mouse(0, false, guiX, guiY);
    }

    /**
     * One mouse event at a GUI position of the current screen (button -1: a move, which GuiScreen turns into mouseClickMove while
     * a button is held), queued in LWJGL's mouse buffer as Mouse.poll() stores it: absolute window pixels, y up. Without a screen
     * (gameplay) only the button matters.
     */
    static void mouse(int button, boolean down, int guiX, int guiY) throws Exception {
        event(button, down, guiX, guiY, 0);
    }

    /** Wheel notches (negative: towards the user, scrolling down), 120 each as Mouse.poll() stores them. */
    static void wheel(int guiX, int guiY, int notches) throws Exception {
        for (int i = 0; i < Math.abs(notches); i++) event(-1, false, guiX, guiY, notches > 0 ? 120 : -120);
    }

    private static void event(int button, boolean down, int guiX, int guiY, int wheel) throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        int x = screen == null ? 0 : (guiX * mc.displayWidth + mc.displayWidth / 2) / screen.width;
        int y = screen == null ? 0 : ((screen.height - 1 - guiY) * mc.displayHeight + mc.displayHeight / 2) / screen.height;
        if (PYLON) {
            pylonEvent("MouseSdl", "button", (byte) button, "state", down, "x", (double) x, "y", (double) y, "wheelDelta", (double) wheel, "nanos", System.nanoTime());
            return;
        }
        ByteBuffer events = queue(Mouse.class);
        events.compact();
        events.put((byte) button).put((byte) (down ? 1 : 0)).putInt(x).putInt(y).putInt(wheel).putLong(System.nanoTime());
        events.flip();
    }

    /** Pylon (LWJGL 3 + SDL3 in place of LWJGL 2) has no readBuffer: its own input queue takes the same events. */
    static final boolean PYLON = PylonWindow189.PRESENT;

    private static void pylonEvent(String device, Object... fields) throws Exception {
        Class<?> type = Class.forName("pl.tomgirl.pylon.window." + device);
        Field instance = type.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        Field events = type.getDeclaredField("events");
        events.setAccessible(true);
        Object queue = events.get(instance.get(null));
        java.lang.reflect.Method claim = queue.getClass().getDeclaredMethod("claim");
        claim.setAccessible(true);
        Object event = claim.invoke(queue);
        for (int i = 0; i < fields.length; i += 2) event.getClass().getField((String) fields[i]).set(event, fields[i + 1]);
    }

    private static ByteBuffer queue(Class<?> device) throws Exception {
        Field field = device.getDeclaredField("readBuffer");
        field.setAccessible(true);
        return (ByteBuffer) field.get(null);
    }

    /** Step done; the next one runs after this many ticks (each tick renders frames first). */
    static boolean after(int ticks) {
        delay = ticks;
        return true;
    }

    /** Step not done yet: run it again after this many ticks. */
    static boolean retry(int ticks) {
        delay = ticks;
        return false;
    }

    static void check(boolean result, String description) {
        QaProgress.label(description);
        if (!result) throw new IllegalStateException("1.8.9 core QA: " + description);
        passed++;
        LOG.info("Lads 1.8.9 core probe PASS {}: {}", passed, description);
    }
}
