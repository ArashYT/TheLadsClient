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

    private static boolean registered, active, failed, scriptStarted, scriptDone, holdPause;
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

    /** A script's "screen pause": NativeWorldVerification, which closes stray pause menus in the QA world, leaves that one open. */
    static boolean holdsPause() { return holdPause; }

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
            if ("world".equalsIgnoreCase(steps.get(index)[0]) && (mc.level == null || mc.player == null || mc.player.tickCount < 20)) {
                return;
            }
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
            LOGGER.info("Lads qa script END: {} passed, {} failed", passed, failedSteps);
        }
    }

    private static void friendso(Minecraft mc) {
        var screen = new com.thelads.core.v26_2.social.FriendsScreen26(mc.gui.screen());
        mc.setScreenAndShow(screen);
        screen.ui().setTab(com.thelads.core.client.social.ui.FriendsScreenLogic.Tab.ADD);
        screen.ui().setAddFriendInput("");
        long win = mc.getWindow().handle();
        mc.keyboardHandler.keyPress(win, 1, new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_O, 0, 0));
        mc.keyboardHandler.charTyped(win, new net.minecraft.client.input.CharacterEvent('o'));
        mc.keyboardHandler.keyPress(win, 0, new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_O, 0, 0));
        String val = screen.ui().addFriendInput();
        if (mc.gui.screen() != screen || !"o".equals(val)) {
            throw new IllegalStateException("friendso failed: screen=" + mc.gui.screen() + " input='" + val + "'");
        }
        LOGGER.info("Lads 26.3 qa friendso: screen={} input='{}' open=true", mc.gui.screen().getClass().getSimpleName(), val);
    }

    private static void stepFailed(String what, Exception e) {
        failedSteps++;
        QaProgress.result(QaProgress.Result.FAIL);
        LOGGER.error("Lads 26.3 qa step FAILED: {}: {}", what, e.toString(), e);
    }

    /** QA: a servers.dat with no stored choice, Disabled and Enabled, read by the game's own list; the title Network button's entry. The file is put back. */
    private static void packs113(Minecraft mc) throws IOException {
        Path file = com.thelads.core.shared.SharedContentPaths.serversFile();
        byte[] before = Files.exists(file) ? Files.readAllBytes(file) : null;
        net.minecraft.nbt.ListTag servers = new net.minecraft.nbt.ListTag();
        for (String kind : List.of("none", "false", "true")) {
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putString("name", "QA " + kind);
            entry.putString("ip", "qa-" + kind + ".invalid");
            if (!kind.equals("none")) entry.putBoolean("acceptTextures", kind.equals("true"));
            servers.add(entry);
        }
        net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
        root.put("servers", servers);
        net.minecraft.nbt.NbtIo.write(root, file);
        net.minecraft.client.multiplayer.ServerList list = new net.minecraft.client.multiplayer.ServerList(mc);
        list.load();
        for (int i = 0; i < list.size(); i++) LOGGER.info("Lads qa packs113: {} = {}", list.get(i).ip, list.get(i).getResourcePackStatus());
        LOGGER.info("Lads qa packs113: Network button entry = {}", new ServerData(com.thelads.core.client.LadsNetwork.NAME, com.thelads.core.client.LadsNetwork.ADDRESS, ServerData.Type.OTHER).getResourcePackStatus());
        if (before != null) Files.write(file, before); else Files.delete(file);
    }

    private static void run(Minecraft mc, String[] s) throws Exception {
        String verb = s[0];
        switch (verb) {
            case "wait" -> waitTicks = duration(arg(s, 1));
            case "shot" -> { pendingShot = arg(s, 1); waitTicks = 3; }
            case "ladsapi" -> LadsApiQa.recordProbe(s.length > 1 ? rest(s, 1) : ("step-" + index));
            case "import113" -> com.thelads.core.client.gui.ClientImportQa.step(((LadsSettingsScreen26) mc.gui.screen()).ui(), s);
            case "packs113" -> packs113(mc);
            case "settings" -> {
                var current = mc.gui.screen();
                var screen = new LadsSettingsScreen26(current);
                mc.setScreenAndShow(screen);
                if (s.length > 1) screen.openModule(rest(s, 1));
            }
            case "screen" -> {
                String target = arg(s, 1).toLowerCase(Locale.ROOT);
                if ("none".equals(target) || "close".equals(target)) { holdPause = false; mc.gui.setScreen(null); }
                else if ("title".equals(target)) mc.setScreenAndShow(new TitleScreen());
                else if ("pause".equals(target)) { holdPause = true; mc.setScreenAndShow(new net.minecraft.client.gui.screens.PauseScreen(true)); }
                else if ("multiplayer".equals(target)) mc.setScreenAndShow(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(mc.gui.screen()));
                else if ("friends".equals(target)) mc.setScreenAndShow(new com.thelads.core.v26_2.social.FriendsScreen26(mc.gui.screen()));
                else if ("screenshots".equals(target)) {
                    var sv = com.thelads.core.v26_2.feature.screenshots.ScreenshotViewer.getInstance();
                    if (sv == null) throw new IllegalStateException("ScreenshotViewer unavailable");
                    sv.getThumbnailManager().configUpdated();
                    mc.setScreenAndShow(new com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots.ManageScreenshotsScreen(mc.gui.screen()));
                }
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
            case "hold" -> { // hold tab [off]: the player list, as holding Tab (the QA bar steps aside: both sit at the top)
                if (!"tab".equalsIgnoreCase(arg(s, 1))) throw new IllegalArgumentException("hold: tab");
                boolean down = !"off".equalsIgnoreCase(s.length > 2 ? s[2] : "on");
                mc.options.keyPlayerList.setDown(down);
                com.thelads.core.client.qa.QaProgress.hide(down);
            }
            case "srv" -> ServerListQa26.step(mc, s);
            case "friendso" -> friendso(mc);
            case "seedshots" -> {
                com.thelads.core.client.qa.QaScreenshotFixtures.seed(gameDir);
                var sv = com.thelads.core.v26_2.feature.screenshots.ScreenshotViewer.getInstance();
                if (sv != null) {
                    sv.getConfig().put(com.thelads.core.v26_2.feature.screenshots.config.ScreenshotViewerOptions.SCREENSHOTS_FOLDER, gameDir.resolve("screenshots").toFile());
                }
            }
            case "restoreshots" -> com.thelads.core.client.qa.QaScreenshotFixtures.restore(gameDir);
            case "assertshots" -> {
                List<String> names = com.thelads.core.client.qa.QaScreenshotFixtures.extractManageScreenNames(mc.gui.screen());
                com.thelads.core.client.qa.QaScreenshotFixtures.assertNewestFirst(names);
                LOGGER.info("Lads 26.3 qa newest-first: {}", names);
            }
            case "log" -> LOGGER.info("Lads 26.3 qa log: {}", rest(s, 1));
            case "textui" -> textUi(mc, s);
            case "gui" -> { mc.options.guiScale().set(Integer.parseInt(arg(s, 1))); mc.resizeGui(); } // the GUI scale, 0 = auto
            case "pointer" -> { // where a real mouse would be, in window pixels: hover states and tooltips render for a shot
                pointer("xpos", Double.parseDouble(arg(s, 1)));
                pointer("ypos", Double.parseDouble(arg(s, 2)));
            }
            case "titlelayout" -> com.thelads.core.modules.TitleScreenModule.toggleLayout(); // what the title's layout toggle does
            case "titlepanorama" -> com.thelads.core.modules.TitleScreenModule.setPanorama(!"off".equals(arg(s, 1))); // the title Panorama setting: titlepanorama on | off
            case "press" -> mc.gui.screen().children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals(rest(s, 1)))
                .map(c -> (net.minecraft.client.gui.components.Button) c).findFirst().orElseThrow(() -> new IllegalArgumentException("press: no button " + rest(s, 1)))
                .onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
            case "account" -> { // account <offline name> | account ms <name>: the switcher open and that account clicked
                if (!(mc.gui.screen() instanceof com.thelads.core.v26_2.gui.AccountSwitcherScreen26)) mc.setScreenAndShow(new com.thelads.core.v26_2.gui.AccountSwitcherScreen26(mc.gui.screen()));
                com.thelads.core.client.auth.AccountSwitcherScreen ui = ((com.thelads.core.v26_2.gui.AccountSwitcherScreen26) mc.gui.screen()).ui();
                if (arg(s, 1).equals("ms")) { var ms = new com.thelads.core.client.auth.LadsAccount(arg(s, 2), "069a79f444e94726a5befca90e38aaf5", "microsoft", "0"); ui.getAccounts().add(ms); ui.selectAccount(ms); }
                else ui.addOfflineAccount(arg(s, 1));
            }
            case "session" -> LOGGER.info("Lads 26.3 qa session: name={} uuid={} profile={} keys={} screen={} status={}", mc.getUser().getName(), mc.getUser().getProfileId(), mc.getGameProfile().name(),
                mc.getProfileKeyPairManager().getClass().getSimpleName(), mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName(),
                mc.gui.screen() instanceof com.thelads.core.v26_2.gui.AccountSwitcherScreen26 a ? a.ui().getStatusMessage() : "");
            case "spworld" -> {
                String w = "qa_b1_sp";
                if (Files.exists(gameDir.resolve("saves").resolve(w).resolve("level.dat"))) {
                    mc.createWorldOpenFlows().openWorld(w, () -> {});
                } else {
                    var settings = new net.minecraft.world.level.LevelSettings(w, net.minecraft.world.level.GameType.CREATIVE,
                        new net.minecraft.world.level.LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL, false, true), true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(w, settings, new net.minecraft.world.level.levelgen.WorldOptions(135L, false, false),
                        registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                            .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.gui.screen());
                }
            }
            case "world" -> {}
            case "rmspworld" -> {
                Path sp = gameDir.resolve("saves").resolve("qa_b1_sp");
                if (Files.exists(sp)) {
                    try (var stream = Files.walk(sp)) {
                        for (Path p : stream.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                    }
                }
            }
            case "quit" -> {
                LOGGER.info("Lads 26.3 qa quit requested");
                scriptDone = true;
                phase = Phase.DONE;
                QaProgress.done();
                LOGGER.info("Lads 26.3 qa script END: {} passed, {} failed", passed + 1, failedSteps);
                LOGGER.info("Lads qa script END: {} passed, {} failed", passed + 1, failedSteps);
                mc.stop();
            }
            default -> LOGGER.warn("Lads 26.3 qa: unknown step '{}'", verb);
        }
    }

    private static void pointer(String field, double value) throws ReflectiveOperationException {
        var f = net.minecraft.client.MouseHandler.class.getDeclaredField(field);
        f.setAccessible(true);
        f.setDouble(Minecraft.getInstance().mouseHandler, value);
    }

    /**
     * The custom text boxes on SDL: textui open friends|host|quickplay|hud, textui tab add, textui flag <the screen logic's private boolean> true|false,
     * textui type <text> (keyboardHandler.charTyped, which SDL's gate does not stop, so also) textui expect on|off: the game's text input state and SDL's own.
     */
    private static void textUi(Minecraft mc, String[] s) throws ReflectiveOperationException {
        switch (arg(s, 1)) {
            case "open" -> mc.setScreenAndShow(switch (arg(s, 2)) {
                case "friends" -> new com.thelads.core.v26_2.social.FriendsScreen26(mc.gui.screen());
                case "host" -> new com.thelads.core.v26_2.social.HostScreen26(mc.gui.screen());
                case "quickplay" -> new com.thelads.core.v26_2.gui.QuickplayScreen26(mc.gui.screen());
                case "hud" -> new com.thelads.core.v26_2.gui.DraggableHudScreen26(mc.gui.screen());
                default -> throw new IllegalArgumentException("textui open: " + s[2]);
            });
            case "tab" -> ((com.thelads.core.v26_2.social.FriendsScreen26) mc.gui.screen()).ui().setTab(com.thelads.core.client.social.ui.FriendsScreenLogic.Tab.valueOf(arg(s, 2).toUpperCase(Locale.ROOT)));
            case "flag" -> {
                var screen = mc.gui.screen();
                var uiField = screen.getClass().getDeclaredField("ui");
                uiField.setAccessible(true);
                Object ui = uiField.get(screen);
                var flag = ui.getClass().getDeclaredField(arg(s, 2));
                flag.setAccessible(true);
                flag.setBoolean(ui, Boolean.parseBoolean(arg(s, 3)));
            }
            case "type" -> rest(s, 2).codePoints().forEach(c -> mc.keyboardHandler.charTyped(mc.getWindow().handle(), new net.minecraft.client.input.CharacterEvent(c)));
            case "expect" -> {
                var enabled = com.mojang.blaze3d.platform.TextInputManager.class.getDeclaredField("textInputEnabled");
                enabled.setAccessible(true);
                boolean game = enabled.getBoolean(mc.textInputManager()), sdl = org.lwjgl.sdl.SDLKeyboard.SDL_TextInputActive(mc.getWindow().handle());
                LOGGER.info("Lads 26.3 qa textinput: screen={} textInputEnabled={} SDL_TextInputActive={}", mc.gui.screen().getClass().getSimpleName(), game, sdl);
                boolean want = "on".equals(arg(s, 2));
                if (game != want || sdl != want) throw new IllegalStateException("text input should be " + s[2] + ": game " + game + ", SDL " + sdl);
            }
            default -> throw new IllegalArgumentException("textui: " + s[1]);
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
