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
 * ui key &lt;glfw code&gt;   ui type &lt;text&gt;   ui scroll &lt;amount&gt;   ui scale &lt;1|2&gt;   ui mouse &lt;x&gt; &lt;y&gt;   ui hover &lt;slot&gt;   ui pose   ui silence &lt;ms&gt;   ui hud click &lt;control id&gt;   ui hud ids
 * </pre>
 */
public final class UiQa1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    /**
     * QA ("ui pose"): until the next Lads screen frame has been drawn, the HUD pipeline and that screen log the translation their
     * GuiGraphics pose has (a pose leaked by a HUD overlay shifts every screen drawn after it in the same frame).
     */
    public static volatile boolean poseProbe;

    public static void logPose(String where, net.minecraft.client.gui.GuiGraphics graphics) {
        if (!poseProbe) return;
        var m = graphics.pose().last().pose();
        LOGGER.info("Lads qa pose {}: translation {} {} {}, scale {} {}", where, m.m30(), m.m31(), m.m32(), m.m00(), m.m11());
    }

    private UiQa1201() {}

    public static void register() {
        if (Boolean.getBoolean("thelads.verifyAutoWorld") || System.getProperty("thelads.qaScript") != null) QaProbe1201.registerStep("ui", UiQa1201::step);
    }

    private static boolean step(String[] a) {
        Minecraft mc = Minecraft.getInstance();
        String what = a.length > 1 ? a[1].toLowerCase(java.util.Locale.ROOT) : "";
        String rest = String.join(" ", Arrays.copyOfRange(a, Math.min(2, a.length), a.length));
        if (what.equals("hud")) return hud(mc, a);
        if (what.equals("pose")) { poseProbe = true; return true; }
        if (what.equals("silence")) { com.thelads.core.v1_20_1.hud.overlay.SignalLoss1201.qaSilence(Long.parseLong(rest) * 1_000_000L); return true; } // the server has been silent this many ms (0 = real)
        if (what.equals("scale")) { // the GUI scale (1 gives a roomy layout in the QA window, 2 the compact one)
            mc.options.guiScale().set(Integer.parseInt(rest));
            mc.resizeDisplay();
            return true;
        }
        if (what.equals("mouse") || what.equals("hover")) { // the pointer a real mouse would be at (GUI pixels), or the centre of a container slot
            double x, y;
            if (what.equals("hover")) {
                if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container))
                    throw new IllegalStateException("ui hover: no container screen open (screen " + mc.screen + ")");
                var slot = container.getMenu().slots.get(Integer.parseInt(rest));
                var access = (com.thelads.core.v1_20_1.game.misc.mixin.ContainerAccess1201) container;
                x = access.lads$leftPos() + slot.x + 8;
                y = access.lads$topPos() + slot.y + 8;
            } else {
                String[] xy = rest.split(" +");
                x = Double.parseDouble(xy[0]);
                y = Double.parseDouble(xy[1]);
            }
            var window = mc.getWindow();
            var mouse = (com.thelads.core.v1_20_1.game.misc.mixin.MouseAccess1201) mc.mouseHandler;
            mouse.lads$xpos(x * window.getScreenWidth() / window.getGuiScaledWidth());
            mouse.lads$ypos(y * window.getScreenHeight() / window.getGuiScaledHeight());
            return true;
        }
        // "ui module" opens the screen when it is not up (Esc on a module page that does not exist, such as one a third-party mod replaces, closes it).
        if (what.equals("module") && !(mc.screen instanceof LadsSettingsScreen1201)) mc.setScreen(new LadsSettingsScreen1201(null));
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
            case "scroll" -> screen.mouseScrolled(mc.getWindow().getGuiScaledWidth() * .28, mc.getWindow().getGuiScaledHeight() * .5, Double.parseDouble(rest)); // inside the options column of a module page at either GUI scale (the preview is to its right)
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
        if (op.equals("profiles")) {
            if (id.equals("open")) {
                var b = ui.controls().stream().filter(c -> c.id().equals("profiles")).findFirst().orElseThrow().bounds();
                screen.mouseClicked(b.x() + b.width() / 2.0, b.y() + b.height() / 2.0, 0);
                screen.mouseReleased(b.x() + b.width() / 2.0, b.y() + b.height() / 2.0, 0);
                return true;
            }
            if (id.equals("close")) {
                screen.keyPressed(256, 0, 0);
                return true;
            }
            if (id.equals("import")) {
                var sample = new com.google.gson.JsonObject();
                sample.addProperty("v", 1);
                var mods = new com.google.gson.JsonObject();
                var cps = new com.google.gson.JsonObject();
                cps.addProperty("enabled", true);
                mods.add("CPS", cps);
                sample.add("modules", mods);
                String code = com.thelads.core.config.ShareCode.encode(sample);
                ui.setClipboardReader(() -> code);
                var b = ui.profilesDialog().importBounds();
                screen.mouseClicked(b.x() + b.width() / 2.0, b.y() + b.height() / 2.0, 0);
                screen.mouseReleased(b.x() + b.width() / 2.0, b.y() + b.height() / 2.0, 0);
                return true;
            }
        }
        if (!op.equals("click")) throw new IllegalArgumentException("ui hud click <id> | ui hud ids | ui hud profiles <open|close|import>");
        var control = ui.controls().stream().filter(c -> c.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalStateException("ui hud click: no control '" + id + "'"));
        double x = control.bounds().x() + control.bounds().width() / 2.0, y = control.bounds().y() + control.bounds().height() / 2.0;
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
        return true;
    }
}
