package com.thelads.core.v1_20_1.feature;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.Option;
import com.thelads.core.v1_20_1.gui.DraggableHudScreen1201;
import com.thelads.core.v1_20_1.gui.LadsSettingsScreen1201;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA-only probe infrastructure for 1.20.1 (plain Fabric and Zero SMP through Connector). Inert unless the harness sets
 * {@code -Dthelads.verifyAutoWorld=true} and/or {@code -Dthelads.qaScript=<file>}, and then only inside a game folder under
 * {@code artifacts/verification}. It
 * <ul>
 * <li>reaches the title screen, photographs it, then opens (creating on first use) the superflat peaceful QA world
 *     {@link #save} and photographs it ("Lads auto-world QA READY", "Lads world capture END: 1 passed, 0 failed");</li>
 * <li>runs a script file, one step per line, and logs "Lads qa script END: N passed, M failed" (see the README beside the
 *     Verification harness for the syntax);</li>
 * <li>stops the game when the harness writes {@code .lads-qa-stop} into the game folder.</li>
 * </ul>
 * Screenshots go to {@code <game folder>/screenshots/<name>.png}, read straight from the main render target.
 */
public final class QaProbe1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    /** The QA world's folder name: -Dthelads.qaWorld (the harness gives each QA game folder its own), else LadsQA-<game folder name>. */
    public static String save = "LadsQA-1_20_1";
    private static final long TITLE_TIMEOUT = 300_000_000_000L, WORLD_TIMEOUT = 150_000_000_000L;

    private enum Phase { TITLE, LOADING, SETUP, SCRIPT, DONE }

    private static boolean registered, active, failed, autoWorld, worldOpened, scriptStarted, scriptDone, titleShot;
    private static Phase phase = Phase.TITLE;
    private static Path gameDir;
    private static long phaseSince, nextStopCheck;
    private static int titleTicks, setupTicks, waitTicks, index, passed, failedSteps, confirmTicks;
    private static String pendingShot;
    private static List<String[]> steps = new ArrayList<>();
    private static final List<InputConstants.Key> releaseNext = new ArrayList<>();
    /** Lanes' own long QA routines, run as script steps: "verb args" calls the routine every tick until it returns true. */
    private static final java.util.Map<String, java.util.function.Predicate<String[]>> routines = new java.util.HashMap<>();
    private static java.util.function.Predicate<String[]> routine;
    private static String[] routineStep;
    private static int reloadsLeft;
    private static java.util.concurrent.CompletableFuture<Void> reloadFuture;

    /** "reload N" routine: reloads the resource packs N times, each one started the tick after the last finished. */
    private static boolean reloadStep(String[] step) {
        Minecraft mc = Minecraft.getInstance();
        if (reloadFuture != null) {
            if (!reloadFuture.isDone()) return false;
            reloadFuture = null;
        }
        if (reloadsLeft <= 0) return true;
        reloadsLeft--;
        LOGGER.info("Lads qa reload: {} more after this one", reloadsLeft);
        reloadFuture = mc.reloadResourcePacks();
        return false;
    }

    private QaProbe1201() {}

    /** Client init: nothing is registered unless a QA flag is set. */
    public static void register() {
        if (registered) return;
        autoWorld = Boolean.getBoolean("thelads.verifyAutoWorld");
        String script = System.getProperty("thelads.qaScript");
        if (!autoWorld && (script == null || script.isBlank())) return;
        registered = true;
        ClientLifecycleEvents.CLIENT_STARTED.register(QaProbe1201::begin);
        ClientTickEvents.START_CLIENT_TICK.register(QaProbe1201::tick);
    }

    /** A script verb for a lane's own routine (ticked until it returns true; an exception fails the step). */
    public static void registerStep(String verb, java.util.function.Predicate<String[]> routine) { routines.put(verb.toLowerCase(Locale.ROOT), routine); }

    /** True while the QA probe drives this game. */
    public static boolean active() { return active && !failed; }

    /** Lads input rules need a focused window; QA windows are never activated, so probes count as focused. */
    public static boolean windowActive() { return Minecraft.getInstance().isWindowActive() || active(); }

    private static void begin(Minecraft mc) {
        try {
            gameDir = FabricLoader.getInstance().getGameDir().toRealPath();
            String normalized = gameDir.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
            if (!normalized.contains("/artifacts/verification/"))
                throw new IOException("QA probes run only in a folder under artifacts/verification, not " + gameDir);
            mc.options.pauseOnLostFocus = false;
            save = System.getProperty("thelads.qaWorld", "LadsQA-" + gameDir.getFileName().toString().replace('.', '_').replaceAll("[^A-Za-z0-9_-]", "_"));
            active = true;
            phaseSince = System.nanoTime();
            String script = System.getProperty("thelads.qaScript");
            if (script != null && !script.isBlank()) steps = parse(Path.of(script));
            LOGGER.info("Lads auto-world QA BEGIN: game folder {}; world {}; script steps {}", gameDir, autoWorld ? save : "(none: title only)", steps.size());
        } catch (Exception e) {
            fail("start", e);
        }
    }

    // ---- state machine ----------------------------------------------------------------------------------------------

    private static void tick(Minecraft mc) {
        if (!active || failed) return;
        long now = System.nanoTime();
        if (now >= nextStopCheck) {
            nextStopCheck = now + 250_000_000L;
            if (Files.isRegularFile(gameDir.resolve(".lads-qa-stop"))) {
                LOGGER.info("Lads auto-world QA STOP: graceful stop request received");
                active = false;
                mc.stop();
                return;
            }
        }
        mc.options.pauseOnLostFocus = false;
        try {
            for (InputConstants.Key key : releaseNext) KeyMapping.set(key, false);
            releaseNext.clear();
            switch (phase) {
                case TITLE -> title(mc, now);
                case LOADING -> loading(mc, now);
                case SETUP -> setup(mc);
                case SCRIPT -> script(mc);
                case DONE -> { }
            }
        } catch (Exception e) {
            fail(phase.name().toLowerCase(Locale.ROOT), e);
        }
    }

    private static void title(Minecraft mc, long now) throws Exception {
        // Supplementaries opens a welcome screen over the title when part of the pack is missing (QA subsets): leave it so the script can start.
        if (mc.getOverlay() == null && mc.screen != null && mc.screen.getClass().getSimpleName().equals("WelcomeMessageScreen")) mc.setScreen(new TitleScreen());
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) {
            titleTicks = 0;
            if (now - phaseSince > TITLE_TIMEOUT) throw new IllegalStateException("no title screen within 300 s; screen=" + describeScreen(mc));
            return;
        }
        titleTicks++;
        if (titleTicks == 60 && !titleShot) {
            titleShot = true;
            shot(mc, "qa-title");
            LOGGER.info("Lads auto-world QA TITLE: title screen reached and photographed (screenshots/qa-title.png)");
        }
        if (titleTicks < 65) return;
        if (!autoWorld) {
            phase = Phase.SCRIPT; // title-only script
            return;
        }
        openOrCreate(mc);
        phase = Phase.LOADING;
        phaseSince = now;
    }

    private static void openOrCreate(Minecraft mc) {
        worldOpened = true;
        Path world = gameDir.resolve("saves").resolve(save);
        if (!Files.exists(world.resolve("level.dat"))) {
            LOGGER.info("Lads auto-world QA CREATE: {} (new superflat, peaceful, survival, cheats on)", save);
            LevelSettings settings = new LevelSettings(save, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(save, settings, new WorldOptions(135L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        } else {
            LOGGER.info("Lads auto-world QA OPEN: {}", save);
            mc.createWorldOpenFlows().loadLevel(mc.screen, save);
        }
    }

    private static void loading(Minecraft mc, long now) {
        if (now - phaseSince > WORLD_TIMEOUT)
            throw new IllegalStateException("QA world not ready within 150 s; screen=" + describeScreen(mc));
        // A modded world asks before it opens (experimental settings, backup): in this sandboxed QA world the answer is always yes, once, and logged.
        if (mc.screen instanceof ConfirmScreen confirm) {
            if (++confirmTicks == 20) {
                LOGGER.info("Lads auto-world QA CONFIRM: '{}' screen answered yes (first button) in the sandbox QA world", confirm.getTitle().getString());
                try { shot(mc, "qa-confirm"); } catch (Exception ignored) { }
                for (var child : confirm.children()) if (child instanceof Button yes) { yes.onPress(); break; }
            }
            return;
        }
        confirmTicks = 0;
        if (mc.level == null || mc.player == null) return;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        if (mc.screen != null || mc.player.tickCount < 60 || !mc.levelRenderer.hasRenderedAllChunks()) return;
        LOGGER.info("Lads auto-world QA READY: world {} loaded, alive, unpaused and screen-free; window focus not needed", save);
        phase = Phase.SETUP;
        setupTicks = 0;
    }

    /** Deterministic scene: day, clear weather, no day or weather cycle, then a baseline photograph. */
    private static void setup(Minecraft mc) throws Exception {
        setupTicks++;
        String[] commands = { "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "time set day", "weather clear" };
        if (setupTicks <= commands.length) {
            mc.player.connection.sendCommand(commands[setupTicks - 1]);
            return;
        }
        if (setupTicks < commands.length + 40) return;
        shot(mc, "qa-world-ready");
        LOGGER.info("Lads world capture END: 1 passed, 0 failed (screenshots/qa-world-ready.png)");
        phase = Phase.SCRIPT;
    }

    // ---- script -----------------------------------------------------------------------------------------------------

    private static void script(Minecraft mc) {
        if (steps.isEmpty()) {
            if (!scriptDone) { scriptDone = true; phase = Phase.DONE; }
            return;
        }
        if (!scriptStarted) {
            scriptStarted = true;
            LOGGER.info("Lads qa script BEGIN: {} steps", steps.size());
        }
        if (waitTicks > 0) { waitTicks--; return; }
        if (routine != null) {
            try { if (!routine.test(routineStep)) return; }
            catch (Exception e) { stepFailed(String.join(" ", routineStep), e); }
            routine = null;
        }
        if (pendingShot != null) {
            String name = pendingShot;
            pendingShot = null;
            try { shot(mc, name); LOGGER.info("Lads qa shot: screenshots/{}.png", name); passed++; }
            catch (Exception e) { stepFailed("shot " + name, e); }
        }
        while (waitTicks == 0 && pendingShot == null && routine == null && index < steps.size()) {
            String[] step = steps.get(index++);
            LOGGER.info("Lads qa step {}/{}: {}", index, steps.size(), String.join(" ", step));
            try {
                run(mc, step);
                if (!step[0].equals("wait")) { if (waitTicks == 0) waitTicks = 1; }
                passed++;
                if (scriptDone) break;
            } catch (Exception e) {
                stepFailed(String.join(" ", step), e);
                waitTicks = 1;
            }
        }
        if (!scriptDone && waitTicks == 0 && pendingShot == null && routine == null && index >= steps.size()) {
            scriptDone = true;
            phase = Phase.DONE;
            LOGGER.info("Lads qa script END: {} passed, {} failed", passed, failedSteps);
            if (failedSteps > 0) LOGGER.error("Lads qa script FAILED: {} step(s) failed", failedSteps);
        }
    }

    private static void stepFailed(String what, Exception e) {
        failedSteps++;
        LOGGER.error("Lads qa step FAILED: {}: {}", what, e.toString());
    }

    private static void run(Minecraft mc, String[] s) throws Exception {
        String verb = s[0];
        switch (verb) {
            case "wait" -> waitTicks = duration(arg(s, 1));
            case "cmd" -> { needWorld(mc); mc.player.connection.sendCommand(rest(s, 1).replaceFirst("^/", "")); }
            case "chat" -> { needWorld(mc); mc.player.connection.sendChat(rest(s, 1)); }
            case "shot" -> { pendingShot = arg(s, 1); waitTicks = 3; }
            case "screen" -> openScreen(mc, arg(s, 1).toLowerCase(Locale.ROOT));
            case "key" -> pressKey(mc, arg(s, 1));
            case "hold" -> hold(mc, arg(s, 1), !"off".equalsIgnoreCase(s.length > 2 ? s[2] : "on"));
            case "click" -> { InputConstants.Key k = InputConstants.Type.MOUSE.getOrCreate(mouse(arg(s, 1))); KeyMapping.click(k); }
            case "hotbar" -> { needWorld(mc); mc.player.getInventory().selected = Math.max(0, Math.min(8, Integer.parseInt(arg(s, 1)) - 1)); }
            case "look" -> { needWorld(mc); mc.player.setYRot(Float.parseFloat(arg(s, 1))); mc.player.setXRot(Float.parseFloat(arg(s, 2))); }
            case "module" -> module(arg(s, 1), s.length > 2 ? s[2] : "toggle");
            case "option" -> option(arg(s, 1), arg(s, 2), rest(s, 3));
            case "dump" -> dump(mc, s.length > 1 ? s[1].toLowerCase(Locale.ROOT) : "state", s.length > 2 ? s[2] : null);
            case "reload" -> { reloadsLeft = s.length > 1 ? Integer.parseInt(s[1]) : 1; routine = QaProbe1201::reloadStep; routineStep = s; } // F3+T, N times back to back
            case "log" -> LOGGER.info("Lads qa log: {}", rest(s, 1));
            case "quit" -> {
                LOGGER.info("Lads qa quit requested");
                scriptDone = true;
                phase = Phase.DONE;
                LOGGER.info("Lads qa script END: {} passed, {} failed", passed + 1, failedSteps);
                mc.stop();
            }
            default -> {
                if (!routines.containsKey(verb)) throw new IllegalArgumentException("unknown step '" + verb + "'");
                routine = routines.get(verb);
                routineStep = s;
            }
        }
    }

    private static List<String[]> parse(Path file) throws IOException {
        List<String[]> out = new ArrayList<>();
        for (String raw : Files.readAllLines(file)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
            List<String> tokens = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false, any = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') { quoted = !quoted; any = true; }
                else if (Character.isWhitespace(c) && !quoted) { if (cur.length() > 0 || any) { tokens.add(cur.toString()); cur.setLength(0); any = false; } }
                else cur.append(c);
            }
            if (cur.length() > 0 || any) tokens.add(cur.toString());
            tokens.set(0, tokens.get(0).toLowerCase(Locale.ROOT));
            out.add(tokens.toArray(new String[0]));
        }
        return out;
    }

    private static String arg(String[] s, int i) {
        if (i >= s.length) throw new IllegalArgumentException("'" + s[0] + "' needs argument " + i);
        return s[i];
    }

    private static String rest(String[] s, int from) { return String.join(" ", java.util.Arrays.copyOfRange(s, Math.min(from, s.length), s.length)); }

    /** "40" = 40 ticks (20 per second); "2s" = 2 seconds; "500ms". */
    public static int duration(String v) {
        v = v.toLowerCase(Locale.ROOT);
        if (v.endsWith("ms")) return Math.max(1, Integer.parseInt(v.substring(0, v.length() - 2)) / 50);
        if (v.endsWith("s")) return Math.max(1, (int) Math.round(Double.parseDouble(v.substring(0, v.length() - 1)) * 20));
        return Math.max(1, Integer.parseInt(v));
    }

    private static void needWorld(Minecraft mc) {
        if (mc.player == null || mc.level == null) throw new IllegalStateException("not in a world");
    }

    private static int mouse(String v) {
        return switch (v.toLowerCase(Locale.ROOT)) { case "left" -> 0; case "right" -> 1; case "middle" -> 2; default -> throw new IllegalArgumentException("mouse button " + v); };
    }

    private static void openScreen(Minecraft mc, String name) {
        Screen current = mc.screen;
        switch (name) {
            case "none", "close" -> mc.setScreen(null);
            case "title" -> mc.setScreen(new TitleScreen());
            case "pause" -> { needWorld(mc); mc.setScreen(new PauseScreen(true)); }
            case "options" -> mc.setScreen(new OptionsScreen(current, mc.options));
            case "video" -> mc.setScreen(new VideoSettingsScreen(current, mc.options));
            case "controls" -> mc.setScreen(new ControlsScreen(current, mc.options));
            case "lads", "settings" -> mc.setScreen(new LadsSettingsScreen1201(current));
            case "hud", "hudeditor" -> mc.setScreen(new DraggableHudScreen1201(current));
            case "inventory" -> { needWorld(mc); mc.setScreen(new InventoryScreen(mc.player)); }
            case "chat" -> mc.setScreen(new ChatScreen(""));
            default -> throw new IllegalArgumentException("unknown screen '" + name + "' (none title pause options video controls lads hud inventory chat)");
        }
    }

    private static void pressKey(Minecraft mc, String name) {
        String n = name.toLowerCase(Locale.ROOT);
        switch (n) {
            case "f1" -> mc.options.hideGui = !mc.options.hideGui;
            case "f3" -> mc.options.renderDebug = !mc.options.renderDebug;
            // As vanilla's KeyboardHandler does for F11.
            case "f11" -> { mc.getWindow().toggleFullScreen(); mc.options.fullscreen().set(mc.getWindow().isFullscreen()); }
            case "escape", "esc" -> { if (mc.screen != null) mc.screen.onClose(); else mc.pauseGame(false); }
            default -> {
                InputConstants.Key key = InputConstants.getKey("key.keyboard." + n);
                KeyMapping.set(key, true);
                KeyMapping.click(key);
                releaseNext.add(key);
            }
        }
    }

    private static void hold(Minecraft mc, String name, boolean down) {
        KeyMapping mapping = switch (name.toLowerCase(Locale.ROOT)) {
            case "forward" -> mc.options.keyUp; case "back" -> mc.options.keyDown; case "left" -> mc.options.keyLeft; case "right" -> mc.options.keyRight;
            case "jump" -> mc.options.keyJump; case "sneak" -> mc.options.keyShift; case "sprint" -> mc.options.keySprint;
            case "attack" -> mc.options.keyAttack; case "use" -> mc.options.keyUse; case "tab" -> mc.options.keyPlayerList;
            default -> throw new IllegalArgumentException("hold: forward back left right jump sneak sprint attack use tab");
        };
        mapping.setDown(down);
    }

    private static Module findModule(String name) {
        for (Module m : ModuleManager.getInstance().getModules()) if (m.getName().equalsIgnoreCase(name)) return m;
        throw new IllegalArgumentException("no module '" + name + "'");
    }

    private static void module(String name, String mode) {
        Module m = findModule(name);
        switch (mode.toLowerCase(Locale.ROOT)) {
            case "on" -> m.setEnabled(true);
            case "off" -> m.setEnabled(false);
            case "toggle" -> m.toggle();
            default -> throw new IllegalArgumentException("module mode on|off|toggle");
        }
        LOGGER.info("Lads qa module: {} enabled={}", m.getName(), m.isEnabled());
    }

    private static void option(String module, String option, String json) {
        Module m = findModule(module);
        Option o = m.getOption(option);
        if (o == null) throw new IllegalArgumentException("module " + m.getName() + " has no option '" + option + "'");
        JsonElement value;
        try { value = JsonParser.parseString(json); } catch (Exception e) { value = JsonParser.parseString("\"" + json + "\""); }
        o.load(value);
        LOGGER.info("Lads qa option: {}.{} = {}", m.getName(), option, o.save());
    }

    public static void dump(Minecraft mc, String what, String filter) {
        if (what.equals("modules") || what.equals("all")) {
            for (Module m : ModuleManager.getInstance().getModules()) {
                if (filter != null && !m.getName().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) continue;
                StringBuilder options = new StringBuilder();
                for (Option o : m.getOptions()) options.append(o.getName()).append('=').append(o.save()).append("; ");
                LOGGER.info("Lads qa dump module: {} enabled={} support={} options: {}", m.getName(), m.isEnabled(), ModuleSupport.support(m.getName()), options);
            }
        }
        if (what.equals("state") || what.equals("all")) {
            StringBuilder s = new StringBuilder("Lads qa dump state: screen=" + describeScreen(mc) + " fps=" + mc.fpsString
                + " window=" + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " guiScale=" + mc.getWindow().getGuiScale()
                + " fov=" + mc.options.fov().get() + " hideGui=" + mc.options.hideGui + " debug=" + mc.options.renderDebug
                // Fullscreen as GLFW has it: owned by a monitor (exclusive) or a desktop window (borderless), and decorated or not.
                + " fullscreen=" + mc.getWindow().isFullscreen() + " ownedByMonitor=" + (org.lwjgl.glfw.GLFW.glfwGetWindowMonitor(mc.getWindow().getWindow()) != 0)
                + " decorated=" + (org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_DECORATED) == org.lwjgl.glfw.GLFW.GLFW_TRUE));
            if (mc.player != null && mc.level != null) {
                s.append(" dimension=").append(mc.level.dimension().location())
                    .append(String.format(Locale.ROOT, " pos=%.2f,%.2f,%.2f yaw=%.1f pitch=%.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot()))
                    .append(" health=").append(mc.player.getHealth()).append(" food=").append(mc.player.getFoodData().getFoodLevel())
                    .append(" gamemode=").append(mc.gameMode == null ? "?" : mc.gameMode.getPlayerMode().getName())
                    .append(" mainhand=").append(mc.player.getMainHandItem()).append(" effects=").append(mc.player.getActiveEffects().size())
                    .append(" dayTime=").append(mc.level.getDayTime()).append(" camera=").append(mc.options.getCameraType());
            }
            LOGGER.info(s.toString());
        }
        if (!what.equals("modules") && !what.equals("state") && !what.equals("all")) throw new IllegalArgumentException("dump modules [filter] | state | all");
    }

    private static String describeScreen(Minecraft mc) { return mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(); }

    // ---- screenshots ------------------------------------------------------------------------------------------------

    /** Writes the main render target (the last finished frame, GUI included) to screenshots/name.png. */
    private static void shot(Minecraft mc, String name) throws IOException {
        if (!name.matches("[A-Za-z0-9._-]{1,80}")) throw new IllegalArgumentException("shot name must be [A-Za-z0-9._-]");
        Path dir = gameDir.resolve("screenshots");
        Files.createDirectories(dir);
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(dir.resolve(name + ".png"));
        }
    }

    private static void fail(String where, Exception e) {
        failed = true;
        LOGGER.error("Lads auto-world QA FAILED: {}: {}", where, e.toString(), e);
    }
}
