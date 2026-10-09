package com.thelads.core.v1_20_1.game.qa;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import com.thelads.core.v1_20_1.game.Gameplay1201;
import com.thelads.core.v1_20_1.game.NativeVerticalBob1201;
import com.thelads.core.v1_20_1.game.anim.NativeOldAnimations1201;
import com.thelads.core.v1_20_1.game.mixin.KeyboardInvokers;
import com.thelads.core.v1_20_1.game.mixin.MouseInvokers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (inert unless the harness set the QA flags and a script exists at {@code <game folder>/lads-qa/game-script.txt}): runs the
 * gameplay lane's own script in the QA world once it is ready and the harness's setup commands are done. Keys and clicks go through
 * the game's real GLFW handlers (KeyboardHandler.keyPress, MouseHandler.onPress/onScroll), so Lads' hooks see what a player causes.
 * One step per line: wait N, cmd text, module name on|off, option module option json, key name tap|down|up, scroll amount,
 * hold key on|off, look yaw pitch, camera first|back|front, screen none|inventory, shot name, log text, trace name on|off,
 * expect metric op value, stop. Results: "Lads game qa PASS|FAIL: ..." in the log, lads-qa/game/results.txt, trace CSVs beside it.
 */
public final class GameProbe1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static List<String[]> steps;
    private static Path gameDir, out;
    private static int index, wait, passed, failed, ticks;
    private static boolean started, finished;
    private static String traceName;
    private static StringBuilder trace;
    private static String pendingShot;
    private static final List<InputConstants.Key> releaseNext = new ArrayList<>();
    private static net.minecraft.world.phys.Vec3 lastPos = net.minecraft.world.phys.Vec3.ZERO;
    private static double speed;

    private GameProbe1201() {}

    public static void register() {
        if (!Gameplay1201.qaActive()) return;
        try {
            gameDir = FabricLoader.getInstance().getGameDir().toRealPath();
            Path script = gameDir.resolve("lads-qa").resolve("game-script.txt");
            if (!Files.isRegularFile(script)) return;
            out = gameDir.resolve("lads-qa").resolve("game");
            Files.createDirectories(out);
            steps = parse(script);
        } catch (Exception e) {
            LOGGER.error("Lads game qa FAILED to start", e);
            return;
        }
        ClientTickEvents.START_CLIENT_TICK.register(GameProbe1201::tick);
        LOGGER.info("Lads game qa armed: {} steps from {}", steps.size(), gameDir.resolve("lads-qa").resolve("game-script.txt"));
    }

    private static List<String[]> parse(Path file) throws IOException {
        List<String[]> parsed = new ArrayList<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            List<String> tokens = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false, any = false;
            for (char c : line.toCharArray()) {
                if (c == '"') { quoted = !quoted; any = true; }
                else if (Character.isWhitespace(c) && !quoted) { if (cur.length() > 0 || any) { tokens.add(cur.toString()); cur.setLength(0); any = false; } }
                else cur.append(c);
            }
            if (cur.length() > 0 || any) tokens.add(cur.toString());
            tokens.set(0, tokens.get(0).toLowerCase(Locale.ROOT));
            // A command keeps its text exactly (quotes included): it is typed into chat as written.
            if (tokens.get(0).equals("cmd")) { parsed.add(new String[] {"cmd", line.substring(3).strip()}); continue; }
            parsed.add(tokens.toArray(new String[0]));
        }
        return parsed;
    }

    private static void tick(Minecraft mc) {
        if (finished) return;
        LocalPlayer player = mc.player;
        if (player != null) {
            speed = Math.hypot(player.getX() - lastPos.x, player.getZ() - lastPos.z);
            lastPos = player.position();
        }
        for (InputConstants.Key key : releaseNext) ((KeyboardInvokers) mc.keyboardHandler).ladsKeyPress(mc.getWindow().getWindow(), key.getValue(), 0, 0, 0);
        releaseNext.clear();
        if (trace != null && player != null) traceRow(mc, player);
        if (!started) {
            // The harness runs its own setup commands up to about tick 100 of the world; start well after.
            if (player == null || mc.level == null || mc.screen != null || player.tickCount < 220 || !mc.levelRenderer.hasRenderedAllChunks()) return;
            started = true;
            LOGGER.info("Lads game qa BEGIN: {} steps", steps.size());
        }
        if (wait > 0) { wait--; return; }
        if (pendingShot != null) {
            try { shot(mc, pendingShot); LOGGER.info("Lads game qa shot: screenshots/{}.png", pendingShot); } catch (Exception e) { fail("shot " + pendingShot + ": " + e); }
            pendingShot = null;
        }
        while (wait == 0 && pendingShot == null && index < steps.size()) {
            String[] step = steps.get(index++);
            try {
                run(mc, step);
            } catch (Exception e) {
                fail(String.join(" ", step) + ": " + e);
            }
        }
        if (wait == 0 && pendingShot == null && index >= steps.size()) {
            finished = true;
            String summary = "Lads game qa END: " + passed + " passed, " + failed + " failed";
            LOGGER.info(summary);
            append("results.txt", summary + "\n");
            if (trace != null) endTrace();
        }
    }

    private static void run(Minecraft mc, String[] s) throws Exception {
        switch (s[0]) {
            case "wait" -> wait = com.thelads.core.v1_20_1.feature.QaProbe1201.duration(s[1]);
            case "dump" -> com.thelads.core.v1_20_1.feature.QaProbe1201.dump(mc, s.length > 1 ? s[1].toLowerCase(Locale.ROOT) : "state", s.length > 2 ? s[2] : null);
            case "action" -> { // a module's action option (Background Profiles, Toggle Sprint key ...): what its card button does
                if (!(module(s[1]).getOption(s[2]) instanceof com.thelads.core.config.ActionOption action) || !action.isAvailable()) throw new IllegalArgumentException("no action " + s[2]);
                action.run();
            }
            case "dfps" -> { // QA: what Lads' Dynamic FPS engine sees right now
                long handle = mc.getWindow().getWindow();
                var observer = com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod.getWindow();
                LOGGER.info("Lads game qa dfps: state={} disabled={} target={} observer(focused={} hovered={} iconified={}) glfw(focused={} hovered={} iconified={} visible={}) loops={} rendered={}",
                    com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod.powerState(), com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod.isDisabled(),
                    com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod.targetFrameRate(),
                    observer == null ? "-" : observer.isFocused(), observer == null ? "-" : observer.isHovered(), observer == null ? "-" : observer.isIconified(),
                    GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED), GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_HOVERED),
                    GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED), GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE),
                    com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod.probeLoops, com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod.probeRendered);
            }
            case "extopen" -> { // QA: the Open settings button of an external mod card (what the Lads menu does for an installed mod)
                com.thelads.core.v1_20_1.gui.ExternalModSettings1201.open(s[1], mc.screen);
                LOGGER.info("Lads game qa extopen: {} -> screen {}", s[1], mc.screen == null ? "none" : mc.screen.getClass().getName());
            }
            case "focus" -> { // QA: the window loses (off) or gets back (on) the focus and the mouse as far as Dynamic FPS can tell; auto = ask GLFW again
                Boolean value = switch (s[1]) { case "off" -> Boolean.FALSE; case "on" -> Boolean.TRUE; default -> null; };
                com.thelads.core.v1_20_1.perf.dynamicfps.feature.state.WindowObserver.qaFocused = value;
                com.thelads.core.v1_20_1.perf.dynamicfps.feature.state.WindowObserver.qaHovered = value;
                LOGGER.info("Lads game qa focus: {}", s[1]);
            }
            case "window" -> { // QA: a real focus loss (hide) or minimise, as a player alt-tabbing causes; show/restore undoes it
                long handle = mc.getWindow().getWindow();
                switch (s[1]) { case "hide" -> GLFW.glfwHideWindow(handle); case "show" -> GLFW.glfwShowWindow(handle); case "iconify" -> GLFW.glfwIconifyWindow(handle); case "restore" -> GLFW.glfwRestoreWindow(handle); default -> throw new IllegalArgumentException("window " + s[1]); }
                LOGGER.info("Lads game qa window: {} focused={}", s[1], GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED));
            }
            case "click" -> {
                int button = switch (s[1]) { case "left" -> 0; case "right" -> 1; default -> 2; };
                var handler = (MouseInvokers) mc.mouseHandler;
                handler.ladsPress(mc.getWindow().getWindow(), button, 1, 0);
                handler.ladsPress(mc.getWindow().getWindow(), button, 0, 0);
            }
            case "cmd" -> mc.player.connection.sendCommand(rest(s, 1).replaceFirst("^/", ""));
            case "module" -> {
                Module m = module(s[1]);
                switch (s[2]) { case "on" -> m.setEnabled(true); case "off" -> m.setEnabled(false); default -> m.toggle(); }
                LOGGER.info("Lads game qa module: {} enabled={}", m.getName(), m.isEnabled());
            }
            case "option" -> {
                Option o = module(s[1]).getOption(s[2]);
                if (o == null) throw new IllegalArgumentException("no option " + s[2]);
                JsonElement value;
                try { value = JsonParser.parseString(rest(s, 3)); } catch (Exception e) { value = JsonParser.parseString("\"" + rest(s, 3) + "\""); }
                o.load(value);
            }
            case "key" -> {
                InputConstants.Key key = InputConstants.getKey("key.keyboard." + s[1].toLowerCase(Locale.ROOT));
                KeyboardInvokers handler = (KeyboardInvokers) mc.keyboardHandler;
                long window = mc.getWindow().getWindow();
                String mode = s.length > 2 ? s[2] : "tap";
                if (!mode.equals("up")) handler.ladsKeyPress(window, key.getValue(), 0, 1, 0);
                if (mode.equals("up")) handler.ladsKeyPress(window, key.getValue(), 0, 0, 0);
                if (mode.equals("tap")) releaseNext.add(key);
            }
            case "mouse" -> {
                int button = switch (s[1]) { case "left" -> 0; case "right" -> 1; default -> 2; };
                MouseInvokers handler = (MouseInvokers) mc.mouseHandler;
                String mode = s.length > 2 ? s[2] : "tap";
                if (!mode.equals("up")) handler.ladsPress(mc.getWindow().getWindow(), button, 1, 0);
                if (mode.equals("up") || mode.equals("tap")) handler.ladsPress(mc.getWindow().getWindow(), button, 0, 0);
            }
            case "scroll" -> ((MouseInvokers) mc.mouseHandler).ladsScroll(mc.getWindow().getWindow(), 0, Double.parseDouble(s[1]));
            case "hold" -> hold(mc, s[1]).setDown(!"off".equals(s.length > 2 ? s[2] : "on"));
            case "look" -> { mc.player.setYRot(Float.parseFloat(s[1])); mc.player.setXRot(Float.parseFloat(s[2])); mc.player.yRotO = mc.player.getYRot(); mc.player.xRotO = mc.player.getXRot(); }
            case "camera" -> mc.options.setCameraType(switch (s[1]) { case "back" -> CameraType.THIRD_PERSON_BACK; case "front" -> CameraType.THIRD_PERSON_FRONT; default -> CameraType.FIRST_PERSON; });
            case "screen" -> mc.setScreen(s[1].equals("inventory") ? new InventoryScreen(mc.player) : null);
            case "hotbar" -> mc.player.getInventory().selected = Integer.parseInt(s[1]) - 1;
            case "shot" -> { pendingShot = s[1]; wait = 3; }
            case "shotnow" -> { shot(mc, s[1]); LOGGER.info("Lads game qa shot: screenshots/{}.png", s[1]); }
            case "cheats" -> LOGGER.info("Lads game qa cheats: {}", com.thelads.core.v1_20_1.game.cheats.Cheats1201.lastState);
            case "grab" -> ((MouseInvokers) mc.mouseHandler).ladsGrabbed(s[1].equals("on"));
            case "mine" -> {
                // QA windows never grab the mouse, so holding the attack key would not mine: start (or stop) the break directly.
                if (s[1].equals("off")) mc.gameMode.stopDestroyBlock();
                else mc.gameMode.startDestroyBlock(mc.player.blockPosition().offset(Integer.parseInt(s[1]), Integer.parseInt(s[2]), Integer.parseInt(s[3])),
                    net.minecraft.core.Direction.NORTH);
            }
            case "anim" -> LOGGER.info("Lads game qa anim: {}", NativeOldAnimations1201.debug(mc.player));
            case "log" -> LOGGER.info("Lads game qa log: {}", rest(s, 1));
            case "trace" -> { if (s[2].equals("on")) { traceName = s[1]; trace = new StringBuilder("tick,sprinting,sentSprint,sneaking,keyShift,pose,speed,fov,zoomActive,x,y,z,yaw,pitch,health,hurtTime,onGround,tiltYaw,tiltStrength,bob,eye,swing\n"); } else endTrace(); }
            case "expect" -> expect(mc, s);
            case "stop" -> { finished = true; LOGGER.info("Lads game qa END: {} passed, {} failed", passed, failed); append("results.txt", "Lads game qa END: " + passed + " passed, " + failed + " failed\n"); mc.stop(); }
            default -> throw new IllegalArgumentException("unknown step " + s[0]);
        }
    }

    private static KeyMapping hold(Minecraft mc, String name) {
        return switch (name) {
            case "forward" -> mc.options.keyUp; case "back" -> mc.options.keyDown; case "left" -> mc.options.keyLeft; case "right" -> mc.options.keyRight;
            case "jump" -> mc.options.keyJump; case "sneak" -> mc.options.keyShift; case "sprint" -> mc.options.keySprint;
            case "attack" -> mc.options.keyAttack; case "use" -> mc.options.keyUse; case "drop" -> mc.options.keyDrop;
            default -> throw new IllegalArgumentException("hold " + name);
        };
    }

    private static Module module(String name) {
        for (Module m : ModuleManager.getInstance().getModules()) if (m.getName().equalsIgnoreCase(name)) return m;
        throw new IllegalArgumentException("no module " + name);
    }

    private static String rest(String[] s, int from) { return String.join(" ", java.util.Arrays.copyOfRange(s, Math.min(from, s.length), s.length)); }

    /** The numeric state a step can check or trace. */
    private static double metric(Minecraft mc, String name) {
        LocalPlayer p = mc.player;
        var toggles = NativeFeatures1201.toggles();
        return switch (name) {
            case "sprinting" -> p.isSprinting() ? 1 : 0;
            case "sneaking" -> p.isShiftKeyDown() ? 1 : 0;
            case "crouching" -> p.isCrouching() ? 1 : 0;
            case "pose" -> p.getPose().ordinal();
            case "speed" -> speed;
            case "fov" -> NativeFeatures1201.lastWorldFov;
            case "zoom" -> NativeFeatures1201.zoom().isActive() ? 1 : 0;
            case "sprinttoggled" -> toggles.isSprintToggled() ? 1 : 0;
            case "sneaktoggled" -> toggles.isSneakToggled() ? 1 : 0;
            case "sentsprint" -> ((com.thelads.core.v1_20_1.game.mixin.LocalPlayerAccessor) p).ladsSentSprint() ? 1 : 0;
            case "yaw" -> p.getYRot();
            case "pitch" -> p.getXRot();
            case "health" -> p.getHealth();
            case "hurttime" -> p.hurtTime;
            case "onground" -> p.onGround() ? 1 : 0;
            case "tiltyaw" -> Gameplay1201.lastTiltYaw;
            case "tiltstrength" -> Gameplay1201.lastTiltStrength;
            case "cheatsguests" -> {
                var server = mc.getSingleplayerServer();
                Boolean guests = server == null ? null : com.thelads.core.client.WorldCheats.guests(com.thelads.core.v1_20_1.game.cheats.Cheats1201.folder(server));
                yield guests == null ? -1 : guests ? 1 : 0;
            }
            case "essentialswitch" -> {
                Boolean essential = com.thelads.core.client.WorldCheats.essential();
                yield essential == null ? -1 : essential ? 1 : 0;
            }
            case "fps" -> mc.getFps();
            case "rawmouse" -> GLFW.glfwGetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_RAW_MOUSE_MOTION) == GLFW.GLFW_TRUE ? 1 : 0;
            case "swinging" -> p.swinging ? 1 : 0;
            case "using" -> p.isUsingItem() ? 1 : 0;
            case "resscale" -> com.thelads.core.v1_20_1.game.RenderScale1201.lastScale;
            case "resframes" -> com.thelads.core.v1_20_1.game.RenderScale1201.scaledFrames;
            case "reswidth" -> com.thelads.core.v1_20_1.game.RenderScale1201.lastWidth;
            case "bob" -> NativeVerticalBob1201.lastDisplacement;
            case "gamma" -> NativeFeatures1201.fullbrightGamma();
            case "eye" -> mc.gameRenderer.getMainCamera().getPosition().y - p.getY();
            case "applied" -> NativeOldAnimations1201.APPLIED.size();
            case "items" -> mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(64)).size();
            default -> throw new IllegalArgumentException("metric " + name);
        };
    }

    private static void expect(Minecraft mc, String[] s) {
        String name = s[1], op = s[2];
        double actual = metric(mc, name), wanted = s.length > 3 ? Double.parseDouble(s[3]) : 1;
        boolean ok = switch (op) {
            case "==", "true" -> Math.abs(actual - wanted) < 1e-3;
            case "false" -> Math.abs(actual) < 1e-3;
            case "!=" -> Math.abs(actual - wanted) >= 1e-3;
            case "<" -> actual < wanted; case ">" -> actual > wanted; case "<=" -> actual <= wanted; case ">=" -> actual >= wanted;
            default -> throw new IllegalArgumentException("op " + op);
        };
        String line = (ok ? "PASS" : "FAIL") + ": expect " + name + " " + op + (s.length > 3 ? " " + s[3] : "") + " (actual " + actual + ")";
        LOGGER.info("Lads game qa {}", line);
        append("results.txt", line + "\n");
        if (ok) passed++; else failed++;
    }

    private static void fail(String what) {
        failed++;
        LOGGER.error("Lads game qa FAIL: {}", what);
        append("results.txt", "FAIL: " + what + "\n");
    }

    private static void traceRow(Minecraft mc, LocalPlayer p) {
        ticks++;
        trace.append(ticks).append(',').append(metric(mc, "sprinting")).append(',').append(metric(mc, "sentsprint")).append(',').append(metric(mc, "sneaking"))
            .append(',').append(mc.options.keyShift.isDown() ? 1 : 0).append(',').append(metric(mc, "pose")).append(',').append(String.format(Locale.ROOT, "%.4f", speed))
            .append(',').append(String.format(Locale.ROOT, "%.2f", NativeFeatures1201.lastWorldFov)).append(',').append(metric(mc, "zoom"))
            .append(',').append(String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f", p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()))
            .append(',').append(p.getHealth()).append(',').append(p.hurtTime).append(',').append(p.onGround() ? 1 : 0)
            .append(',').append(String.format(Locale.ROOT, "%.1f,%.2f,%.3f,%.4f,%.3f", Gameplay1201.lastTiltYaw, Gameplay1201.lastTiltStrength, NativeVerticalBob1201.lastDisplacement,
                mc.gameRenderer.getMainCamera().getPosition().y - p.getY(), p.getAttackAnim(0))).append('\n');
    }

    private static void endTrace() {
        if (trace == null) return;
        append("trace-" + traceName + ".csv", trace.toString());
        trace = null;
    }

    private static void append(String file, String text) {
        try {
            Files.writeString(out.resolve(file), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.warn("Lads game qa: could not write {}", file, e);
        }
    }

    private static void shot(Minecraft mc, String name) throws IOException {
        if (!name.matches("[A-Za-z0-9._-]{1,80}")) throw new IllegalArgumentException("shot name");
        Path dir = gameDir.resolve("screenshots");
        Files.createDirectories(dir);
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(dir.resolve(name + ".png"));
        }
    }
}
