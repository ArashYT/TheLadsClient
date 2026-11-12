package com.thelads.core.v1_21_11.feature;

import com.thelads.core.v1_21_11.gui.AccountSwitcherScreen12111;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** QA script (-Dthelads.qaScript), the verbs of 26.3's QaProbe26 this version's title and switcher need: wait, world, shot, screen, press, notice, titlelayout, account, session, quit. */
public final class QaProbe12111 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final List<String> steps = new ArrayList<>();
    private static int index, waitTicks, readyTicks, passed, failed;
    private static boolean done;

    private QaProbe12111() {}

    public static void register() {
        String script = System.getProperty("thelads.qaScript");
        if (script == null || script.isBlank()) return;
        try {
            for (String line : Files.readAllLines(Path.of(script))) if (!line.isBlank() && !line.strip().startsWith("#")) steps.add(line.strip());
        } catch (Exception e) {
            LOGGER.error("Lads qa step FAILED: reading {}: {}", script, e.toString());
            failed++;
        }
        ClientTickEvents.START_CLIENT_TICK.register(QaProbe12111::tick);
    }

    private static void tick(Minecraft mc) {
        if (done) return;
        // Any screen will do once the game is up: a mod's prompt can sit over the title (Xaero's update screen), so scripts start with "screen title".
        boolean ready = mc.getOverlay() == null && mc.screen != null || mc.level != null && mc.player != null && mc.player.tickCount >= 20;
        if (index == 0 && (!ready || ++readyTicks < 65)) return;
        if (waitTicks > 0) { waitTicks--; return; }
        if (index >= steps.size()) { end(); return; }
        String step = steps.get(index);
        if (step.equals("world") && (mc.player == null || mc.player.tickCount < 40)) return;
        index++;
        LOGGER.info("Lads qa step {}/{}: {}", index, steps.size(), step);
        try {
            run(mc, step);
            passed++;
        } catch (Exception e) {
            failed++;
            LOGGER.error("Lads qa step FAILED: {}: {}", step, e.toString(), e);
        }
        if (waitTicks == 0) waitTicks = 1;
    }

    private static void end() {
        done = true;
        LOGGER.info("Lads qa script END: {} passed, {} failed", passed, failed);
    }

    private static void run(Minecraft mc, String step) throws Exception {
        String[] s = step.split("\\s+", 2);
        String arg = s.length > 1 ? s[1] : "";
        switch (s[0].toLowerCase()) {
            case "wait" -> waitTicks = arg.endsWith("s") ? (int) (Double.parseDouble(arg.substring(0, arg.length() - 1)) * 20) : Integer.parseInt(arg);
            case "shot" -> {
                Path output = FabricLoader.getInstance().getGameDir().resolve(arg.endsWith(".png") ? arg : arg + ".png");
                Files.createDirectories(output.getParent());
                Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
                    try (image) {
                        image.writeToFile(output);
                        LOGGER.info("Lads qa shot: {}", arg);
                    } catch (Exception e) {
                        LOGGER.error("Lads qa step FAILED: shot {}: {}", arg, e.toString());
                    }
                });
                waitTicks = 3;
            }
            case "screen" -> {
                mc.setScreen(switch (arg) {
                    case "title" -> new TitleScreen();
                    case "pause" -> new PauseScreen(true);
                    case "options" -> new net.minecraft.client.gui.screens.options.OptionsScreen(mc.screen, mc.options);
                    case "video" -> new net.minecraft.client.gui.screens.options.VideoSettingsScreen(mc.screen, mc, mc.options);
                    case "controls" -> new net.minecraft.client.gui.screens.options.controls.ControlsScreen(mc.screen, mc.options);
                    case "multiplayer" -> new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(mc.screen);
                    case "worlds" -> new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(mc.screen);
                    case "screenshots" -> (net.minecraft.client.gui.screens.Screen) Class.forName(
                        "io.github.lgatodu47.screenshot_viewer.screen.manage_screenshots.ManageScreenshotsScreen")
                        .getConstructor(net.minecraft.client.gui.screens.Screen.class)
                        .newInstance(mc.screen);
                    default -> throw new IllegalArgumentException("screen: title, pause, options, video, controls, multiplayer or screenshots");
                });
                LOGGER.info("Lads qa screen: {}", mc.screen.children().stream().filter(Button.class::isInstance).map(c -> ((Button) c).getMessage().getString()).toList());
            }
            case "press" -> mc.screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals(arg))
                .map(c -> (Button) c).findFirst().orElseThrow(() -> new IllegalArgumentException("press: no button " + arg))
                .onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
            case "world" -> {} // tick() held the script until the QA world was up
            case "textui" -> { // sidebar friends|messages, best <uuid>, pin <world folder>, party ...
                String[] words = step.split("\\s+");
                if (!com.thelads.core.client.qa.SocialQa.step(words, 1)) throw new IllegalArgumentException("textui: " + arg);
            }
            case "notice" -> com.thelads.core.client.bridge.LadsGameBridge.get().displayChatMessage(arg);
            case "titlelayout" -> com.thelads.core.modules.TitleScreenModule.toggleLayout();
            case "titlepanorama" -> com.thelads.core.modules.TitleScreenModule.setPanorama(!"off".equals(arg)); // the title Panorama setting: titlepanorama on | off
            case "panoramaversion" -> {
                var mod = com.thelads.core.config.ModuleManager.getInstance().getModule("TitleScreen");
                if (mod != null && mod.getOption(com.thelads.core.modules.TitleScreenModule.PANORAMA_VERSION) instanceof com.thelads.core.config.DropdownOption opt) {
                    opt.select(arg);
                    com.thelads.core.config.ConfigManager.save();
                }
            }
            case "settings" -> { // the modules menu, optionally opened on a page or module: settings page:HUD | open:HUD/PvP | search:color | Hit Color
                var screen = new com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111(mc.screen);
                mc.setScreen(screen);
                if (!arg.isEmpty()) screen.openModule(arg);
            }
            case "settingsclick" -> {
                var screen = (com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111) mc.screen;
                String id = arg;
                var rect = screen.ui().controlBounds(id);
                if (rect == null) {
                    for (String cid : screen.ui().controlIds()) if (cid.startsWith(id)) { id = cid; rect = screen.ui().controlBounds(cid); break; }
                }
                if (rect == null) throw new IllegalStateException("settingsclick: no control " + arg + " in " + screen.ui().controlIds());
                var ev = new net.minecraft.client.input.MouseButtonEvent(rect.x() + rect.width() / 2.0, rect.y() + rect.height() / 2.0,
                    new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0));
                screen.mouseClicked(ev, false);
                screen.mouseReleased(ev);
                LOGGER.info("Lads qa settingsclick: {} at {},{}", id, rect.x() + rect.width() / 2, rect.y() + rect.height() / 2);
            }
            case "settingstype" -> {
                var screen = (com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111) mc.screen;
                arg.codePoints().forEach(c -> screen.charTyped(new net.minecraft.client.input.CharacterEvent(c, 0)));
                LOGGER.info("Lads qa settingstype: '{}' query='{}'", arg, screen.ui().getSearchQuery());
            }
            case "wintitle" -> {
                new Thread(() -> {
                    try {
                        Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                            "Get-Process java* | Select MainWindowTitle | ForEach-Object { $_.MainWindowTitle } | Where-Object { $_ }").redirectErrorStream(true).start();
                        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                        p.waitFor();
                        LOGGER.info("Lads qa wintitle: {}", out);
                    } catch (Exception e) {
                        LOGGER.error("Lads qa wintitle err: {}", e.toString());
                    }
                }, "LadsQaWinTitle").start();
                waitTicks = 25;
            }
            case "gui" -> { mc.options.guiScale().set(Integer.parseInt(arg)); mc.resizeDisplay(); } // the GUI scale, 0 = auto
            case "pointer" -> { // where a real mouse would be, in window pixels: hover states and tooltips render for a shot
                String[] xy = arg.split(" ");
                var mouse = (com.thelads.core.v1_21_11.feature.qa.mixin.MouseHandlerQaInvoker) mc.mouseHandler;
                mouse.ladsQaXpos(Double.parseDouble(xy[0]));
                mouse.ladsQaYpos(Double.parseDouble(xy[1]));
            }
            case "account" -> { // the switcher open and this offline account added and clicked
                if (!(mc.screen instanceof AccountSwitcherScreen12111)) mc.setScreen(new AccountSwitcherScreen12111(mc.screen));
                ((AccountSwitcherScreen12111) mc.screen).ui().addOfflineAccount(arg);
            }
            case "session" -> LOGGER.info("Lads qa session: name={} uuid={} profile={} keys={} screen={} status={}", mc.getUser().getName(), mc.getUser().getProfileId(), mc.getGameProfile().name(),
                mc.getProfileKeyPairManager().getClass().getSimpleName(), mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(),
                mc.screen instanceof AccountSwitcherScreen12111 a ? a.ui().getStatusMessage() : "");
            case "srv" -> ServerListQa12111.step(mc, step.split("\\s+"));
            case "seedshots" -> com.thelads.core.client.qa.QaScreenshotFixtures.seed(FabricLoader.getInstance().getGameDir());
            case "restoreshots" -> com.thelads.core.client.qa.QaScreenshotFixtures.restore(FabricLoader.getInstance().getGameDir());
            case "assertshots" -> {
                List<String> names = com.thelads.core.client.qa.QaScreenshotFixtures.extractManageScreenNames(mc.screen);
                com.thelads.core.client.qa.QaScreenshotFixtures.assertNewestFirst(names);
                LOGGER.info("Lads 1.21.11 qa newest-first: {}", names);
            }
            case "spworld" -> {
                String w = "qa_b1_sp";
                if (Files.exists(FabricLoader.getInstance().getGameDir().resolve("saves").resolve(w).resolve("level.dat"))) {
                    mc.createWorldOpenFlows().openWorld(w, () -> {});
                } else {
                    var settings = new net.minecraft.world.level.LevelSettings(w, net.minecraft.world.level.GameType.CREATIVE, false,
                        net.minecraft.world.Difficulty.PEACEFUL, true,
                        new net.minecraft.world.level.gamerules.GameRules(net.minecraft.world.level.WorldDataConfiguration.DEFAULT.enabledFeatures()),
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(w, settings, new net.minecraft.world.level.levelgen.WorldOptions(135L, false, false),
                        registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                            .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
                }
            }
            case "rmspworld" -> {
                Path sp = FabricLoader.getInstance().getGameDir().resolve("saves").resolve("qa_b1_sp");
                if (Files.exists(sp)) {
                    try (var stream = Files.walk(sp)) {
                        for (Path p : stream.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                    }
                }
            }
            case "quit" -> { end(); mc.stop(); }
            default -> throw new IllegalArgumentException("unknown step " + s[0]);
        }
    }
}
