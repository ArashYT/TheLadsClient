package com.thelads.core.client.sound;

import com.google.gson.JsonElement;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ComboSoundsModule;
import com.thelads.core.modules.KillBannerModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (LADS_VERIFY_SOUNDS = a folder holding combo_1..5.mp3, kill_1.mp3 and broken.mp3): uploaded sounds on every version, one
 * routine driven each client tick by the version (1.8.9 CoreProbe "sounds", 1.20.1 script verb "sounds", 26.3 auto-world capture).
 * Uploads the files (copied into the sandbox's shared sounds folder), decodes and plays one, photographs the Combo Sounds and Kill
 * Banner "Your sounds" rows, lands six hits on a tough no-AI mob through the version's real attack path (each must be the next
 * combo step and play its file), waits out Combo Reset, then kills a weak mob (its banner plays Kill 1). Options are put back.
 * Logs "Lads sounds capture END: n passed, m failed" (or FAILED).
 */
public final class SoundsQa {
    /** What the routine needs from a game version. */
    public interface Game {
        /** A no-AI mob 2.5 blocks in front of the player, on the server (the last one goes): tough survives many hits, else one kills it. */
        void spawnTarget(boolean tough);
        /** The player's own attack on that mob through the version's attack path; false while the client does not have it yet. */
        boolean attackTarget();
        /** The Lads settings at that module's page; returns the shared menu it shows. */
        LadsSettingsScreen openSettings(String module);
        void closeScreen();
        void screenshot(String name) throws Exception;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    public static final String ENV = "LADS_VERIFY_SOUNDS";
    private final Path files;
    private final List<String> failures = new ArrayList<>();
    private final List<JsonElement> comboBefore = new ArrayList<>(), bannerBefore = new ArrayList<>();
    private boolean comboWas, bannerWas;
    private int step, wait, passed, hit, lines;

    private SoundsQa(Path files) { this.files = files; }

    /** The routine when the harness asked for it, else null. */
    public static SoundsQa fromEnv() {
        String folder = System.getenv(ENV);
        return folder == null || folder.isBlank() ? null : new SoundsQa(Path.of(folder));
    }

    public List<String> failures() { return failures; }

    /** One client tick in the world; true once finished. */
    public boolean tick(Game game) throws Exception {
        if (wait > 0) { wait--; return false; }
        ComboSoundsModule combo = (ComboSoundsModule) ModuleManager.getInstance().getModule(ComboSoundsModule.NAME);
        KillBannerModule banner = (KillBannerModule) ModuleManager.getInstance().getModule("KillBanner");
        switch (step++) {
            case 0 -> {
                LOGGER.info("Lads sounds capture BEGIN: files {}, sounds folder {}", files, CustomSounds.folder());
                comboWas = combo.isEnabled();
                bannerWas = banner.isEnabled();
                for (Option o : combo.getOptions()) comboBefore.add(o.save());
                for (Option o : banner.getOptions()) bannerBefore.add(o.save());
                combo.setEnabled(true);
                combo.mobs.set(true);
                combo.volume.setValue(1);
                banner.setEnabled(true);
                banner.mobs.set(true);
                banner.sound.set(true);
                banner.volume.setValue(1);
                banner.randomize.setIndex(KillBannerModule.RANDOM_OFF);
                banner.bannerStyle.setIndex(KillBannerModule.CUSTOM);
                banner.ownSounds.set(true);
                for (int i = 0; i < 5; i++) combo.steps[i].set(upload("combo_" + (i + 1) + ".mp3"));
                banner.killSounds[0].set(upload("kill_1.mp3"));
                try {
                    CustomSounds.upload(files.resolve("broken.mp3"));
                    check(false, "broken.mp3 is refused at upload");
                } catch (java.io.IOException refused) {
                    check(true, "broken.mp3 is refused at upload (" + refused.getMessage() + ")");
                }
                CustomSounds.Pcm pcm = CustomSounds.decoded(combo.steps[0].get());
                check(pcm != null && pcm.frames() > 0, "combo_1.mp3 decodes: " + (pcm == null ? "no" : pcm.frames() + " frames, " + pcm.seconds() + " s, " + pcm.format()));
                lines = CustomSounds.linesOpened();
                CustomSounds.play(combo.steps[0].get(), 1);
                wait = 20;
            }
            case 1 -> {
                check(CustomSounds.linesOpened() > lines, "a sound line opened for the preview (" + CustomSounds.linesOpened() + " so far, last " + CustomSounds.lastPlayed() + ")");
                game.openSettings(ComboSoundsModule.NAME).scrollBy(4 * 43); // past Players, Mobs, Volume, Combo Reset: the slots
                wait = 30;
            }
            case 2 -> { game.screenshot("sounds-combo-settings"); wait = 10; }
            case 3 -> {
                LadsSettingsScreen.openKillBannerSections("customsound");
                game.openSettings("KillBanner").scrollBy(66); // the three section headers: Your sounds and its slots
                wait = 30;
            }
            case 4 -> { game.screenshot("sounds-killbanner-your-sounds"); wait = 10; }
            case 5 -> {
                LadsSettingsScreen.openKillBannerSections("skins");
                game.closeScreen();
                game.spawnTarget(true);
                wait = 40;
            }
            case 6 -> { // six hits, 15 ticks apart (past the 10-tick hurt immunity)
                if (!game.attackTarget()) { check(false, "the tough mob is in the client world"); step = 9; break; }
                hit++;
                lines = CustomSounds.linesOpened();
                wait = 15;
            }
            case 7 -> {
                String expected = "combo_" + Math.min(hit, 5) + ".mp3";
                check(combo.combo() == hit && CustomSounds.lastPlayed().equals(expected) && CustomSounds.linesOpened() > lines,
                    "hit " + hit + ": combo step " + combo.combo() + ", played " + CustomSounds.lastPlayed() + " (expected " + expected + "), lines " + CustomSounds.linesOpened());
                if (hit < 6) step = 6;
                else wait = 60; // past Combo Reset (2 s)
            }
            case 8 -> {
                if (game.attackTarget()) wait = 15;
            }
            case 9 -> {
                check(combo.combo() == 1, "a hit after Combo Reset starts again at step 1 (" + combo.combo() + ")");
                combo.setEnabled(false); // the kill's own hit would play a combo step too
                KillBanners.reset();
                game.spawnTarget(false);
                wait = 40;
            }
            case 10 -> {
                lines = CustomSounds.linesOpened();
                check(game.attackTarget(), "the weak mob is in the client world");
                wait = 12;
            }
            case 11 -> {
                check(KillBanners.TIMELINE.sequence() == 1 && CustomSounds.lastPlayed().equals("kill_1.mp3") && CustomSounds.linesOpened() > lines,
                    "the kill shows banner " + KillBanners.TIMELINE.sequence() + " and plays " + CustomSounds.lastPlayed() + " (expected kill_1.mp3)");
                game.screenshot("sounds-kill-banner");
                wait = 10;
            }
            default -> {
                List<Option> options = combo.getOptions();
                for (int i = 0; i < options.size(); i++) options.get(i).load(comboBefore.get(i));
                options = banner.getOptions();
                for (int i = 0; i < options.size(); i++) options.get(i).load(bannerBefore.get(i));
                combo.setEnabled(comboWas);
                banner.setEnabled(bannerWas);
                if (failures.isEmpty()) LOGGER.info("Lads sounds capture END: {} passed, 0 failed", passed);
                else LOGGER.error("Lads sounds capture FAILED: {} passed, {} failed: {}", passed, failures.size(), failures);
                return true;
            }
        }
        return false;
    }

    private String upload(String name) throws java.io.IOException {
        String stored = CustomSounds.upload(files.resolve(name));
        Path copy = CustomSounds.folder().resolve(stored);
        check(Files.isRegularFile(copy), "uploaded " + name + " -> " + copy + " (" + (Files.isRegularFile(copy) ? Files.size(copy) : 0) + " bytes)");
        return stored;
    }

    private void check(boolean ok, String what) {
        if (ok) { passed++; LOGGER.info("Lads sounds QA ok: {}", what); }
        else { failures.add(what); LOGGER.error("Lads sounds QA FAILED: {}", what); }
    }
}
