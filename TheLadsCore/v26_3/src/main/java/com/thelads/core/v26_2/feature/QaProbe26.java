package com.thelads.core.v26_2.feature;

import com.thelads.core.client.ladsapi.LadsApiQa;
import com.thelads.core.client.qa.QaProgress;
import com.thelads.core.v26_2.gui.LadsSettingsScreen26;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scripted QA runner for 26.3 driven by -Dthelads.qaScript.
 * Executes verification steps (ladsapi probe, settings, shot, disconnect, connect, cmd, wait, quit)
 * and updates the live QA boss bar.
 */
public final class QaProbe26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-26.3");

    private enum Phase { TITLE, SCRIPT, DONE }

    private static boolean registered, active, failed, scriptStarted, scriptDone;
    private static Phase phase = Phase.TITLE;
    private static Path gameDir;
    private static List<String[]> steps = new ArrayList<>();
    private static int index, waitTicks, titleTicks, passed, failedSteps;
    private static String pendingShot;
    private static long nextStopCheck;

    private QaProbe26() {}

    public static void register() {
        if (registered) return;
        String script = System.getProperty("thelads.qaScript");
        if (script == null || script.isBlank()) return;
        registered = true;
        ClientLifecycleEvents.CLIENT_STARTED.register(QaProbe26::begin);
        ClientTickEvents.START_CLIENT_TICK.register(QaProbe26::tick);
    }

    private static void begin(Minecraft mc) {
        try {
            gameDir = FabricLoader.getInstance().getGameDir().toRealPath();
            active = true;
            String script = System.getProperty("thelads.qaScript");
            if (script != null && !script.isBlank()) steps = parse(Path.of(script));
            LOGGER.info("Lads 26.3 QA script BEGIN: {} steps from {}", steps.size(), script);
        } catch (Exception e) {
            fail("begin", e);
        }
    }

    private static void tick(Minecraft mc) {
        if (!active || failed) return;
        long now = System.nanoTime();
        if (now >= nextStopCheck) {
            nextStopCheck = now + 250_000_000L;
            if (Files.isRegularFile(gameDir.resolve(".lads-qa-stop"))) {
                LOGGER.info("Lads 26.3 QA STOP: graceful stop request received");
                active = false;
                mc.stop();
                return;
            }
        }
        try {
            switch (phase) {
                case TITLE -> title(mc);
                case SCRIPT -> script(mc);
                case DONE -> {}
            }
        } catch (Exception e) {
            fail(phase.name().toLowerCase(Locale.ROOT), e);
        }
    }

    private static void title(Minecraft mc) {
        if (mc.level != null && mc.player != null && mc.gui.screen() == null && mc.player.tickCount >= 20) {
            LOGGER.info("Lads 26.3 QA READY: joined server/world alive and unpaused");
            phase = Phase.SCRIPT;
            return;
        }
        if (mc.gui.screen() instanceof TitleScreen && mc.gui.overlay() == null) {
            if (++titleTicks >= 65) {
                LOGGER.info("Lads 26.3 QA READY: title screen script ready");
                phase = Phase.SCRIPT;
            }
        }
    }

    private static void script(Minecraft mc) {
        if (steps.isEmpty()) {
            if (!scriptDone) {
                scriptDone = true;
                phase = Phase.DONE;
                QaProgress.begin("26.3 QA", 1);
                QaProgress.step("empty script");
                QaProgress.done();
            }
            return;
        }
        if (!scriptStarted) {
            scriptStarted = true;
            LOGGER.info("Lads 26.3 qa script BEGIN: {} steps", steps.size());
            QaProgress.begin("26.3 script", steps.size());
            QaProgress.slowAfter(600_000);
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (pendingShot != null) {
            String name = pendingShot;
            pendingShot = null;
            try {
                shot(mc, name);
                LOGGER.info("Lads 26.3 qa shot: {}", name);
                passed++;
            } catch (Exception e) {
                stepFailed("shot " + name, e);
            }
        }
        while (waitTicks == 0 && pendingShot == null && index < steps.size()) {
            String[] step = steps.get(index++);
            String label = String.join(" ", step);
            LOGGER.info("Lads 26.3 qa step {}/{}: {}", index, steps.size(), label);
            QaProgress.step(label);
            try {
                run(mc, step);
                if (!step[0].equals("wait")) {
                    if (waitTicks == 0) waitTicks = 1;
                }
                passed++;
                if (scriptDone) break;
            } catch (Exception e) {
                stepFailed(label, e);
                waitTicks = 1;
            }
        }
        if (!scriptDone && waitTicks == 0 && pendingShot == null && index >= steps.size()) {
            scriptDone = true;
            phase = Phase.DONE;
            QaProgress.done();
            LOGGER.info("Lads 26.3 qa script END: {} passed, {} failed", passed, failedSteps);
        }
    }

    private static void stepFailed(String what, Exception e) {
        failedSteps++;
        QaProgress.result(QaProgress.Result.FAIL);
        LOGGER.error("Lads 26.3 qa step FAILED: {}: {}", what, e.toString());
    }

    private static void run(Minecraft mc, String[] s) throws Exception {
        String verb = s[0];
        switch (verb) {
            case "wait" -> waitTicks = duration(arg(s, 1));
            case "shot" -> { pendingShot = arg(s, 1); waitTicks = 3; }
            case "ladsapi" -> LadsApiQa.recordProbe(s.length > 1 ? rest(s, 1) : ("step-" + index));
            case "settings" -> {
                var current = mc.gui.screen();
                var screen = new LadsSettingsScreen26(current);
                mc.setScreenAndShow(screen);
                if (s.length > 1) screen.openModule(rest(s, 1));
            }
            case "screen" -> {
                String target = arg(s, 1).toLowerCase(Locale.ROOT);
                if ("none".equals(target) || "close".equals(target)) mc.gui.setScreen(null);
                else if ("title".equals(target)) mc.setScreenAndShow(new TitleScreen());
                else if ("lads".equals(target) || "settings".equals(target)) {
                    var screen = new LadsSettingsScreen26(mc.gui.screen());
                    mc.setScreenAndShow(screen);
                    if (s.length > 2) screen.openModule(rest(s, 2));
                }
            }
            case "disconnect" -> {
                if (mc.level != null) {
                    mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                }
                mc.setScreenAndShow(new TitleScreen());
            }
            case "connect" -> {
                String address = arg(s, 1);
                if (mc.level != null) {
                    mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                }
                ServerData data = new ServerData("QA", address, ServerData.Type.OTHER);
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address), data, false, null);
            }
            case "cmd" -> {
                if (mc.player != null && mc.player.connection != null) {
                    mc.player.connection.sendCommand(rest(s, 1).replaceFirst("^/", ""));
                }
            }
            case "chat" -> {
                if (mc.player != null && mc.player.connection != null) {
                    mc.player.connection.sendChat(rest(s, 1));
                }
            }
            case "log" -> LOGGER.info("Lads 26.3 qa log: {}", rest(s, 1));
            case "quit" -> {
                LOGGER.info("Lads 26.3 qa quit requested");
                scriptDone = true;
                phase = Phase.DONE;
                QaProgress.done();
                mc.stop();
            }
            default -> LOGGER.warn("Lads 26.3 qa: unknown step '{}'", verb);
        }
    }

    static void shot(Minecraft mc, String name) throws IOException {
        Path p = Path.of(name.endsWith(".png") ? name : (name + ".png"));
        Path output = p.isAbsolute() ? p : (name.contains("/") || name.contains("\\") ? gameDir.resolve(p) : gameDir.resolve("screenshots").resolve(name + ".png"));
        Files.createDirectories(output.getParent());
        try {
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
                try {
                    image.writeToFile(output);
                    if (!p.isAbsolute() && (name.startsWith("artifacts/") || name.startsWith("artifacts\\"))) {
                        Path cwdOutput = Path.of("").toAbsolutePath().resolve(p);
                        if (!cwdOutput.normalize().equals(output.normalize())) {
                            try {
                                Files.createDirectories(cwdOutput.getParent());
                                image.writeToFile(cwdOutput);
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to write screenshot {}: {}", output, e.toString());
                } finally {
                    image.close();
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to take screenshot {}: {}", output, e.toString());
        }
    }

    private static int duration(String v) {
        v = v.toLowerCase(Locale.ROOT);
        if (v.endsWith("ms")) return Math.max(1, Integer.parseInt(v.substring(0, v.length() - 2)) / 50);
        if (v.endsWith("s")) return Math.max(1, (int) Math.round(Double.parseDouble(v.substring(0, v.length() - 1)) * 20));
        return Math.max(1, Integer.parseInt(v));
    }

    private static String arg(String[] s, int i) {
        if (i >= s.length) throw new IllegalArgumentException("'" + s[0] + "' needs argument " + i);
        return s[i];
    }

    private static String rest(String[] s, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(s, Math.min(from, s.length), s.length));
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
                else if (Character.isWhitespace(c) && !quoted) {
                    if (cur.length() > 0 || any) { tokens.add(cur.toString()); cur.setLength(0); any = false; }
                } else cur.append(c);
            }
            if (cur.length() > 0 || any) tokens.add(cur.toString());
            tokens.set(0, tokens.get(0).toLowerCase(Locale.ROOT));
            out.add(tokens.toArray(new String[0]));
        }
        return out;
    }

    private static void fail(String where, Exception e) {
        failed = true;
        QaProgress.result(QaProgress.Result.FAIL);
        QaProgress.done();
        LOGGER.error("Lads 26.3 QA FAILED: {}: {}", where, e.toString(), e);
    }
}
