package com.thelads.core.v26_2.feature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (-Dthelads.verifyAddServer=true, the harness's LADS_VERIFY_ADDSERVER=1 on a title run): the Add Server screen over the
 * title screen, addresses typed into its address field one character at a time, and the name ServerNameMixin fills in. Each
 * case's frame is saved as screenshots/addserver-*.png. The screen is closed without adding anything to the server list.
 */
public final class AddServerProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static int step, wait = 60, passed;
    private static Screen title;
    private static EditBox name, ip;
    private static String initial;

    private AddServerProbe() {}

    public static void register() {
        if (Boolean.getBoolean("thelads.verifyAddServer")) ClientTickEvents.END_CLIENT_TICK.register(AddServerProbe::tick);
    }

    private static void tick(Minecraft mc) {
        if (step < 0 || --wait > 0) return;
        wait = 20; // each case's frame renders before the next tick looks at it
        try {
            switch (step++) {
                case 0 -> {
                    if (!(mc.gui.screen() instanceof TitleScreen)) { step = 0; return; }
                    title = mc.gui.screen();
                    // As the multiplayer screen's Add Server button opens it.
                    Screen screen = new ManageServerScreen(title, Component.translatable("manageServer.add.title"), added -> {}, new ServerData("", "", ServerData.Type.OTHER));
                    mc.gui.setScreen(screen);
                    List<EditBox> boxes = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
                    name = boxes.get(0);
                    ip = boxes.get(1);
                    initial = name.getValue();
                    type("mc.hypixel.net");
                    check("Hypixel", "a known server's address names it");
                }
                case 1 -> {
                    screenshot(mc, "addserver-hypixel");
                    ip.setValue("");
                    check(initial, "clearing the address puts the untouched name back");
                    type("play.funnyservername.com:25565");
                    check("Funnyservername", "an unknown domain is named after its registrable label");
                }
                case 2 -> {
                    screenshot(mc, "addserver-unknown");
                    ip.setValue("");
                    type("192.168.1.20:25565");
                    check(initial, "a bare IP gets no name");
                    name.setValue("Lads SMP");
                    ip.setValue("");
                    type("play.cubecraft.net");
                    check("Lads SMP", "a typed name is never replaced");
                }
                default -> {
                    screenshot(mc, "addserver-typed-name");
                    mc.gui.setScreen(title);
                    step = -1;
                    LOGGER.info("Lads add-server probe END: {} passed, 0 failed", passed);
                }
            }
        } catch (Throwable failure) {
            step = -1;
            LOGGER.error("Lads add-server probe FAILED after {} checks", passed, failure);
            if (title != null) mc.gui.setScreen(title);
        }
    }

    /** One character at a time, as typing does (each one calls the field's responder). */
    private static void type(String text) {
        for (char c : text.toCharArray()) ip.insertText(String.valueOf(c));
    }

    private static void check(String expected, String description) {
        if (!expected.equals(name.getValue()))
            throw new IllegalStateException(description + ": name '" + name.getValue() + "' for address '" + ip.getValue() + "', expected '" + expected + "'");
        passed++;
        LOGGER.info("Lads add-server probe PASS {}: {} ('{}' -> '{}')", passed, description, ip.getValue(), name.getValue());
    }

    private static void screenshot(Minecraft mc, String file) throws Exception {
        Path output = mc.gameDirectory.toPath().resolve("screenshots").resolve(file + ".png");
        Files.createDirectories(output.getParent());
        net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try { image.writeToFile(output); }
            catch (Exception failure) { LOGGER.error("Lads add-server probe FAILED: {}", output, failure); }
            finally { image.close(); }
        });
    }
}
