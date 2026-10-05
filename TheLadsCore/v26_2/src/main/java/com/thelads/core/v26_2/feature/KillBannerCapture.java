package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.config.Option;
import com.thelads.core.modules.KillBannerModule;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-killbanner" from the harness's LADS_VERIFY_CAPTURE_KILLBANNER). First banners held at
 * set frames after the kill (KillBannerTimeline.freeze) for 1, 3 and 5 kills of Base, Reaver, Rogue and Kingdom Archives skins
 * of every kind, saved cropped to the banner as screenshots/kb172/kb-&lt;skin&gt;-v&lt;variant&gt;-k&lt;kills&gt;-f&lt;frame&gt;.png; then the
 * frame times around real kills (a random skin whose art is not loaded yet, the same again, the chosen skin, Reaver's 4 kills),
 * when the world's other QA is done; then the settings picker and its playing preview; last, every skin's art and sounds.
 */
final class KillBannerCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    /** One banner held at a frame (60 fps) after its kill, shown for {@code seconds}. */
    private record Shot(KillBannerStyle style, int variant, int kills, boolean headshot, int frame, double seconds) {}
    /** Frame times around a real kill of this skin; {@code chosen}: the module's choice (its art kept loaded), else a random pick. */
    private record Timing(String name, KillBannerStyle style, int kills, boolean chosen) {}
    private static final List<Timing> TIMINGS = List.of(
        new Timing("random-cold-kuronami-k3", KillBannerStyle.KURONAMI, 3, false),
        new Timing("random-again-kuronami-k3", KillBannerStyle.KURONAMI, 3, false),
        new Timing("chosen-glitchpop-k1", KillBannerStyle.GLITCHPOP, 1, true),
        new Timing("chosen-reaver-k4", KillBannerStyle.REAVER, 4, true), // its 4-kill frames: read before the kill (chosen)
        new Timing("chosen-champions2025-k2", KillBannerStyle.CHAMPIONS2025, 2, true));
    private static final List<Shot> SHOTS = shots();
    // Then the modules list and the picker: Base, a skin with variants, a search, Custom, Randomize's chosen pool, and the
    // preview playing (Oni three times, a moment apart, and Rogue).
    private static final String[] PICKERS = {"list", "base", "variants", "search", "custom", "chosen", "preview-oni-1", "preview-oni-2",
        "preview-oni-3", "preview-rogue"};
    private static final int BEFORE = 30, AFTER = 120;
    private static final long[] TIMES = new long[BEFORE + AFTER];
    private static int step = -1, saved, recorded, waitFrames;
    private static long due, lastFrame;
    private static boolean capturing, held, enabledBefore;
    private static long modifiedBefore;
    private static final List<JsonElement> OPTIONS_BEFORE = new ArrayList<>();
    private KillBannerCapture() {}

    private static List<Shot> shots() {
        List<Shot> shots = new ArrayList<>();
        Object[][] skins = {{KillBannerStyle.DEFAULT, 0}, {KillBannerStyle.REAVER, 0}, {KillBannerStyle.ROGUE, 0}, {KillBannerStyle.AEMONDIR, 0},
            {KillBannerStyle.CHAMPIONS2024, 0}, {KillBannerStyle.PHASEGUARD, 1}, {KillBannerStyle.ONI, 0}, {KillBannerStyle.VCT, 0},
            {KillBannerStyle.BOLT, 0}, {KillBannerStyle.GLITCHPOP, 0}, {KillBannerStyle.XEROFANG, 2}};
        for (Object[] skin : skins)
            for (int kills : new int[] {1, 3, 5}) sequence(shots, (KillBannerStyle) skin[0], (int) skin[1], kills);
        sequence(shots, KillBannerStyle.AEMONDIR, 2, 3);
        shots.add(new Shot(KillBannerStyle.AEMONDIR, 0, 1, true, 30, 3));
        shots.add(new Shot(KillBannerStyle.REAVER, 0, 1, true, 30, 3));
        shots.add(new Shot(KillBannerStyle.CHAMPIONS2024, 0, 1, true, 30, 3));
        return shots;
    }

    /** The banner's frames: its opening, (an ace's turn), settled, and three moments of its way out. */
    private static void sequence(List<Shot> shots, KillBannerStyle style, int variant, int kills) {
        double seconds = kills == 5 ? 5 : 3;
        int total = (int) Math.round(seconds * 60), intro, exit;
        if (style.isAnimated()) {
            var strip = style.strip(kills);
            intro = strip.introEnd + 1;
            exit = (int) Math.round(KillBannerPlayer.minimumSeconds(strip) * 60) - intro;
        } else {
            intro = com.thelads.core.client.killbanner.KillBannerTemplate.of(kills).introEnd + 1;
            exit = com.thelads.core.client.killbanner.KillBannerTemplate.of(kills).exit;
        }
        List<Integer> frames = new ArrayList<>(Arrays.asList(2, 5, 9, 12, 16, 22, 30, 40, 52, 66, 80));
        if (kills == 5) frames.addAll(Arrays.asList(110, 140, 160, 185, 200));
        frames.add(intro + 10);
        for (float at : new float[] {.2f, .5f, .8f}) frames.add(total - exit + Math.round(exit * at));
        for (int frame : frames) shots.add(new Shot(style, variant, kills, false, frame, seconds));
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
        LOGGER.info("Lads kill banner capture BEGIN: {} frame-time runs, {} frames, no kill statistic changed", TIMINGS.size(), SHOTS.size() + PICKERS.length);
        step = 0;
        fire();
    }

    /** Started and not yet finished: another capture waits so the frames do not mix. */
    static boolean busy() { return step >= 0 && step < steps(); }

    private static int steps() { return TIMINGS.size() + SHOTS.size() + PICKERS.length; }

    /** This step's frame-time run, or -1: they come after the held frames. */
    private static int timing() {
        int t = step - SHOTS.size();
        return t >= 0 && t < TIMINGS.size() ? t : -1;
    }

    /** This step's held frame (below SHOTS.size()) or picker (from SHOTS.size()). */
    private static int shot() { return step < SHOTS.size() ? step : step - TIMINGS.size(); }

    /** Each completed game frame (NativeWorldVerification.renderedFrame). */
    static void frame(RenderTarget target, Path game) {
        if (step < 0 || step >= steps() || capturing) return;
        if (timing() >= 0) { time(); return; }
        int shot = shot();
        // Another QA menu over the HUD hides the banner: hold this shot and fire it again once the screen is gone.
        boolean picker = shot >= SHOTS.size();
        if (picker && !(Minecraft.getInstance().gui.screen() instanceof com.thelads.core.v26_2.gui.LadsSettingsScreen26)) return;
        if (!picker && Minecraft.getInstance().gui.screen() != null) { held = true; return; }
        if (held) { held = false; fire(); return; }
        if (picker ? System.nanoTime() < due : --waitFrames > 0) return;
        capturing = true;
        Shot s = picker ? null : SHOTS.get(shot);
        String name = picker ? "killbanner-picker-" + PICKERS[shot - SHOTS.size()]
            : String.format("kb172/kb-%s-v%d-k%d%s-f%03d", s.style().id, s.variant(), s.kills(), s.headshot() ? "-hs" : "", s.frame());
        try {
            Path output = game.resolve("screenshots").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try {
                    if (picker) image.writeToFile(output);
                    else crop(image, output);
                    saved++;
                    if (picker || s.frame() == 2) LOGGER.info("Lads kill banner frame {}", output);
                }
                catch (Exception failure) { LOGGER.error("Lads kill banner capture FAILED: {}", name, failure); }
                finally { image.close(); Minecraft.getInstance().execute(KillBannerCapture::next); }
            });
        } catch (Exception failure) {
            LOGGER.error("Lads kill banner capture FAILED: {}", name, failure);
            next();
        }
    }

    /** The banner's part of the frame: the ring centre sits at the middle, 79.4% down, and the banner scales with the height. */
    private static void crop(NativeImage image, Path output) throws Exception {
        int w = image.getWidth(), h = image.getHeight();
        int x0 = Math.round(w * .5f - h * .3f), y0 = Math.round(h * .594f), cw = Math.min(w - x0, Math.round(h * .6f)), ch = Math.min(h - y0, Math.round(h * .37f));
        try (NativeImage part = new NativeImage(cw, ch, false)) {
            for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) part.setPixel(x, y, image.getPixel(x0 + x, y0 + y));
            part.writeToFile(output);
        }
    }

    /** A frame-time run: frames before the kill, the kill (on its frame), then the frames of its banner. */
    private static void time() {
        long now = System.nanoTime(), since = now - lastFrame;
        lastFrame = now;
        if (recorded < 0) { recorded++; return; } // the chosen skin's art loads on these frames' ticks
        TIMES[recorded++] = since;
        if (recorded == BEFORE) {
            Timing t = TIMINGS.get(timing());
            NativeKillBanner.bindCurrent();
            NativeKillBanner.timeline().clear();
            NativeKillBanner.trigger(t.kills(), false, false, t.chosen() ? module().chosen() : new KillBannerModule.Pick(t.style(), 0, t.style()));
        }
        if (recorded < TIMES.length) return;
        Timing t = TIMINGS.get(timing());
        int slowest = BEFORE + 1;
        for (int i = BEFORE + 1; i < TIMES.length; i++) if (TIMES[i] > TIMES[slowest]) slowest = i;
        long[] before = Arrays.copyOfRange(TIMES, 0, BEFORE), after = Arrays.copyOfRange(TIMES, BEFORE + 1, TIMES.length);
        Arrays.sort(before);
        Arrays.sort(after);
        LOGGER.info("Lads kill banner frame times {}: before the kill max {} ms (median {}), the kill's frame {} ms, its banner max {} ms"
            + " ({} frames after the kill's) (median {})", t.name(), ms(before[before.length - 1]), ms(before[before.length / 2]), ms(TIMES[BEFORE]),
            ms(after[after.length - 1]), slowest - BEFORE, ms(after[after.length / 2]));
        next();
    }

    private static String ms(long nanos) { return String.format("%.1f", nanos / 1e6); }

    private static void next() {
        capturing = false;
        if (++step < steps()) { fire(); return; }
        Minecraft.getInstance().gui.setScreen(null);
        KillBannerModule module = module();
        for (int i = 0; i < OPTIONS_BEFORE.size(); i++) module.getOptions().get(i).load(OPTIONS_BEFORE.get(i));
        module.setEnabled(enabledBefore);
        module.setLastModified(modifiedBefore);
        NativeKillBanner.timeline().clear();
        int failed = SHOTS.size() + PICKERS.length - saved;
        try {
            LOGGER.info("Lads kill banner assets: {} checks passed ({} skins: strips decode, every kill count's sound registered with its sample)",
                NativeKillBannerProbe.assets(), KillBannerStyle.values().length);
        } catch (Throwable failure) {
            failed++;
            LOGGER.error("Lads kill banner capture FAILED: assets", failure);
        }
        LOGGER.info("Lads kill banner capture END: {} passed, {} failed", saved, failed);
    }

    private static void fire() {
        KillBannerModule module = module();
        module.setEnabled(true);
        module.randomize.setIndex(KillBannerModule.RANDOM_OFF);
        if (timing() >= 0) {
            Timing t = TIMINGS.get(timing());
            module.sound.set(true); // the real play path (the QA game is muted)
            module.duration.setValue(2);
            // A random pick is not the chosen skin, so nothing loaded its art before the kill.
            module.bannerStyle.setIndex(KillBannerModule.styleIndexOf(t.chosen() ? t.style() : KillBannerStyle.DEFAULT));
            NativeKillBanner.timeline().clear();
            recorded = -20; // about 160 ms at 120 fps: the chosen skin's art loads on the next ticks, before the kill
            lastFrame = System.nanoTime();
            return;
        }
        int shot = shot();
        if (shot >= SHOTS.size()) {
            String picker = PICKERS[shot - SHOTS.size()];
            int aemondir = KillBannerModule.styleIndexOf(KillBannerStyle.AEMONDIR);
            module.randomize.setIndex(picker.equals("chosen") ? KillBannerModule.RANDOM_CHOSEN : KillBannerModule.RANDOM_OFF);
            module.bannerStyle.setIndex(switch (picker) {
                case "custom" -> KillBannerModule.CUSTOM;
                case "variants", "search" -> aemondir;
                case "base", "chosen" -> KillBannerModule.BASE;
                case "preview-rogue" -> KillBannerModule.ROGUE;
                case "list" -> KillBannerModule.REAVER;
                default -> KillBannerModule.styleIndexOf(KillBannerStyle.ONI);
            });
            module.setVariant(KillBannerStyle.AEMONDIR, 2);
            module.customVisual.setIndex(KillBannerStyle.AEMONDIR.ordinal());
            module.customSound.setIndex(KillBannerModule.REAVER);
            com.thelads.core.v26_2.gui.LadsSettingsScreen26 screen = new com.thelads.core.v26_2.gui.LadsSettingsScreen26(null);
            Minecraft.getInstance().gui.setScreen(screen);
            if (!picker.equals("list")) screen.openModule("KillBanner"); // "list": the modules list itself
            screen.searchKillBanners(picker.equals("search") ? "phase" : picker.equals("chosen") ? "rogue" : "");
            due = System.nanoTime() + (picker.startsWith("preview-oni-") ? 900_000_000L + 700_000_000L * (picker.charAt(12) - '1') : 1_200_000_000L);
            return;
        }
        Shot s = SHOTS.get(shot);
        module.sound.set(false); // hundreds of held frames: the frame-time runs play the sounds
        module.headshotText.set(true);
        module.bannerStyle.setIndex(KillBannerModule.styleIndexOf(s.style()));
        module.setVariant(s.style(), s.variant());
        module.duration.setValue(s.seconds());
        NativeKillBanner.bindCurrent();
        NativeKillBanner.timeline().clear(); // a fresh streak of exactly s.kills() kills
        NativeKillBanner.trigger(s.kills(), false, s.headshot());
        NativeKillBanner.timeline().freeze(s.frame() / 60.0 + 1 / 240.0); // a quarter frame in, as a 60 fps frame shows it
        waitFrames = 3;
    }

    private static KillBannerModule module() { return (KillBannerModule) NativeQualityOfLife.module("KillBanner"); }
}
