package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.config.Option;
import com.thelads.core.modules.KillBannerModule;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-killbanner" from the harness's LADS_VERIFY_CAPTURE_KILLBANNER): checks every skin's
 * art and sounds, fires Base, Reaver, Rogue and one skin of each Kingdom Archives kind for every kill count, variants and
 * headshots in the QA world, and saves the completed game frame at a set moment after each kill; then the settings picker.
 */
final class KillBannerCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    /** One banner: skin, variant, kill count, headshot, ms after the kill, duration in tenths of a second. */
    private record Shot(KillBannerStyle style, int variant, int kills, boolean headshot, int ms, int tenths) {}
    private static final List<Shot> SHOTS = shots();
    // Then the modules list and the picker: Base, a skin with variants, a search, Custom, and Randomize's chosen pool.
    private static final String[] PICKERS = {"list", "base", "variants", "search", "custom", "chosen"};
    private static int step = -1, saved;
    private static long due;
    private static boolean capturing, held, enabledBefore;
    private static long modifiedBefore;
    private static final List<JsonElement> OPTIONS_BEFORE = new ArrayList<>();
    private KillBannerCapture() {}

    private static List<Shot> shots() {
        List<Shot> shots = new ArrayList<>();
        // Every kill count, settled (the strips' five-kill banners settle later).
        for (KillBannerStyle s : new KillBannerStyle[] {KillBannerStyle.DEFAULT, KillBannerStyle.REAVER, KillBannerStyle.ROGUE,
                KillBannerStyle.AEMONDIR, KillBannerStyle.CHAMPIONS2024, KillBannerStyle.PHASEGUARD})
            for (int k = 1; k <= 5; k++) shots.add(new Shot(s, 0, k, false, s.isAnimated() && k == 5 ? 3700 : 1000, 60));
        // The kill mark landing, a headshot, and the way out.
        shots.add(new Shot(KillBannerStyle.DEFAULT, 0, 1, true, 200, 50));
        shots.add(new Shot(KillBannerStyle.DEFAULT, 0, 1, true, 600, 50));
        shots.add(new Shot(KillBannerStyle.DEFAULT, 0, 1, false, 1850, 20));
        shots.add(new Shot(KillBannerStyle.AEMONDIR, 0, 1, true, 600, 50));
        shots.add(new Shot(KillBannerStyle.ROGUE, 3, 4, true, 1000, 50));
        // Variants.
        for (int v = 1; v <= 3; v++) {
            shots.add(new Shot(KillBannerStyle.AEMONDIR, v, 3, false, 1000, 50));
            shots.add(new Shot(KillBannerStyle.PHASEGUARD, v, 3, false, 1000, 50));
            shots.add(new Shot(KillBannerStyle.REAVER, v, 2, false, 1000, 50));
        }
        return shots;
    }

    /** Each client tick of the auto-world run; starts once the world is ready and the request exists. */
    static void tick(Path game, boolean ready) {
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-killbanner");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads kill banner capture FAILED: request", failure); return; }
        KillBannerModule module = module();
        enabledBefore = module.isEnabled();
        modifiedBefore = module.getLastModified();
        OPTIONS_BEFORE.clear();
        for (Option option : module.getOptions()) OPTIONS_BEFORE.add(option.save());
        try {
            LOGGER.info("Lads kill banner assets: {} checks passed ({} skins: strips decode, every kill count's sound registered with its sample)",
                NativeKillBannerProbe.assets(), KillBannerStyle.values().length);
        } catch (Throwable failure) {
            LOGGER.error("Lads kill banner capture FAILED: assets", failure);
        }
        LOGGER.info("Lads kill banner capture BEGIN: {} frames, no kill statistic changed", SHOTS.size() + PICKERS.length);
        step = 0;
        fire();
    }

    /** Started and not yet finished: another capture waits so the frames do not mix. */
    static boolean busy() { return step >= 0 && step < SHOTS.size() + PICKERS.length; }

    /** Each completed game frame (NativeWorldVerification.renderedFrame). */
    static void frame(RenderTarget target, Path game) {
        if (step < 0 || step >= SHOTS.size() + PICKERS.length || capturing) return;
        // Another QA menu over the HUD hides the banner: hold this shot and fire it again once the screen is gone.
        boolean picker = step >= SHOTS.size();
        if (picker && !(Minecraft.getInstance().gui.screen() instanceof com.thelads.core.v26_2.gui.LadsSettingsScreen26)) return;
        if (!picker && Minecraft.getInstance().gui.screen() != null) { held = true; return; }
        if (held) { held = false; fire(); return; }
        if (System.nanoTime() < due) return;
        capturing = true;
        Shot s = picker ? null : SHOTS.get(step);
        String name = picker ? "killbanner-picker-" + PICKERS[step - SHOTS.size()]
            : "killbanner-" + s.style().id + "-v" + s.variant() + "-k" + s.kills() + (s.headshot() ? "-hs" : "") + "-" + s.ms() + "ms";
        try {
            Path output = game.resolve("screenshots").resolve(name + ".png");
            Files.createDirectories(output.getParent());
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
        if (++step < SHOTS.size() + PICKERS.length) { fire(); return; }
        Minecraft.getInstance().gui.setScreen(null);
        KillBannerModule module = module();
        for (int i = 0; i < OPTIONS_BEFORE.size(); i++) module.getOptions().get(i).load(OPTIONS_BEFORE.get(i));
        module.setEnabled(enabledBefore);
        module.setLastModified(modifiedBefore);
        NativeKillBanner.timeline().clear();
        LOGGER.info("Lads kill banner capture END: {} passed, {} failed", saved, SHOTS.size() + PICKERS.length - saved);
    }

    private static void fire() {
        KillBannerModule module = module();
        if (step >= SHOTS.size()) {
            String picker = PICKERS[step - SHOTS.size()];
            int aemondir = KillBannerModule.styleIndexOf(KillBannerStyle.AEMONDIR);
            module.randomize.setIndex(picker.equals("chosen") ? KillBannerModule.RANDOM_CHOSEN : KillBannerModule.RANDOM_OFF);
            module.bannerStyle.setIndex(switch (picker) {
                case "custom" -> KillBannerModule.CUSTOM;
                case "variants", "search" -> aemondir;
                case "base", "chosen" -> KillBannerModule.BASE;
                default -> KillBannerModule.REAVER;
            });
            module.setVariant(KillBannerStyle.AEMONDIR, 2);
            module.customVisual.setIndex(KillBannerStyle.AEMONDIR.ordinal());
            module.customSound.setIndex(KillBannerModule.REAVER);
            com.thelads.core.v26_2.gui.LadsSettingsScreen26 screen = new com.thelads.core.v26_2.gui.LadsSettingsScreen26(null);
            Minecraft.getInstance().gui.setScreen(screen);
            if (!picker.equals("list")) screen.openModule("KillBanner"); // "list": the modules list itself
            screen.searchKillBanners(picker.equals("search") ? "phase" : picker.equals("chosen") ? "rogue" : "");
            due = System.nanoTime() + 1_200_000_000L;
            return;
        }
        Shot s = SHOTS.get(step);
        module.setEnabled(true);
        module.sound.set(true); // the real play path (the QA game is muted): an unknown sound event would be logged
        module.headshotText.set(true);
        module.randomize.setIndex(KillBannerModule.RANDOM_OFF);
        module.bannerStyle.setIndex(KillBannerModule.styleIndexOf(s.style()));
        module.setVariant(s.style(), s.variant());
        module.duration.setValue(s.tenths() / 10.0);
        NativeKillBanner.bindCurrent();
        NativeKillBanner.timeline().clear(); // a fresh streak of exactly s.kills() kills
        NativeKillBanner.trigger(s.kills(), false, s.headshot());
        due = System.nanoTime() + s.ms() * 1_000_000L;
    }

    private static KillBannerModule module() { return (KillBannerModule) NativeQualityOfLife.module("KillBanner"); }
}
