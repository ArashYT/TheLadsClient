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
 * combo step and play its file; hits 6 to 8 play After Combo 5 in order), waits out Combo Reset, then kills a weak mob (its banner
 * plays Kill 1). 1.9.1: the sound library (search, a favourite first, its picture), the trimmer on a 30 s file (handles dragged by
 * real mouse events, zoom, preview, Save: a WAV of the part in Combo 1), Sound Sets (save, rename, swap), After Combo 5's Add and
 * reorder, a delete with its question; then the bow sounds: real arrows of the player's at a villager 30 blocks away (head: long
 * shot and headshot) and 8 blocks away (chest: neither), and at a stand-in player only the client knows, with the hurt and
 * arrow-hit-player packets a server sends through the client's own handlers (uploaded and built-in sounds). Options are put
 * back. Logs "Lads sounds capture END: n passed, m failed" (or FAILED).
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
        /**
         * A no-AI villager {@code distance} blocks in front of the player, on the server (the last target goes), or with {@code player}
         * a stand-in player only the client knows (no server entity: arrows fly through it there).
         */
        void spawnArcheryTarget(double distance, boolean player);
        /** The client has the target. */
        boolean archeryTargetReady();
        /** A real arrow of the player's (spawned by the server, so the client tracks it as yours) 4 blocks before the target, at its head or chest. */
        void shootTarget(boolean head);
        /** What a server sends when your arrow hits a player, through the client's packet handlers: the stand-in's hurt, then the arrow-hit-player event. */
        void arrowHitPlayerPackets();
        void removeArcheryTarget();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    public static final String ENV = "LADS_VERIFY_SOUNDS";
    private final Path files;
    private final List<String> failures = new ArrayList<>();
    private final List<JsonElement> comboBefore = new ArrayList<>(), bannerBefore = new ArrayList<>();
    private boolean comboWas, bannerWas;
    private int step, wait, passed, hit, lines, patience;
    private interface Step { boolean run() throws Exception; }
    private List<Step> extra;
    private int extraAt;
    private LadsSettingsScreen menu;
    private String trimmed = "", longName = "";
    private double longSeconds;
    private int longBefore, headBefore, bowBefore;

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
                combo.after.set(List.of(upload("kill_4.mp3"), upload("kill_5.mp3")));
                combo.afterOrder.setIndex(ComboSoundsModule.AFTER_IN_ORDER);
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
                game.openSettings(ComboSoundsModule.NAME).scrollBy(4 * 43 + 67); // past Players, Mobs, Volume, Combo Reset, Sound Sets: the slots
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
            case 6 -> { // eight hits, 15 ticks apart (past the 10-tick hurt immunity)
                if (!game.attackTarget()) { check(false, "the tough mob is in the client world"); step = 9; break; }
                hit++;
                lines = CustomSounds.linesOpened();
                wait = 15;
            }
            case 7 -> {
                String expected = hit <= 5 ? "combo_" + hit + ".mp3" : hit % 2 == 0 ? "kill_4.mp3" : "kill_5.mp3"; // After Combo 5, In Order
                // The server's answer and the sound thread can lag a busy QA machine: up to 3 s for this hit to land and play.
                if (!(combo.combo() == hit && CustomSounds.lastPlayed().equals(expected) && CustomSounds.linesOpened() > lines) && ++patience < 60) {
                    step = 7;
                    break;
                }
                patience = 0;
                check(combo.combo() == hit && CustomSounds.lastPlayed().equals(expected) && CustomSounds.linesOpened() > lines,
                    "hit " + hit + ": combo step " + combo.combo() + ", played " + CustomSounds.lastPlayed() + " (expected " + expected + "), lines " + CustomSounds.linesOpened());
                if (hit < 8) step = 6;
                else wait = 60; // past Combo Reset (2 s)
            }
            case 8 -> {
                if (game.attackTarget()) wait = 15;
            }
            case 9 -> {
                if (combo.combo() != 1 && ++patience < 60) {
                    step = 9;
                    break;
                }
                patience = 0;
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
                if (!(KillBanners.TIMELINE.sequence() == 1 && CustomSounds.lastPlayed().equals("kill_1.mp3")) && ++patience < 60) {
                    step = 11;
                    break;
                }
                check(KillBanners.TIMELINE.sequence() == 1 && CustomSounds.lastPlayed().equals("kill_1.mp3") && CustomSounds.linesOpened() > lines,
                    "the kill shows banner " + KillBanners.TIMELINE.sequence() + " and plays " + CustomSounds.lastPlayed() + " (expected kill_1.mp3)");
                game.screenshot("sounds-kill-banner");
                wait = 10;
            }
            case 12 -> { // 1.9.1: the library, trimmer, sets and bow sounds, one step a tick until each is done
                if (extra == null) extra = extraSteps(game, combo);
                if (extraAt < extra.size()) {
                    if (extra.get(extraAt).run()) { extraAt++; patience = 0; }
                    else if (++patience > 400) { check(false, "1.9.1 step " + extraAt + " finished in time"); extraAt++; patience = 0; }
                    step = 12;
                }
            }
            default -> {
                List<Option> options = combo.getOptions();
                for (int i = 0; i < options.size(); i++) options.get(i).load(comboBefore.get(i));
                options = banner.getOptions();
                for (int i = 0; i < options.size(); i++) options.get(i).load(bannerBefore.get(i));
                combo.setEnabled(comboWas);
                banner.setEnabled(bannerWas);
                com.thelads.core.config.ConfigManager.save(); // the settings clicks saved QA values: the restored ones replace them
                if (failures.isEmpty()) LOGGER.info("Lads sounds capture END: {} passed, 0 failed", passed);
                else LOGGER.error("Lads sounds capture FAILED: {} passed, {} failed: {}", passed, failures.size(), failures);
                return true;
            }
        }
        return false;
    }

    private boolean clickScreen(String id) {
        LadsSettingsScreen.Rect r = menu.controlBounds(id);
        if (r == null) return false;
        menu.mouseClicked(r.x() + r.width() / 2.0, r.y() + r.height() / 2.0, 0);
        return true;
    }

    private boolean clickLibrary(String id) {
        LadsSettingsScreen.Rect r = menu.soundLibrary().bounds(id);
        if (r == null) return false;
        menu.mouseClicked(r.x() + r.width() / 2.0, r.y() + r.height() / 2.0, 0);
        return true;
    }

    /** Scrolls the page from the top until the control is whole on screen (one frame between steps); true once it is. */
    private boolean reveal(String id) {
        if (menu.revealed(id)) return true;
        if (patience == 0) menu.scrollBy(-100000);
        else menu.scrollBy(20);
        return false;
    }

    private void type(String text) { text.codePoints().forEach(menu::charTyped); }

    /** 30 s of 22.05 kHz mono: a short beep each second, a semitone higher each time, so a cut is easy to see and hear. */
    private static Path longWav(Path folder) throws java.io.IOException {
        int rate = 22050;
        byte[] data = new byte[30 * rate * 2];
        for (int i = 0; i < data.length / 2; i++) {
            int second = i / rate, at = i % rate;
            double level = at < rate / 4 ? Math.sin(Math.PI * at / (rate / 4.0)) : 0;
            int v = (int) (level * (6000 + 800 * second) * Math.sin(2 * Math.PI * 440 * Math.pow(2, second / 12.0) * i / rate));
            data[2 * i] = (byte) v;
            data[2 * i + 1] = (byte) (v >> 8);
        }
        Path wav = folder.resolve("qa-long.wav");
        javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(rate, 16, 1, true, false);
        javax.sound.sampled.AudioSystem.write(new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(data), format, data.length / 2),
            javax.sound.sampled.AudioFileFormat.Type.WAVE, wav.toFile());
        return wav;
    }

    private List<Step> extraSteps(Game game, ComboSoundsModule combo) {
        List<Step> steps = new ArrayList<>();
        // The library: Combo 1's Choose, a search, a favourite (first in the list), its picture.
        steps.add(() -> {
            combo.setEnabled(true);
            combo.favourites.set(List.of());
            combo.sets.load(new com.google.gson.JsonArray()); // no sets of an earlier run (all are put back at the end)
            Path scratch = Files.createDirectories(CustomSounds.folder().resolveSibling("qa-sounds"));
            // Antigravity's 45 s MP3 (a beep a second, rising) when the folder has it, else a 30 s WAV made here.
            boolean mp3 = Files.isRegularFile(files.resolve("long_45s.mp3"));
            longName = CustomSounds.upload(mp3 ? files.resolve("long_45s.mp3") : longWav(scratch));
            longSeconds = mp3 ? 45 : 30;
            check(CustomSounds.list().contains(longName), "a " + (int) longSeconds + " s sound is in the library: " + longName);
            Path doomed = scratch.resolve("qa-delete.wav");
            Files.copy(files.resolve("kill_3.wav").toFile().exists() ? files.resolve("kill_3.wav") : files.resolve("kill_3.mp3"), doomed, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            combo.steps[1].set(CustomSounds.upload(doomed));
            menu = game.openSettings(ComboSoundsModule.NAME);
            wait = 20;
            return true;
        });
        steps.add(() -> reveal("option:Combo 1:choose"));
        steps.add(() -> {
            clickScreen("option:Combo 1:choose");
            check(menu.soundLibrary().isOpen(), "Choose opens the sound library");
            type("combo");
            wait = 3;
            return true;
        });
        steps.add(() -> {
            check(menu.soundLibrary().listed().stream().allMatch(f -> f.contains("combo")), "the search keeps the combo sounds: " + menu.soundLibrary().listed());
            clickLibrary("star:combo_3.mp3");
            wait = 3;
            return true;
        });
        steps.add(() -> {
            List<String> listed = menu.soundLibrary().listed();
            check(combo.favourites.contains("combo_3.mp3") && listed.get(0).equals("combo_3.mp3"), "the starred sound is a favourite, listed first: " + listed);
            game.screenshot("sounds-library");
            wait = 10;
            return true;
        });
        // The trimmer: the 30 s file's Trim, the start handle to 5 s and the end one to 16 s by mouse, a zoom, a preview, Save.
        steps.add(() -> {
            menu.keyPressed(65, 2); // Ctrl+A clears the search
            type("long");
            wait = 3;
            return true;
        });
        steps.add(() -> {
            check(clickLibrary("trim:" + longName), longName + " has a Trim button");
            return true;
        });
        steps.add(() -> {
            if (!menu.soundLibrary().trimmerReady()) return false;
            double[] sel = menu.soundLibrary().selection();
            check(Math.abs(sel[2] - longSeconds) < .5 && sel[0] == 0 && Math.abs(sel[1] - 12) < .01, String.format(java.util.Locale.ROOT,
                "the trimmer reads all %.0f s and starts on the first 12: %.2f-%.2f of %.2f", longSeconds, sel[0], sel[1], sel[2]));
            check(Math.abs(menu.soundLibrary().trimmerVolume() - 1.0) < .01, "the trimmer volume slider starts at 100%");
            LadsSettingsScreen.Rect volRect = menu.soundLibrary().bounds("volume");
            check(volRect != null, "the trimmer has a volume slider");
            if (volRect != null) {
                double vx = volRect.x() + 4 + (volRect.width() - 8) * 0.75;
                double vy = volRect.y() + volRect.height() / 2.0;
                menu.mouseClicked(vx, vy, 0);
                menu.mouseReleased(vx, vy, 0);
                check(Math.abs(menu.soundLibrary().trimmerVolume() - 1.5) < .06,
                    "volume slider clicked to 150% (" + (int) Math.round(menu.soundLibrary().trimmerVolume() * 100) + "%)");
            }
            LadsSettingsScreen.Rect wave = menu.soundLibrary().bounds("wave");
            double y = wave.y() + wave.height() / 2.0;
            double total = sel[2];
            java.util.function.DoubleUnaryOperator x = t -> wave.x() + t / total * wave.width();
            menu.mouseClicked(x.applyAsDouble(0), y, 0);
            menu.mouseDragged(x.applyAsDouble(5), y, 0, 0, 0);
            menu.mouseReleased(x.applyAsDouble(5), y, 0);
            menu.mouseClicked(x.applyAsDouble(12), y, 0);
            menu.mouseDragged(x.applyAsDouble(16), y, 0, 0, 0);
            menu.mouseReleased(x.applyAsDouble(16), y, 0);
            sel = menu.soundLibrary().selection();
            check(Math.abs(sel[0] - 5) < .3 && Math.abs(sel[1] - 16) < .3, String.format(java.util.Locale.ROOT, "dragging the handles selects %.2f-%.2f s (wanted 5-16)", sel[0], sel[1]));
            menu.mouseScrolled(x.applyAsDouble(10), y, 1); // one notch of zoom about 10 s
            lines = CustomSounds.linesOpened();
            menu.charTyped(' '); // Space plays the selection
            wait = 12;
            return true;
        });
        steps.add(() -> {
            check(CustomSounds.linesOpened() > lines, "Space previews the selection (" + CustomSounds.lastPlayed() + ")");
            check(Math.abs(CustomSounds.lastVolume() - 1.5) < .1, "preview plays at 150% volume (" + CustomSounds.lastVolume() + ")");
            game.screenshot("sounds-trimmer");
            wait = 10;
            return true;
        });
        steps.add(() -> {
            check(clickLibrary("save"), "the trimmer has Save");
            return true;
        });
        steps.add(() -> {
            if (menu.soundLibrary().isOpen()) return false;
            trimmed = combo.steps[0].get();
            CustomSounds.Pcm pcm = CustomSounds.decoded(trimmed);
            check(trimmed.startsWith(longName.substring(0, longName.lastIndexOf('.')) + " ") && trimmed.endsWith(".wav") && pcm != null && Math.abs(pcm.seconds() - 11) < .4,
                "Save puts the part in Combo 1 as a WAV: " + trimmed + " (" + (pcm == null ? "unreadable" : pcm.seconds() + " s") + ")");
            check(Math.abs(CustomSounds.volume(trimmed) - 1.5) < .06, "the saved sound keeps 150% volume in trim data (" + (int) Math.round(CustomSounds.volume(trimmed) * 100) + "%)");
            combo.play(1);
            check(Math.abs(CustomSounds.lastVolume() - combo.volume.getValue() * 1.5) < .1,
                "playback applies 150% volume: " + CustomSounds.lastVolume() + " (module volume " + combo.volume.getValue() + " * 1.5)");
            CustomSounds.stopPreview();
            return true;
        });
        steps.add(() -> reveal("option:Combo 1:choose"));
        steps.add(() -> { game.screenshot("sounds-trimmed-slot"); wait = 10; return true; });
        // Sound Sets: Save current (then typed name), Default pling empties the slots, the saved set fills them again.
        steps.add(() -> reveal("option:Sound Sets:save"));
        steps.add(() -> {
            clickScreen("option:Sound Sets:save");
            type("QA set");
            menu.keyPressed(257, 0);
            check(combo.sets.get().size() == 1 && combo.sets.get().get(0).name().equals("QA set"), "Save current makes a set, named as typed: " + combo.sets.get());
            wait = 3;
            return true;
        });
        steps.add(() -> reveal("option:Sound Sets:use:1"));
        steps.add(() -> {
            clickScreen("option:Sound Sets:use:0");
            check(!combo.steps[0].isSet() && combo.after.size() == 0, "Default pling empties Combo 1-5 and After Combo 5");
            wait = 3;
            return true;
        });
        steps.add(() -> {
            clickScreen("option:Sound Sets:use:1");
            check(combo.steps[0].get().equals(trimmed) && combo.after.size() == 2, "QA set fills them again: " + combo.steps[0].get() + ", " + combo.after.get());
            wait = 5;
            return true;
        });
        steps.add(() -> { game.screenshot("sounds-sets"); wait = 5; return true; });
        // After Combo 5: Add from the library, then move it up.
        steps.add(() -> reveal("option:After Combo 5:add"));
        steps.add(() -> {
            clickScreen("option:After Combo 5:add");
            wait = 3;
            return true;
        });
        steps.add(() -> {
            check(clickLibrary("row:combo_1.mp3"), "the library lists combo_1.mp3 for After Combo 5");
            check(combo.after.get().equals(List.of("kill_4.mp3", "kill_5.mp3", "combo_1.mp3")), "Add appends it: " + combo.after.get());
            wait = 3;
            return true;
        });
        steps.add(() -> reveal("option:After Combo 5:up:2"));
        steps.add(() -> {
            clickScreen("option:After Combo 5:up:2");
            check(combo.after.get().equals(List.of("kill_4.mp3", "combo_1.mp3", "kill_5.mp3")), "^ moves it up: " + combo.after.get());
            wait = 3;
            return true;
        });
        steps.add(() -> reveal("option:After Combo 5:remove:2"));
        steps.add(() -> { game.screenshot("sounds-after-combo5"); wait = 5; return true; });
        // A delete: Combo 2's sound, asked first, gone from the folder and the slot.
        steps.add(() -> reveal("option:Combo 2:choose"));
        steps.add(() -> {
            clickScreen("option:Combo 2:choose");
            type("qa-delete");
            wait = 3;
            return true;
        });
        steps.add(() -> {
            check(clickLibrary("delete:qa-delete.wav"), "the library has Delete for qa-delete.wav");
            check(menu.confirmDialog().isOpen(), "Delete asks first");
            wait = 3;
            return true;
        });
        steps.add(() -> { game.screenshot("sounds-delete-confirm"); wait = 5; return true; });
        steps.add(() -> {
            var ok = menu.confirmDialog().confirmBounds();
            menu.mouseClicked(ok.x() + ok.width() / 2.0, ok.y() + ok.height() / 2.0, 0);
            check(!Files.exists(CustomSounds.folder().resolve("qa-delete.wav")) && !combo.steps[1].isSet() && !menu.soundLibrary().listed().contains("qa-delete.wav"),
                "the deleted sound leaves the folder, the list and Combo 2");
            menu.keyPressed(256, 0); // Esc closes the library
            game.closeScreen();
            return true;
        });
        // Bow sounds: a villager 30 blocks away, an arrow in its head.
        steps.add(() -> {
            ComboSoundsModule.trace = true;
            combo.setEnabled(true);
            combo.bowMobs.set(true);
            combo.longShots.set(true);
            combo.headshots.set(true);
            combo.longShotDistance.setValue(20);
            combo.longShotSound.set(upload("kill_2.mp3"));
            combo.headshotSound.set(upload("kill_3.mp3"));
            game.spawnArcheryTarget(30, false);
            wait = 40;
            return true;
        });
        steps.add(() -> {
            if (!game.archeryTargetReady()) return false;
            bowShot(game, combo, true);
            return true;
        });
        steps.add(() -> bowCheck(combo, true, true, "an arrow in the head of a villager 30 blocks away: long shot (kill_2) and headshot (kill_3)"));
        steps.add(() -> { game.spawnArcheryTarget(8, false); wait = 40; return true; });
        steps.add(() -> {
            if (!game.archeryTargetReady()) return false;
            bowShot(game, combo, false);
            wait = 60;
            return true;
        });
        steps.add(() -> bowCheck(combo, false, false, "an arrow in the chest of a villager 8 blocks away: no bow sound"));
        // A player: the stand-in 30 blocks away, the arrow at its head, then the server's packets for a player hit.
        steps.add(() -> { game.spawnArcheryTarget(30, true); wait = 10; return true; });
        steps.add(() -> {
            if (!game.archeryTargetReady()) return false;
            bowShot(game, combo, true);
            wait = 4; // the client sees the arrow cross the stand-in
            return true;
        });
        steps.add(() -> { game.arrowHitPlayerPackets(); wait = 5; return true; });
        steps.add(() -> bowCheck(combo, true, true, "the arrow-hit-player event after a head hit on a player 30 blocks away: long shot and headshot"));
        steps.add(() -> {
            combo.longShotSound.set("");
            combo.headshotSound.set("");
            game.spawnArcheryTarget(30, true);
            wait = 10;
            return true;
        });
        steps.add(() -> {
            if (!game.archeryTargetReady()) return false;
            bowShot(game, combo, true);
            wait = 4;
            return true;
        });
        steps.add(() -> { game.arrowHitPlayerPackets(); wait = 5; return true; });
        steps.add(() -> {
            if (combo.bowPlayed() < bowBefore + 2 && patience < 60) return false;
            check(combo.bowPlayed() == bowBefore + 2, "with no sound picked, the built-in level-up chime and anvil clink play (" + (combo.bowPlayed() - bowBefore) + " played)");
            ComboSoundsModule.trace = false;
            game.removeArcheryTarget();
            return true;
        });
        return steps;
    }

    private void bowShot(Game game, ComboSoundsModule combo, boolean head) {
        longBefore = CustomSounds.plays("kill_2.mp3");
        headBefore = CustomSounds.plays("kill_3.mp3");
        bowBefore = combo.bowPlayed();
        game.shootTarget(head);
    }

    /** Waits up to 3 s for the expected bow sounds (none expected: the whole wait), then checks exactly those played. */
    private boolean bowCheck(ComboSoundsModule combo, boolean longShot, boolean headshot, String what) {
        int longs = CustomSounds.plays("kill_2.mp3") - longBefore, heads = CustomSounds.plays("kill_3.mp3") - headBefore;
        boolean done = (longs > 0) == longShot && (heads > 0) == headshot;
        if ((!done || !longShot && !headshot) && patience < 60) return false;
        check(done && longs <= 1 && heads <= 1, what + " (long " + longs + ", head " + heads + ")");
        return true;
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
