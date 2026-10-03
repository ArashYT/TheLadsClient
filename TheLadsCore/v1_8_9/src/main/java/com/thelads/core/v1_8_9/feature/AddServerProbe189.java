package com.thelads.core.v1_8_9.feature;

import com.thelads.core.v1_8_9.mixin.GuiScreenAddServerAccessor;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenAddServer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ScreenShotHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/**
 * QA only (-Dthelads.verifyAddServer=true, the harness's LADS_VERIFY_ADDSERVER=1 on a title run): the Add Server screen over the
 * title screen, typed into through LWJGL's keyboard queue (CoreProbe.tap, so GuiScreen.handleInput and keyTyped read real key
 * events), and the name ServerNameMixin fills in. Each case's frame is saved as screenshots/addserver-*.png. The screen is
 * closed without adding anything to the server list. Synthetic keys need Essential and Resourcify off.
 */
public final class AddServerProbe189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final int[] TAB = {Keyboard.KEY_TAB, '\t'}, BACK = {Keyboard.KEY_BACK, '\b'};
    private static final ArrayDeque<int[]> keys = new ArrayDeque<>();
    private static int step, delay = 60, passed;
    private static GuiScreen title;
    private static GuiTextField name, ip;
    private static String initial;

    private AddServerProbe189() {}

    /** Every client tick (END) while -Dthelads.verifyAddServer=true. */
    public static void tick() {
        if (step < 0 || --delay > 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            // At most 10 keys per tick: LWJGL's keyboard queue holds 50 events (a key is two).
            if (!keys.isEmpty()) {
                for (int i = 0; i < 10 && !keys.isEmpty(); i++) { int[] key = keys.poll(); CoreProbe.tap(key[0], (char) key[1]); }
                delay = keys.isEmpty() ? 10 : 2; // read by the next tick's GuiScreen.handleInput, then rendered
                return;
            }
            delay = 20;
            switch (step++) {
                case 0:
                    if (!(mc.currentScreen instanceof GuiMainMenu)) { step = 0; return; }
                    title = mc.currentScreen;
                    // As GuiMultiplayer's Add Server button opens it.
                    mc.displayGuiScreen(new GuiScreenAddServer(title, new ServerData(I18n.format("selectServer.defaultName"), "", false)));
                    GuiScreenAddServerAccessor screen = (GuiScreenAddServerAccessor) mc.currentScreen;
                    name = screen.getServerNameField();
                    ip = screen.getServerIPField();
                    initial = name.getText();
                    if (!ip.isFocused()) press(TAB);
                    type("mc.hypixel.net");
                    break;
                case 1:
                    check(ip.isFocused() && !name.isFocused(), "Tab moved the typing to the address field");
                    check("Hypixel", "a known server's address names it");
                    screenshot(mc, "addserver-hypixel");
                    for (int i = 0; i < "mc.hypixel.net".length(); i++) press(BACK);
                    break;
                case 2:
                    check(initial, "erasing the address puts the untouched name back");
                    type("play.funnyservername.com:25565");
                    break;
                case 3:
                    check("Funnyservername", "an unknown domain is named after its registrable label");
                    screenshot(mc, "addserver-unknown");
                    press(TAB);
                    for (int i = 0; i < "Funnyservername".length(); i++) press(BACK);
                    type("Lads SMP");
                    press(TAB);
                    for (int i = 0; i < "play.funnyservername.com:25565".length(); i++) press(BACK);
                    type("play.cubecraft.net");
                    break;
                default:
                    check("Lads SMP", "a typed name is never replaced");
                    screenshot(mc, "addserver-typed-name");
                    mc.displayGuiScreen(title);
                    step = -1;
                    LOG.info("Lads add-server probe END: {} passed, 0 failed", passed);
            }
        } catch (Throwable failure) {
            step = -1;
            LOG.error("Lads add-server probe FAILED after {} checks", passed, failure);
            if (title != null) mc.displayGuiScreen(title);
        }
    }

    private static void press(int[] key) {
        keys.add(key);
    }

    private static void type(String text) {
        for (char c : text.toCharArray()) keys.add(new int[] {0, c});
    }

    private static void check(String expected, String description) {
        check(expected.equals(name.getText()), description + ": name '" + name.getText() + "' for address '" + ip.getText() + "', expected '" + expected + "'");
    }

    private static void check(boolean result, String description) {
        if (!result) throw new IllegalStateException(description);
        passed++;
        LOG.info("Lads add-server probe PASS {}: {} ('{}' -> '{}')", passed, description, ip.getText(), name.getText());
    }

    /** The last completed frame, as screenshots/&lt;file&gt;.png in the game folder. */
    private static void screenshot(Minecraft mc, String file) {
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, file + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }
}
