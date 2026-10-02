package com.thelads.core.v1_21_11.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.modules.KillBannerModule;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-killbanner" from the harness's LADS_VERIFY_CAPTURE_KILLBANNER): fires each skin,
 * variant and kill count in the QA world and saves the completed game frame at a set moment after each kill.
 */
final class KillBannerCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    // style (1 Reaver, 2 Rogue), variant, kills, headshot, ms after the kill, duration (tenths of a second)
    private static final int[][] SHOTS = {
        {1, 0, 1, 1, 210, 50}, {1, 0, 1, 0, 520, 50}, {1, 0, 1, 1, 1300, 50}, {1, 0, 1, 0, 1250, 20}, {1, 0, 1, 0, 1750, 20},
        {1, 1, 2, 0, 1000, 50}, {1, 2, 3, 0, 1000, 50}, {1, 3, 4, 1, 1100, 50}, {1, 0, 5, 0, 3600, 60},
        {2, 0, 1, 1, 280, 50}, {2, 0, 1, 0, 520, 50}, {2, 0, 1, 1, 1300, 50}, {2, 0, 1, 0, 1900, 20},
        {2, 1, 2, 0, 1000, 50}, {2, 2, 3, 0, 1000, 50}, {2, 3, 4, 1, 1000, 50}, {2, 0, 5, 1, 3800, 60}};
    // Then the modules list and the settings picker: skin + variants, Custom, and Randomize's chosen pool.
    private static final String[] PICKERS = {"list", "reaver", "custom", "chosen"};
    private static int step = -1, saved;
    private static long due;
    private static boolean capturing, held, enabledBefore, soundBefore, headshotBefore;
    private static int styleBefore, reaverBefore, rogueBefore, visualBefore, soundStyleBefore, randomBefore;
    private static double durationBefore;
    private static long modifiedBefore;
    private KillBannerCapture() {}

    /** Each client tick of the auto-world run; starts once the world is ready and the request exists. */
    static void tick(Path game, boolean ready) {
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-killbanner");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads kill banner capture FAILED: request", failure); return; }
        KillBannerModule module = module();
        enabledBefore = module.isEnabled();
        modifiedBefore = module.getLastModified();
        soundBefore = module.sound.get();
        headshotBefore = module.headshotText.get();
        styleBefore = module.bannerStyle.getIndex();
        reaverBefore = module.reaverVariant.getIndex();
        rogueBefore = module.rogueVariant.getIndex();
        visualBefore = module.customVisual.getIndex();
        soundStyleBefore = module.customSound.getIndex();
        randomBefore = module.randomize.getIndex();
        durationBefore = module.duration.getValue();
        LOGGER.info("Lads kill banner capture BEGIN: {} frames, no kill statistic changed", SHOTS.length + PICKERS.length);
        step = 0;
        fire();
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame). */
    static void frame(RenderTarget target, Path game) {
        if (step < 0 || step >= SHOTS.length + PICKERS.length || capturing) return;
        // Another QA menu over the HUD hides the banner: hold this shot and fire it again once the screen is gone.
        boolean picker = step >= SHOTS.length;
        if (picker && !(Minecraft.getInstance().screen instanceof com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111)) return;
        if (!picker && Minecraft.getInstance().screen != null) { held = true; return; }
        if (held) { held = false; fire(); return; }
        if (System.nanoTime() < due) return;
        capturing = true;
        int[] s = picker ? null : SHOTS[step];
        String name = picker ? "killbanner-picker-" + PICKERS[step - SHOTS.length]
            : "killbanner-" + (s[0] == 1 ? "reaver" : "rogue") + "-v" + s[1] + "-k" + s[2] + (s[3] == 1 ? "-hs" : "") + "-" + s[4] + "ms";
        try {
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve(name + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); saved++; LOGGER.info("Lads kill banner frame {}", output); }
                catch (Exception failure) { LOGGER.error("Lads kill banner capture FAILED: {}", name, failure); }
                finally { image.close(); Minecraft.getInstance().execute(KillBannerCapture::next); }
            });
        } catch (Exception failure) {
            LOGGER.error("Lads kill banner capture FAILED: {}", name, failure);
            next();
        }
    }

    private static void next() {
        capturing = false;
        if (++step < SHOTS.length + PICKERS.length) { fire(); return; }
        Minecraft.getInstance().setScreen(null);
        KillBannerModule module = module();
        module.setEnabled(enabledBefore);
        module.sound.set(soundBefore);
        module.headshotText.set(headshotBefore);
        module.bannerStyle.setIndex(styleBefore);
        module.reaverVariant.setIndex(reaverBefore);
        module.rogueVariant.setIndex(rogueBefore);
        module.customVisual.setIndex(visualBefore);
        module.customSound.setIndex(soundStyleBefore);
        module.randomize.setIndex(randomBefore);
        module.duration.setValue(durationBefore);
        module.setLastModified(modifiedBefore);
        NativeKillBanner.timeline().clear();
        LOGGER.info("Lads kill banner capture END: {} passed, {} failed", saved, SHOTS.length + PICKERS.length - saved);
    }

    private static void fire() {
        KillBannerModule module = module();
        if (step >= SHOTS.length) {
            String picker = PICKERS[step - SHOTS.length];
            module.randomize.setIndex(picker.equals("chosen") ? KillBannerModule.RANDOM_CHOSEN : KillBannerModule.RANDOM_OFF);
            module.bannerStyle.setIndex(picker.equals("custom") ? KillBannerModule.CUSTOM : picker.equals("chosen") ? KillBannerModule.BASE : KillBannerModule.REAVER);
            module.customVisual.setIndex(KillBannerModule.ROGUE);
            module.customSound.setIndex(KillBannerModule.REAVER);
            com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111 screen = new com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111(null);
            Minecraft.getInstance().setScreen(screen);
            if (!picker.equals("list")) screen.openModule("KillBanner"); // "list": the modules list itself
            due = System.nanoTime() + 1_200_000_000L;
            return;
        }
        int[] s = SHOTS[step];
        module.setEnabled(true);
        module.sound.set(false);
        module.headshotText.set(true);
        module.bannerStyle.setIndex(s[0]);
        (s[0] == 1 ? module.reaverVariant : module.rogueVariant).setIndex(s[1]);
        module.duration.setValue(s[5] / 10.0);
        NativeKillBanner.bindCurrent();
        NativeKillBanner.timeline().clear(); // a fresh streak of exactly s[2] kills
        NativeKillBanner.trigger(s[2], false, s[3] == 1);
        due = System.nanoTime() + s[4] * 1_000_000L;
    }

    private static KillBannerModule module() { return (KillBannerModule) NativeQualityOfLife.module("KillBanner"); }
}
