package com.thelads.core.client.hud;

import com.google.gson.JsonElement;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.HudModule;
import com.thelads.core.modules.KillBannerModule;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only: the Action Bar module and kill banners above the action bar, one routine driven each client tick by the version (1.8.9
 * CoreProbe "actionbar", 1.20.1 script verb "actionbar", 26.3 auto-world capture). A Hypixel-style action bar (formatting codes)
 * arrives the way the server sends it; kill banners fire over it. Photographs (ab-*): vanilla's action bar, a banner raised above
 * it, still raised once it faded, the next banner at its normal place, the module at vanilla's place, customised (size,
 * background) with a banner raised above it, moved away (the banner stays down), its own text colour, a record's "Now playing",
 * a styled component from /title (where the version has the command), hidden, the HUD editor, and five real server lines (long,
 * hex colours, bold, obfuscated) as vanilla and as the module show them (ab-lineN-vanilla / -module). Options are put back.
 * Logs "Lads action bar capture END: n passed, m failed" (or FAILED).
 */
public final class ActionBarQa {
    /** What the routine needs from a game version. */
    public interface Game {
        /**
         * An action bar message as the server sends it: {@code json} a chat component (versions that read one; null: {@code legacy}
         * as its text), else {@code legacy}, its text with formatting codes (1.8.9, and Hypixel's lines on every version).
         */
        void actionBar(String legacy, String json);
        /** A record's "Now playing". */
        void nowPlaying(String song);
        /** Runs a command as the player; false where the version has no /title actionbar (1.8.9). */
        boolean titleActionBar(String json);
        void hudEditor(boolean open);
        void screenshot(String name) throws Exception;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    static final String HYPIXEL = "§c1500/1500❤     §a+12 Coins §7(Bed Wars)     §b300/300✎ Mana";
    /**
     * Server lines (artifacts-agy/1.9.1/actionbar-lines.json): long, hex colours, bold, obfuscated, a health bar. Each is photographed
     * as vanilla shows it and as the module does with its defaults: the two must match (1.8.9: plus the module's shadow).
     */
    static final String[][] LINES = {
        {"§c§lWARNING: §7An intruder has invaded your base defense! Protect your bed or face elimination!",
            "{\"text\":\"WARNING: \",\"color\":\"red\",\"bold\":true,\"extra\":[{\"text\":\"An intruder has invaded your base defense! Protect your bed or face elimination!\",\"color\":\"gray\",\"bold\":false}]}"},
        {"§cOpponent: §6Steve §8[§a||||||||||§8||||||||||] §c20.0 HP",
            "{\"text\":\"Opponent: \",\"color\":\"#FF5555\",\"extra\":[{\"text\":\"Steve \",\"color\":\"#FFAA00\"},{\"text\":\"[\",\"color\":\"#707070\"},{\"text\":\"||||||||||\",\"color\":\"#00FF66\"},{\"text\":\"||||||||||\",\"color\":\"#333333\"},{\"text\":\"] \",\"color\":\"#707070\"},{\"text\":\"20.0 HP\",\"color\":\"#FF3366\"}]}"},
        {"§6ROUND 2/3 §f• §e§lCROWN RUSH §f• §a01:45",
            "{\"text\":\"ROUND 2/3 \",\"color\":\"#FF9F43\",\"extra\":[{\"text\":\"• \",\"color\":\"#FFFFFF\"},{\"text\":\"CROWN RUSH \",\"color\":\"#FEEA00\",\"bold\":true},{\"text\":\"• \",\"color\":\"#FFFFFF\"},{\"text\":\"01:45\",\"color\":\"#10AC84\"}]}"},
        {"§c§l§kXX§r §c§lCRITICAL HIT! 250% DAMAGE §c§l§kXX§r",
            "{\"text\":\"XX\",\"color\":\"red\",\"bold\":true,\"obfuscated\":true,\"extra\":[{\"text\":\" CRITICAL HIT! 250% DAMAGE \",\"color\":\"red\",\"bold\":true,\"obfuscated\":false},{\"text\":\"XX\",\"color\":\"red\",\"bold\":true,\"obfuscated\":true}]}"},
        {"§cOpponent: §eAlex §7- §c§l[||||§8||||||||||||||||] §44.5 HP",
            "{\"text\":\"Opponent: \",\"color\":\"red\",\"extra\":[{\"text\":\"Alex \",\"color\":\"yellow\"},{\"text\":\"- \",\"color\":\"gray\"},{\"text\":\"[||||\",\"color\":\"red\",\"bold\":true},{\"text\":\"||||||||||||||||] \",\"color\":\"dark_gray\",\"bold\":true},{\"text\":\"4.5 HP\",\"color\":\"dark_red\"}]}"}};
    private final List<String> failures = new ArrayList<>();
    private final List<JsonElement> barBefore = new ArrayList<>(), bannerBefore = new ArrayList<>();
    private boolean barWas, bannerWas, titled;
    private int step, wait, passed, line;
    private float lift;

    public List<String> failures() { return failures; }

    /** One client tick in the world; true once finished. */
    public boolean tick(Game game) throws Exception {
        if (wait > 0) { wait--; return false; }
        Module bar = ModuleManager.getInstance().getModule(ActionBar.MODULE);
        KillBannerModule banner = (KillBannerModule) ModuleManager.getInstance().getModule("KillBanner");
        switch (step++) {
            case 0 -> {
                LOGGER.info("Lads action bar capture BEGIN");
                barWas = bar.isEnabled();
                bannerWas = banner.isEnabled();
                for (Option o : bar.getOptions()) barBefore.add(o.save());
                for (Option o : banner.getOptions()) bannerBefore.add(o.save());
                bar.setEnabled(false);
                banner.setEnabled(true);
                banner.sound.set(false);
                banner.randomize.setIndex(KillBannerModule.RANDOM_OFF);
                banner.headshotBanner.set(true);
                banner.duration.setValue(5); // longer than the action bar's 3 s: it fades out under a raised banner
                KillBanners.reset();
                game.actionBar(HYPIXEL, null);
                wait = 10;
            }
            case 1 -> { game.screenshot("ab-0-vanilla"); wait = 5; }
            case 2 -> { fire(banner); wait = 15; }
            case 3 -> {
                lift = KillBanners.TIMELINE.lift();
                check(ActionBar.text() != null && lift > 0, "a banner that starts over vanilla's action bar plays raised (" + lift + " px)");
                game.screenshot("ab-1-raised");
                wait = 45; // the action bar's 3 s run out; the banner's 5 s do not
            }
            case 4 -> {
                check(ActionBar.text() == null && KillBanners.TIMELINE.age(System.nanoTime()) >= 0 && KillBanners.TIMELINE.lift() == lift,
                    "the action bar faded out and the banner stays raised (" + KillBanners.TIMELINE.lift() + " px, "
                        + KillBanners.TIMELINE.age(System.nanoTime()) + " s into it)");
                game.screenshot("ab-2-raised-after-fade");
                wait = 50;
            }
            case 5 -> { fire(banner); wait = 15; }
            case 6 -> {
                check(KillBanners.TIMELINE.lift() == 0, "the next banner, with no action bar, plays at its normal place (" + KillBanners.TIMELINE.lift() + " px)");
                game.screenshot("ab-3-next-normal");
                wait = 2; // each photograph ends its step: 26.3 takes it on the next frame
            }
            case 7 -> {
                KillBanners.reset();
                bar.setEnabled(true); // its defaults: vanilla's look and place, with a text shadow
                game.actionBar(HYPIXEL, null);
                wait = 10;
            }
            case 8 -> {
                check(ActionBar.text() != null, "the module shows the action bar");
                game.screenshot("ab-4-module-default");
                wait = 2;
            }
            case 9 -> {
                ((SliderOption) bar.getOption("Size")).setValue(150);
                ((BoolOption) bar.getOption("Show Background")).set(true);
                ColorOption background = (ColorOption) bar.getOption("Background");
                background.setUseGlobal(false);
                background.setColor(0xA0203060);
                game.actionBar(HYPIXEL, null);
                wait = 5;
            }
            case 10 -> { fire(banner); wait = 15; }
            case 11 -> {
                int[] spot = ActionBar.spot(null); // the module's own place: no graphics needed
                check(KillBanners.TIMELINE.lift() > 0, "a banner over the module's action bar (Size 150, background) plays raised ("
                    + KillBanners.TIMELINE.lift() + " px; action bar at " + (spot == null ? "none" : java.util.Arrays.toString(spot)) + ")");
                game.screenshot("ab-5-module-custom-raised");
                wait = 2;
            }
            case 12 -> {
                KillBanners.reset();
                HudSettings.getInstance().setPosition(ActionBar.MODULE, 20, 40);
                ((SliderOption) bar.getOption("Size")).setValue(125);
                game.actionBar(HYPIXEL, null);
                wait = 10;
            }
            case 13 -> { fire(banner); wait = 15; }
            case 14 -> {
                check(KillBanners.TIMELINE.lift() == 0, "with the action bar moved away the banner stays at its place (" + KillBanners.TIMELINE.lift() + " px)");
                game.screenshot("ab-6-module-moved");
                wait = 2;
            }
            case 15 -> {
                KillBanners.reset();
                ((BoolOption) bar.getOption("Custom Text Color")).set(true);
                ((HudModule) bar).setUseGlobalColor(false);
                ((HudModule) bar).setCustomColor(0xFFFFAA00);
                game.actionBar(HYPIXEL, null);
                wait = 10;
            }
            case 16 -> { game.screenshot("ab-7-custom-color"); wait = 2; }
            case 17 -> {
                ((BoolOption) bar.getOption("Custom Text Color")).set(false);
                game.nowPlaying("C418 - cat");
                wait = 10;
            }
            case 18 -> {
                check(ActionBar.animated(), "a record's \"Now playing\" cycles its colour");
                game.screenshot("ab-8-now-playing");
                wait = 2;
            }
            case 19 -> {
                titled = game.titleActionBar("{\"text\":\"Styled \",\"color\":\"gold\",\"bold\":true,\"extra\":[{\"text\":\"component\",\"color\":\"aqua\",\"italic\":true,\"bold\":false}]}");
                wait = titled ? 20 : 0;
            }
            case 20 -> {
                if (titled) {
                    check(ActionBar.text() != null && !ActionBar.animated(), "/title @s actionbar reaches the module");
                    game.screenshot("ab-9-title-component");
                }
                wait = 2;
            }
            case 21 -> {
                ((BoolOption) bar.getOption("Hide Action Bar")).set(true);
                game.actionBar(HYPIXEL, null);
                wait = 10;
            }
            case 22 -> {
                check(ActionBar.spot(null) == null, "Hide Action Bar: none shows");
                game.screenshot("ab-10-hidden");
                wait = 2;
            }
            case 23 -> {
                ((BoolOption) bar.getOption("Hide Action Bar")).set(false);
                game.actionBar(HYPIXEL, null);
                game.hudEditor(true);
                wait = 30;
            }
            case 24 -> { game.screenshot("ab-11-hud-editor"); wait = 10; }
            case 25 -> {
                game.hudEditor(false);
                for (Option o : bar.getOptions()) o.reset();
                HudSettings.getInstance().getPositions().remove(ActionBar.MODULE);
                wait = 10;
            }
            case 26 -> { // each server line: vanilla's, then the module's (defaults, vanilla's place)
                bar.setEnabled(line % 2 == 1);
                game.actionBar(LINES[line / 2][0], LINES[line / 2][1]);
                wait = 5;
            }
            case 27 -> {
                game.screenshot("ab-line" + (line / 2 + 1) + (line % 2 == 1 ? "-module" : "-vanilla"));
                if (++line < LINES.length * 2) step = 26;
                wait = 2;
            }
            default -> {
                game.hudEditor(false);
                List<Option> options = bar.getOptions();
                for (int i = 0; i < options.size(); i++) options.get(i).load(barBefore.get(i));
                options = banner.getOptions();
                for (int i = 0; i < options.size(); i++) options.get(i).load(bannerBefore.get(i));
                bar.setEnabled(barWas);
                banner.setEnabled(bannerWas);
                HudSettings.getInstance().getPositions().remove(ActionBar.MODULE);
                HudSettings.getInstance().setAnchor(ActionBar.MODULE, null);
                KillBanners.reset();
                if (failures.isEmpty()) LOGGER.info("Lads action bar capture END: {} passed, 0 failed", passed);
                else LOGGER.error("Lads action bar capture FAILED: {} passed, {} failed: {}", passed, failures.size(), failures);
                return true;
            }
        }
        return false;
    }

    /** A one-kill headshot banner now (its HEADSHOT label is its lowest part). */
    private static void fire(KillBannerModule banner) {
        KillBanners.show(banner, 1, false, true, banner.chosen(), System.nanoTime());
    }

    private void check(boolean ok, String what) {
        if (ok) { passed++; LOGGER.info("Lads action bar QA ok: {}", what); }
        else { failures.add(what); LOGGER.error("Lads action bar QA FAILED: {}", what); }
    }
}
