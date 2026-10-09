package com.thelads.core.v1_20_1.feature;

import com.thelads.core.v1_20_1.gui.DraggableHudScreen1201;
import com.thelads.core.v1_20_1.gui.LadsSettingsScreen1201;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only: the probe script verb {@code ui <what> [argument]} drives the open Lads settings screen (or HUD editor) through the same
 * input methods a real mouse and keyboard reach, so a script can open every category, search, click a control and photograph it.
 * <pre>
 * ui open fps|colors|mods|actions   ui module &lt;name&gt;   ui search &lt;text&gt;   ui click &lt;control id&gt;   ui ids [prefix]
 * ui key &lt;glfw code&gt;   ui type &lt;text&gt;   ui scroll &lt;amount&gt;   ui scale &lt;1|2&gt;   ui hud click &lt;control id&gt;   ui hud ids
 * </pre>
 */
public final class UiQa1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    private UiQa1201() {}

    public static void register() {
        if (Boolean.getBoolean("thelads.verifyAutoWorld") || System.getProperty("thelads.qaScript") != null) QaProbe1201.registerStep("ui", UiQa1201::step);
    }

    private static boolean step(String[] a) {
        Minecraft mc = Minecraft.getInstance();
        String what = a.length > 1 ? a[1].toLowerCase(java.util.Locale.ROOT) : "";
        String rest = String.join(" ", Arrays.copyOfRange(a, Math.min(2, a.length), a.length));
        if (what.equals("hud")) return hud(mc, a);
        if (what.equals("scale")) { // the GUI scale (1 gives a roomy layout in the QA window, 2 the compact one)
            mc.options.guiScale().set(Integer.parseInt(rest));
            mc.resizeDisplay();
            return true;
        }
        if (!(mc.screen instanceof LadsSettingsScreen1201 screen)) throw new IllegalStateException("ui: the Lads settings screen is not open (screen " + mc.screen + ")");
        var ui = screen.ui();
        switch (what) {
            case "open" -> {
                switch (rest.toLowerCase(java.util.Locale.ROOT)) {
                    case "fps" -> ui.openHudFpsDialog();
                    case "colors" -> screen.openGlobalColors();
                    case "mods" -> screen.openMods();
                    case "actions" -> screen.openDisplayActions();
                    default -> throw new IllegalArgumentException("ui open fps|colors|mods|actions");
                }
            }
            case "module" -> screen.openModule(rest);
            case "search" -> ui.setSearchQuery(rest);
            case "click" -> {
                var r = ui.controlBounds(rest);
                if (r == null) throw new IllegalStateException("ui click: no control '" + rest + "' on screen; have " + ui.controlIds());
                double x = r.x() + r.width() / 2.0, y = r.y() + r.height() / 2.0;
                screen.mouseClicked(x, y, 0);
                screen.mouseReleased(x, y, 0);
            }
            case "ids" -> LOGGER.info("Lads qa ui ids: {}", ui.controlIds().stream().filter(id -> id.startsWith(rest)).toList());
            case "key" -> { int code = Integer.parseInt(rest); screen.keyPressed(code, 0, 0); }
            case "type" -> rest.chars().forEach(c -> screen.charTyped((char) c, 0));
            case "scroll" -> screen.mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0, mc.getWindow().getGuiScaledHeight() / 2.0, Double.parseDouble(rest));
            default -> throw new IllegalArgumentException("unknown ui step '" + what + "'");
        }
        return true;
    }

    private static boolean hud(Minecraft mc, String[] a) {
        if (!(mc.screen instanceof DraggableHudScreen1201 screen)) throw new IllegalStateException("ui hud: the HUD editor is not open (screen " + mc.screen + ")");
        var ui = screen.ui();
        String op = a.length > 2 ? a[2].toLowerCase(java.util.Locale.ROOT) : "";
        String id = String.join(" ", Arrays.copyOfRange(a, Math.min(3, a.length), a.length));
        if (op.equals("ids")) {
            LOGGER.info("Lads qa ui hud controls: {}", ui.controls().stream().map(c -> c.id()).toList());
            return true;
        }
        if (!op.equals("click")) throw new IllegalArgumentException("ui hud click <id> | ui hud ids");
        var control = ui.controls().stream().filter(c -> c.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalStateException("ui hud click: no control '" + id + "'"));
        double x = control.bounds().x() + control.bounds().width() / 2.0, y = control.bounds().y() + control.bounds().height() / 2.0;
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
        return true;
    }
}
