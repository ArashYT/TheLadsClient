package com.thelads.core.client;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only: the 1.9.1 fixes lane in a QA world, on every version (26.3 auto-world capture ".lads-qa-capture-fixes", 1.20.1 script verb
 * "fixes", 1.8.9 LADS_VERIFY_189_ONLY=fixes). Fullbright at midnight and in a closed stone room, off and on, photographed and measured
 * (mean brightness of the middle of the frame); three name tags at 4, 14 and 32 blocks with Text Shadow as fixed, as before the fix
 * (NametagDepth.qaVanilla) and off, photographed while the camera turns; then the pause menu's Multiplayer: the server list over the
 * world, Back to the pause menu, and a join (a local port nobody listens on) that saves and leaves the world first and ends on the
 * title screen's side. Logs "Lads fixes capture END: n passed, 0 failed" (or FAILED).
 */
public final class FixesQa {
    /** What the routine needs from a game version. */
    public interface Game {
        /** The player's feet (x, y, z). */
        double[] feet();
        /** A command as the integrated server's console (absolute coordinates). */
        void command(String command);
        /** A hollow stone box, or air, over that range. */
        void fill(int x1, int y1, int z1, int x2, int y2, int z2, boolean stone);
        /** A still armour stand with a visible name. */
        void summonTag(String name, double x, double y, double z);
        /** Removes every armour stand (earlier runs' tags). */
        void removeTags();
        void look(float yaw, float pitch);
        /** Photographs the next frame into this file (the version's QA screenshots folder). */
        Path screenshot(String name);
        boolean inWorld();
        void openPause();
        /** Presses the visible button with that label on the open screen (throws when there is none). */
        void press(String label);
        /** Back on the open screen (its Back, Cancel or "Back to ..." button). */
        void back();
        void closeScreen();
        /** The open screen: pause, servers, connect, failed, title, none, or its class name. */
        String screen();
        /** Refuses a server list with a non-local entry and gives an empty one two local entries nobody listens on (and skips the multiplayer warning). */
        void localServersOnly();
        /** Joins the server list's first entry through the list's own join. */
        void joinFirstServer();
        /** The open singleplayer world's folder. */
        Path worldFolder();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String[] MODES = {"fixed", "before", "noshadow"};
    private final List<String> failures = new ArrayList<>();
    private final List<JsonElement> fullbrightBefore = new ArrayList<>(), nametagsBefore = new ArrayList<>();
    private boolean fullbrightWas, nametagsWas;
    private int step, wait, passed, patience, frame, mode;
    private double[] home;
    private double nightOff, nightOn, caveOn, caveOff;
    private Path pendingShot, guiShot;
    private FileTime savedBefore;
    private Path levelDat;
    private boolean looking;
    private float yaw, pitch;

    public List<String> failures() { return failures; }

    /** One client tick in the QA world; true once finished. A step that throws fails the run, which then ends. */
    public boolean tick(Game game) {
        if (looking) game.look(yaw, pitch); // every tick: a mouse grab's jump on a focused window must not move the camera off it
        if (wait > 0) { wait--; return false; }
        Module fullbright = ModuleManager.getInstance().getModule("Fullbright");
        Module nametags = ModuleManager.getInstance().getModule("Nametags");
        try {
            return step(game, fullbright, nametags);
        } catch (Exception | Error failure) {
            LOGGER.error("Lads fixes capture: step {} threw", step - 1, failure);
            check(false, "step " + (step - 1) + ": " + failure);
            restoreModules(fullbright, nametags);
            step = 1000;
            wait = 0;
            return false;
        }
    }

    private boolean step(Game game, Module fullbright, Module nametags) throws Exception {
        switch (step++) {
            case 0 -> {
                LOGGER.info("Lads fixes capture BEGIN: Fullbright, nametags, pause-menu Multiplayer");
                fullbrightWas = fullbright.isEnabled();
                nametagsWas = nametags.isEnabled();
                for (Option o : fullbright.getOptions()) fullbrightBefore.add(o.save());
                for (Option o : nametags.getOptions()) nametagsBefore.add(o.save());
                home = game.feet();
                game.command("time set 18000");
                game.command("weather clear");
                fullbright.setEnabled(false);
                look(game, 0, 35);
                wait = 60;
            }
            case 1 -> { pendingShot = game.screenshot("fb-night-off"); wait = 10; }
            case 2 -> { if ((nightOff = luma()) < 0) return retry(); fullbright.setEnabled(true); game.command("time set 18000"); wait = 40; }
            case 3 -> { pendingShot = game.screenshot("fb-night-on"); wait = 10; }
            case 4 -> {
                if ((nightOn = luma()) < 0) return retry();
                check(lit(nightOff, nightOn), "Fullbright lights the midnight ground: off " + f(nightOff) + ", on " + f(nightOn));
                int x = floor(home[0]), y = floor(home[1]), z = floor(home[2]);
                game.fill(x - 4, y - 1, z - 4, x + 4, y + 5, z + 4, true);
                look(game, 0, 20);
                wait = 80;
            }
            case 5 -> { pendingShot = game.screenshot("fb-cave-on"); wait = 10; }
            case 6 -> { if ((caveOn = luma()) < 0) return retry(); fullbright.setEnabled(false); wait = 40; }
            case 7 -> { pendingShot = game.screenshot("fb-cave-off"); wait = 10; }
            case 8 -> {
                if ((caveOff = luma()) < 0) return retry();
                check(lit(caveOff, caveOn), "Fullbright lights a closed stone room: off " + f(caveOff) + ", on " + f(caveOn));
                int x = floor(home[0]), y = floor(home[1]), z = floor(home[2]);
                game.fill(x - 4, y, z - 4, x + 4, y + 5, z + 4, false);
                game.command("time set 6000");
                fullbright.setEnabled(false);
                game.removeTags();
                game.summonTag("Near Lads Tag", home[0], home[1], home[2] + 4);
                game.summonTag("Mid Lads Tag", home[0] + 1, home[1], home[2] + 14);
                game.summonTag("Far Lads Tag", home[0] - 1, home[1], home[2] + 32);
                nametags.setEnabled(true);
                ((BoolOption) nametags.getOption("Render Background")).set(false);
                wait = 60;
            }
            case 9 -> { // per mode: fixed, before the fix, no shadow; four frames each while the camera turns a little
                ((BoolOption) nametags.getOption("Text Shadow")).set(mode != 2);
                NametagDepth.qaVanilla = mode == 1;
                look(game, 0.15F * frame, frame % 2 == 0 ? 0 : 0.05F);
                wait = 3;
            }
            case 10 -> {
                game.screenshot("nt-" + MODES[mode] + "-" + frame);
                wait = 6;
                if (++frame < 4) step = 9;
                else if (++mode < MODES.length) { frame = 0; step = 9; }
                else {
                    NametagDepth.qaVanilla = false;
                    game.removeTags();
                    check(true, "name tags photographed: 3 modes x 4 frames (nt-<mode>-<frame>.png)");
                    step = 100; // the GUI text check, then the pause menu
                }
            }
            // GUI text is untouched by the name-tag depth fix: a shadowed menu and the chat, as fixed and as vanilla, pixel for pixel.
            case 100 -> { game.command("say Lads QA: shadowed chat text"); game.openPause(); wait = 30; }
            case 101 -> { guiShot = game.screenshot("gui-menu-fixed"); wait = 5; }
            case 102 -> { NametagDepth.qaVanilla = true; wait = 5; }
            case 103 -> { pendingShot = game.screenshot("gui-menu-vanilla"); wait = 5; }
            case 104 -> {
                if (!sameText(guiShot, pendingShot, 25, 20, 75, 95, "the pause menu's text")) return retry();
                NametagDepth.qaVanilla = false;
                game.closeScreen();
                game.command("say Lads QA: shadowed chat text");
                wait = 20;
            }
            case 105 -> { guiShot = game.screenshot("gui-chat-fixed"); wait = 5; }
            case 106 -> { NametagDepth.qaVanilla = true; wait = 5; }
            case 107 -> { pendingShot = game.screenshot("gui-chat-vanilla"); wait = 5; }
            case 108 -> {
                if (!sameText(guiShot, pendingShot, 0, 70, 50, 88, "the chat's text")) return retry(); // its lines, below HUD counters (FPS)
                NametagDepth.qaVanilla = false;
                step = 11;
            }
            case 11 -> {
                restoreModules(fullbright, nametags);
                looking = false;
                game.localServersOnly();
                levelDat = game.worldFolder().resolve("level.dat");
                savedBefore = Files.getLastModifiedTime(levelDat);
                game.openPause();
                wait = 20;
            }
            case 12 -> {
                check("pause".equals(game.screen()), "the pause menu is open (" + game.screen() + ")");
                game.press("Multiplayer");
                wait = 30;
            }
            case 13 -> {
                check("servers".equals(game.screen()) && game.inWorld(), "Multiplayer opens the server list over the world, still in it (" + game.screen() + ")");
                pendingShot = game.screenshot("pm-1-servers-in-world");
                wait = 10;
            }
            case 14 -> { game.back(); wait = 20; }
            case 15 -> {
                check("pause".equals(game.screen()) && game.inWorld(), "Back returns to the pause menu, still in the world (" + game.screen() + ")");
                pendingShot = game.screenshot("pm-2-back-to-pause");
                wait = 10;
            }
            case 16 -> { game.press("Multiplayer"); wait = 30; }
            case 17 -> { check("servers".equals(game.screen()), "Multiplayer again (" + game.screen() + ")"); game.joinFirstServer(); wait = 5; }
            case 18 -> {
                if (game.inWorld() && ++patience < 600) return retry();
                patience = 0;
                check(!game.inWorld(), "joining from the list left the world first");
            }
            case 19 -> {
                if (!"failed".equals(game.screen()) && ++patience < 600) return retry();
                patience = 0;
                check("failed".equals(game.screen()), "the join to a closed local port ends on the connection-failed screen (" + game.screen() + ")");
                FileTime saved = Files.getLastModifiedTime(levelDat);
                check(saved.compareTo(savedBefore) > 0, "the world was saved when it was left (level.dat " + savedBefore + " -> " + saved + ")");
                wait = 10; // a frame of it first
            }
            case 20 -> { pendingShot = game.screenshot("pm-3-connect-failed"); wait = 10; }
            case 21 -> { game.back(); wait = 30; }
            case 22 -> {
                check("servers".equals(game.screen()), "the failed screen leads back to the server list (" + game.screen() + ")");
                pendingShot = game.screenshot("pm-4-servers-after");
                wait = 10;
            }
            case 23 -> { game.back(); wait = 30; }
            case 24 -> {
                check("title".equals(game.screen()), "Back from that list is the title screen, not the pause menu of the world that is gone (" + game.screen() + ")");
                pendingShot = game.screenshot("pm-5-title");
                wait = 10;
            }
            case 25 -> { game.openPause(); wait = 20; } // any path back to the pause menu (Discover Servers' Back) without a world
            case 26 -> check("title".equals(game.screen()), "a pause menu asked for without a world is the title screen (" + game.screen() + ")");
            default -> {
                if (failures.isEmpty()) LOGGER.info("Lads fixes capture END: {} passed, 0 failed", passed);
                else LOGGER.error("Lads fixes capture FAILED: {} passed, {} failed: {}", passed, failures.size(), failures);
                return true;
            }
        }
        return false;
    }

    /**
     * The text in the two frames' region (percent of width and height) is the same: its white text pixels match, all but 2% (the
     * world behind keeps moving, and 1.20.1's Mods button has an animated gem). False while either is still being written. The fixed frame must be the same
     * as vanilla's: the depth fix only touches text drawn in the world.
     */
    private boolean sameText(Path fixed, Path vanilla, int x0, int y0, int x1, int y1, String what) {
        try {
            if (!Files.isRegularFile(fixed) || !Files.isRegularFile(vanilla)) return false;
            BufferedImage a = ImageIO.read(fixed.toFile()), b = ImageIO.read(vanilla.toFile());
            if (a == null || b == null) return false;
            int width = a.getWidth(), height = a.getHeight(), differ = 0, text = 0;
            for (int y = height * y0 / 100; y < height * y1 / 100; y++)
                for (int x = width * x0 / 100; x < width * x1 / 100; x++) {
                    boolean inA = white(a.getRGB(x, y)), inB = white(b.getRGB(x, y));
                    if (inA || inB) text++;
                    if (inA != inB) differ++;
                }
            patience = 0;
            check(text > 50 && differ <= text / 50, what + " is the same with the fix as without it (" + differ + " of " + text + " text pixels differ)");
            return true;
        } catch (Exception partial) {
            return false; // still being written
        }
    }

    private static boolean white(int rgb) {
        return ((rgb >> 16) & 255) > 200 && ((rgb >> 8) & 255) > 200 && (rgb & 255) > 200;
    }

    /** Puts both modules back as the run found them (also after a failure). */
    public void restoreModules(Module fullbright, Module nametags) {
        NametagDepth.qaVanilla = false;
        if (fullbrightBefore.isEmpty()) return;
        List<Option> options = fullbright.getOptions();
        for (int i = 0; i < options.size(); i++) options.get(i).load(fullbrightBefore.get(i));
        options = nametags.getOptions();
        for (int i = 0; i < options.size(); i++) options.get(i).load(nametagsBefore.get(i));
        fullbright.setEnabled(fullbrightWas);
        nametags.setEnabled(nametagsWas);
    }

    private boolean retry() {
        step--;
        if (++patience > 200) throw new IllegalStateException("step " + step + " did not settle (screenshot " + pendingShot + ")");
        return false;
    }

    /** Mean brightness (0-255) of the frame's middle (30-70% across, 10-45% down: ground or wall, no HUD), or -1 until it is written. */
    private double luma() {
        try {
            if (pendingShot == null || !Files.isRegularFile(pendingShot)) return -1;
            BufferedImage image = ImageIO.read(pendingShot.toFile());
            if (image == null) return -1;
            long sum = 0, count = 0;
            for (int y = image.getHeight() / 10; y < image.getHeight() * 45 / 100; y += 2)
                for (int x = image.getWidth() * 3 / 10; x < image.getWidth() * 7 / 10; x += 2) {
                    int rgb = image.getRGB(x, y);
                    sum += (299 * ((rgb >> 16) & 255) + 587 * ((rgb >> 8) & 255) + 114 * (rgb & 255)) / 1000;
                    count++;
                }
            patience = 0;
            LOGGER.info("Lads fixes capture frame {}: brightness {}", pendingShot.getFileName(), f(sum / (double) count));
            return sum / (double) count;
        } catch (Exception partial) {
            return -1; // still being written
        }
    }

    private void look(Game game, float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        looking = true;
        game.look(yaw, pitch);
    }

    /** Fullbright made the frame clearly brighter (a pack's own Brightness may already be at vanilla's brightest: Zero SMP's is). */
    private static boolean lit(double off, double on) { return on >= 1.5 * off && on - off >= 25; }

    private static int floor(double v) { return (int) Math.floor(v); }

    private static String f(double v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }

    private void check(boolean ok, String what) {
        if (ok) { passed++; LOGGER.info("Lads fixes QA ok: {}", what); }
        else { failures.add(what); LOGGER.error("Lads fixes QA FAILED: {}", what); }
    }
}
