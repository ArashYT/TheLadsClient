package com.thelads.core.v26_2.feature;

import com.thelads.core.v26_2.gui.AccountSwitcherScreen26;
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
public final class QaProbe26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final List<String> steps = new ArrayList<>();
    private static int index, waitTicks, readyTicks, passed, failed;
    private static boolean done;

    private QaProbe26() {}

    public static void register() {
        String script = System.getProperty("thelads.qaScript");
        if (script == null || script.isBlank()) return;
        try {
            for (String line : Files.readAllLines(Path.of(script))) if (!line.isBlank() && !line.strip().startsWith("#")) steps.add(line.strip());
        } catch (Exception e) {
            LOGGER.error("Lads qa step FAILED: reading {}: {}", script, e.toString());
            failed++;
        }
        ClientTickEvents.START_CLIENT_TICK.register(QaProbe26::tick);
    }

    private static void tick(Minecraft mc) {
        if (done) return;
        // Any screen will do once the game is up: a mod's prompt can sit over the title (Xaero's update screen), so scripts start with "screen title".
        boolean ready = mc.gui.overlay() == null && mc.gui.screen() != null || mc.level != null && mc.player != null && mc.player.tickCount >= 20;
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
            LOGGER.error("Lads qa step FAILED: {}: {}", step, e.toString());
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
                Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
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
                mc.setScreenAndShow(switch (arg) {
                    case "title" -> new TitleScreen();
                    case "pause" -> new PauseScreen(true);
                    default -> throw new IllegalArgumentException("screen: title or pause");
                });
                LOGGER.info("Lads qa screen: {}", mc.gui.screen().children().stream().filter(Button.class::isInstance).map(c -> ((Button) c).getMessage().getString()).toList());
            }
            case "press" -> mc.gui.screen().children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals(arg))
                .map(c -> (Button) c).findFirst().orElseThrow(() -> new IllegalArgumentException("press: no button " + arg))
                .onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
            case "world" -> {} // tick() held the script until the QA world was up
            case "notice" -> com.thelads.core.client.bridge.LadsGameBridge.get().displayChatMessage(arg);
            case "titlelayout" -> com.thelads.core.modules.TitleScreenModule.toggleLayout();
            case "gui" -> { mc.options.guiScale().set(Integer.parseInt(arg)); mc.resizeGui(); } // the GUI scale, 0 = auto
            case "pointer" -> { // where a real mouse would be, in window pixels: hover states and tooltips render for a shot
                String[] xy = arg.split(" ");
                var x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
                var y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
                x.setAccessible(true);
                y.setAccessible(true);
                x.setDouble(mc.mouseHandler, Double.parseDouble(xy[0]));
                y.setDouble(mc.mouseHandler, Double.parseDouble(xy[1]));
            }
            case "account" -> { // the switcher open and this offline account added and clicked
                if (!(mc.gui.screen() instanceof AccountSwitcherScreen26)) mc.setScreenAndShow(new AccountSwitcherScreen26(mc.gui.screen()));
                ((AccountSwitcherScreen26) mc.gui.screen()).ui().addOfflineAccount(arg);
            }
            case "session" -> LOGGER.info("Lads qa session: name={} uuid={} profile={} keys={} screen={} status={}", mc.getUser().getName(), mc.getUser().getProfileId(), mc.getGameProfile().name(),
                mc.getProfileKeyPairManager().getClass().getSimpleName(), mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName(),
                mc.gui.screen() instanceof AccountSwitcherScreen26 a ? a.ui().getStatusMessage() : "");
            case "srv" -> ServerListQa26.step(mc, step.split("\\s+"));
            case "quit" -> { end(); mc.stop(); }
            default -> throw new IllegalArgumentException("unknown step " + s[0]);
        }
    }
}
