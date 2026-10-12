package com.thelads.core.client.killbanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.thelads.core.client.sound.ModuleVolumes;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ComboSoundsModule;
import com.thelads.core.modules.KillBannerModule;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (1.9.1 kill banner lane, every version through its {@link Driver}): first the loudness run (game sounds as the game
 * plays them, then kill banner sounds at default settings, 2 to 3.5 s apart, for an OpenAL Soft recording to measure:
 * ALSOFT_CONF with the wave writer), then Hypixel's kill lines sent by the integrated server's own /tellraw about client-only
 * stand-in players the player never hit (each must make exactly the banners it should), a real kill of a pig by the player's
 * blow, and the Music &amp; Sounds sliders (shown and clicked). Module options are put back.
 */
public final class KillBanner191Qa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    public static final List<String> STAND_INS = List.of("LadsQaBob", "LadsQaCarl", "LadsQaDave", "LadsQaErin", "LadsQaFinn");

    /** What each version does for the run. */
    public interface Driver {
        String me();
        void standIns(List<String> names);
        void removeStandIns();
        /** The integrated server runs "tellraw @s {json}" as the player (the real command and chat packet path). */
        void tellraw(String json);
        /** A pig with almost no health, no AI, next to the player; {@link #attackPig} hits it with the player's own blow. */
        void spawnPig();
        boolean attackPig();
        void openSoundScreen();
        /** Clicks the slider whose label starts with {@code label} at {@code fraction} of its width, through the screen's input. */
        boolean clickSlider(String label, double fraction);
        void closeScreen();
        void shot(String name);
        /** The vanilla sounds to measure against, as the game plays them (sound id, volume, category). */
        List<String> references();
        void playReference(String reference);
        /** The kill banner of {@code pick} with its sound, as a kill plays it. */
        void banner(KillBannerModule.Pick pick);
        /**
         * Every volume but Master and the ones the run plays muted (true), or as they were (false); and Dynamic FPS off meanwhile
         * where the game has it (QA windows are never focused, and it turns an unfocused or hidden game's volume down or off).
         */
        void mute(boolean on);
        /** The integrated server has run every task this driver gave it (a busy QA machine's server can lag seconds). */
        default boolean serverCaughtUp() { return true; }
        /** Ticks to wait before the loudness run: unfocused Fabric QA games stay silent for their first minute or so. */
        default int settleTicks() { return 0; }
        /** 1.8.9: stands the player 1000 blocks east of the world's origin, facing south (or back where it was). */
        default boolean farFromOrigin(boolean on) { return false; }
    }

    private final Driver driver;
    private final List<IntSupplier> steps = new ArrayList<>();
    private final List<JsonElement> options = new ArrayList<>();
    private int step, wait, passed;
    private final List<String> failures = new ArrayList<>();
    private boolean enabledBefore;
    private double comboBefore;
    private long began;

    public KillBanner191Qa(Driver driver) {
        this.driver = driver;
        steps.add(this::begin);
        loudness();
        lines();
        realKill();
        sliders();
        steps.add(this::finish);
    }

    /** Game sounds as the game plays them, then kill banner sounds at default settings, apart for the recording to tell them apart. */
    private void loudness() {
        steps.add(driver::settleTicks);
        steps.add(() -> {
            driver.mute(true);
            LOGGER.info("Lads kb191 loudness BEGIN: Kill Banner Volume {} (default), master as the QA options set it, Dynamic FPS off", module().volume.getValue());
            return 60;
        });
        for (String reference : driver.references())
            steps.add(() -> { LOGGER.info("Lads kb191 sound {} at {} ms", reference, (System.nanoTime() - began) / 1_000_000); driver.playReference(reference); return 40; });
        for (KillBannerStyle style : new KillBannerStyle[] {KillBannerStyle.REAVER, KillBannerStyle.ROGUE, null}) {
            steps.add(() -> {
                KillBannerModule.Pick pick = style == null ? new KillBannerModule.Pick(null, 0, null) : new KillBannerModule.Pick(style, 0, style);
                LOGGER.info("Lads kb191 sound banner:{} at {} ms", style == null ? "base-chime" : style.id, (System.nanoTime() - began) / 1_000_000);
                KillBanners.TIMELINE.clear();
                driver.banner(pick);
                return style == null ? 40 : 70;
            });
        }
        boolean[] far = new boolean[1];
        steps.add(() -> (far[0] = driver.farFromOrigin(true)) ? 40 : 1);
        steps.add(() -> {
            if (!far[0]) return 1;
            LOGGER.info("Lads kb191 sound banner:reaver-far-from-origin at {} ms", (System.nanoTime() - began) / 1_000_000);
            KillBanners.TIMELINE.clear();
            driver.banner(new KillBannerModule.Pick(KillBannerStyle.REAVER, 0, KillBannerStyle.REAVER));
            return 70;
        });
        steps.add(() -> {
            if (far[0]) driver.farFromOrigin(false);
            driver.mute(false);
            KillBanners.TIMELINE.clear();
            LOGGER.info("Lads kb191 loudness END");
            return 20;
        });
    }

    /** Hypixel's lines by the server's /tellraw, about stand-ins the player never hit: the banners each must make. */
    private void lines() {
        String[][] lines = {
            // text, the kills it makes, why
            {"LadsQaBob was glazed in BBQ sauce by ME.", "1", "a cosmetic kill message about a player never hit (Bed Wars BBQ pack)"},
            {"LadsQaBob was glazed in BBQ sauce by ME.", "0", "the same kill's line again: one kill, one banner"},
            {"\u00a7cLadsQaCarl\u00a77 lost the draw to \u00a7aME\u00a77. \u00a7b\u00a7lFINAL KILL!", "1", "Western pack, colour codes, FINAL KILL!"},
            {"\u00a7a\u00a7lDOUBLE KILL! \u00a77on \u00a76[120] LadsQaDave \u00a7b+15XP \u00a76+10.00g", "1", "The Pit's double kill"},
            {"LadsQaErin was killed by ME.", "1", "Bed Wars kill"},
            {"\u00a76+10 coins! (Kill)", "0", "its reward line: the same kill"},
            {"\u00a7b+10 Bed Wars Experience (Kill)", "0", "its experience line: the same kill"},
            {"WAIT", "30", ""},
            {"\u00a76+10 coins! (Kill)", "1", "a reward line whose kill message said nothing known: a kill of its own"},
            {"LadsQaFinn was kicked from the guild by ME!", "0", "a guild notice is no kill"},
            {"LadsQaFinn was spooked by Steve.", "0", "someone else's kill"},
            {"[MVP+] LadsQaFinn: LadsQaFinn was killed by ME.", "0", "player chat"},
            {"\u00a7aYou were tipped by 5 players in the last minute!", "0", "a tip notice neither kills nor ends the streak"},
        };
        for (String[] line : lines) {
            if (line[0].equals("WAIT")) { steps.add(() -> Integer.parseInt(line[1])); continue; }
            int[] before = new int[1];
            steps.add(() -> {
                before[0] = KillBanners.TIMELINE.streak();
                JsonObject text = new JsonObject();
                text.addProperty("text", line[0].replace("ME", driver.me()));
                driver.tellraw(text.toString());
                return 1;
            });
            steps.add(() -> awaited(driver::serverCaughtUp, 200, 6)); // the line, then a few ticks for its packet
            if (line[2].startsWith("a cosmetic")) { steps.add(() -> 8); steps.add(() -> { driver.shot("kb191-themed"); return 4; }); }
            steps.add(() -> {
                int made = KillBanners.TIMELINE.streak() - before[0];
                check(made == Integer.parseInt(line[1]), "'" + line[0] + "': " + line[2] + " (" + made + " banner(s), streak " + KillBanners.TIMELINE.streak() + ")");
                return 2;
            });
        }
    }

    /** A pig the player kills with a blow of their own (Mobs on). */
    private void realKill() {
        int[] before = new int[1];
        steps.add(() -> { driver.spawnPig(); return 1; });
        steps.add(() -> awaited(driver::serverCaughtUp, 200, 10));
        steps.add(() -> { before[0] = KillBanners.TIMELINE.streak(); check(driver.attackPig(), "the player hits the pig"); return 1; });
        steps.add(() -> awaited(() -> KillBanners.TIMELINE.streak() != before[0], 200, 12));
        steps.add(() -> { driver.shot("kb191-real-kill"); return 10; });
        steps.add(() -> { check(KillBanners.TIMELINE.streak() == before[0] + 1, "a real kill (a pig, Mobs on) by the player's blow shows a banner"); return 1; });
    }

    /** Music & Sounds: the two sliders show the modules' Volumes and set them. */
    private void sliders() {
        steps.add(() -> {
            KillBanners.TIMELINE.clear();
            ModuleVolumes.set(0, .35);
            ModuleVolumes.set(1, .8);
            driver.openSoundScreen();
            return 15;
        });
        steps.add(() -> { driver.shot("kb191-sound-settings"); return 5; });
        steps.add(() -> {
            check(driver.clickSlider(ModuleVolumes.LABELS[0], .75), "the Kill Banners slider is on Music & Sounds and takes a click");
            check(Math.abs(module().volume.getValue() - .75) < 1e-6, "a click at 3/4 of Kill Banners sets the Kill Banner module's Volume to 75% (" + module().volume.getValue() + ")");
            check(driver.clickSlider(ModuleVolumes.LABELS[1], .2), "the Combo Sounds slider takes a click");
            check(Math.abs(combo().volume.getValue() - .2) < 1e-6, "a click at 1/5 of Combo Sounds sets Combo Sounds' Volume to 20% (" + combo().volume.getValue() + ")");
            return 5;
        });
        steps.add(() -> { driver.shot("kb191-sound-settings-set"); return 5; });
        steps.add(() -> { driver.closeScreen(); return 5; });
    }

    private static KillBannerModule module() { return (KillBannerModule) ModuleManager.getInstance().getModule("KillBanner"); }
    private static ComboSoundsModule combo() { return (ComboSoundsModule) ModuleManager.getInstance().getModule(ComboSoundsModule.NAME); }

    private int begin() {
        began = System.nanoTime();
        KillBannerModule module = module();
        enabledBefore = module.isEnabled();
        comboBefore = combo().volume.getValue();
        for (Option option : module.getOptions()) options.add(option.save().deepCopy());
        module.getOptions().forEach(Option::reset);
        module.setEnabled(true);
        module.mobs.set(true);
        module.unlimitedStreak.set(true);
        KillBanners.reset();
        driver.standIns(STAND_INS);
        LOGGER.info("Lads kb191 BEGIN: loudness, Hypixel lines by /tellraw, a real kill, the Music & Sounds sliders");
        return 10;
    }

    private int finish() {
        driver.mute(false);
        driver.removeStandIns();
        KillBannerModule module = module();
        for (int i = 0; i < options.size(); i++) module.getOptions().get(i).load(options.get(i));
        module.setEnabled(enabledBefore);
        combo().volume.setValue(comboBefore);
        KillBanners.reset();
        if (failures.isEmpty()) LOGGER.info("Lads kb191 END: {} checks passed, 0 failed", passed);
        else LOGGER.error("Lads kb191 FAILED: {} | {} checks passed", String.join(" | ", failures), passed);
        return 0;
    }

    private int polled;

    /** A step that waits: -1 (run it again next tick) until {@code done} or {@code timeout} ticks, then {@code after} ticks. */
    private int awaited(java.util.function.BooleanSupplier done, int timeout, int after) {
        if (!done.getAsBoolean() && ++polled < timeout) return -1;
        polled = 0;
        return after;
    }

    /** Each client tick; true once the run is over. */
    public boolean tick() {
        if (step >= steps.size()) return true;
        if (wait-- > 0) return false;
        try {
            int next = steps.get(step).getAsInt();
            if (next < 0) return false;
            step++;
            wait = next;
        } catch (RuntimeException failure) {
            fail("step " + step + ": " + failure);
            LOGGER.error("Lads kb191 step {} failed", step, failure);
            step = steps.size() - 1; // finish: put everything back
            wait = 0;
        }
        return false;
    }

    public boolean failed() { return !failures.isEmpty(); }

    private void check(boolean result, String what) {
        if (result) { passed++; LOGGER.info("Lads kb191 PASS: {}", what); }
        else fail(what);
    }

    private void fail(String what) {
        failures.add(what);
        LOGGER.error("Lads kb191 check failed: {}", what);
    }
}
