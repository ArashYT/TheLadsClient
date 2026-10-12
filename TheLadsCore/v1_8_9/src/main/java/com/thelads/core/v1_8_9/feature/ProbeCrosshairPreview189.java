package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.google.gson.JsonElement;
import com.thelads.core.client.CrosshairBackground;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.CrosshairModule;
import com.thelads.core.v1_8_9.gui.CrosshairDrawingScreen189;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only (LADS_VERIFY_189_ONLY=xhair): the Crosshair Tweaks settings page with its live preview, open while the options change
 * (default cross, green circle, vanilla adaptive, rotated square with the dot at 60% opacity), then the drawing editor with its
 * preview, as lads-qa/screenshots/xhair-*.png. The module and its options are put back.
 */
final class ProbeCrosshairPreview189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    static final List<CoreProbe.Step> STEPS = new ArrayList<CoreProbe.Step>();
    private static final Map<Option, JsonElement> optionsWere = new LinkedHashMap<Option, JsonElement>();
    private static boolean started, enabledWas;
    private static long modifiedWas;
    private static LadsSettingsScreen189 menu;
    private static CrosshairDrawingScreen189 editor;

    private ProbeCrosshairPreview189() {}

    static {
        STEPS.add(mc -> {
            started = true;
            Module module = Options189.module("Crosshair Tweaks");
            enabledWas = module.isEnabled();
            modifiedWas = module.getLastModified();
            for (Option option : module.getOptions()) optionsWere.put(option, option.save());
            reset();
            module.setEnabled(true);
            // The drawing editor first, with a green 45 degree x2 look (Essential's screenshot popups would cover its preview later).
            green(); slider("Rotation", 45); module().scale.set(2.0);
            editor = new CrosshairDrawingScreen189(null);
            mc.displayGuiScreen(editor);
            LOG.info("Lads crosshair preview capture BEGIN: the drawing editor, then the settings page");
            return after(30);
        });
        STEPS.add(mc -> {
            check(mc.currentScreen == editor && editor.hasPreview(), "the drawing editor opens with its live preview at " + mc.displayWidth + "x" + mc.displayHeight);
            screenshot(mc, "xhair-editor");
            return after(10);
        });
        STEPS.add(mc -> {
            reset();
            menu = new LadsSettingsScreen189(null);
            mc.displayGuiScreen(menu);
            menu.openModule("Crosshair Tweaks");
            return after(140); // the editor shot's popup is gone
        });
        page("xhair-page-1-default", () -> { });
        page("xhair-page-2-circle-green", () -> { choice("Shape", 4); slider("Gap", 4); slider("Width", 8); slider("Height", 8); green(); });
        page("xhair-page-3-vanilla-adaptive", () -> {
            choice("Shape", 3); flag("Adaptive Color", true);
            ColorOption color = (ColorOption) module().getOption("Color");
            color.setUseGlobal(false);
            color.setColor(0xffffffff);
        });
        page("xhair-page-4-square-rotated-dot", () -> {
            choice("Shape", 2); flag("Adaptive Color", false); flag("Outline", false); flag("Center Dot", true);
            slider("Rotation", 45); slider("Opacity First Person", 60); module().scale.set(2.0); green();
        });
        // 1.9.1: the backgrounds behind the preview (CrosshairBackground): a biome at sunset, a gradient, a solid colour, your
        // own image (a picture made here, through the same copy the file dialog's pick takes) and the live game.
        page("xhair-bg-1-biome-meadow-sunset", () -> { reset(); background(CrosshairBackground.BIOME); biome("meadow"); choice("Time of Day", 3); });
        page("xhair-bg-2-gradient", () -> { background(CrosshairBackground.GRADIENT); choice("Gradient Direction", 1); });
        page("xhair-bg-3-solid", () -> background(CrosshairBackground.SOLID));
        STEPS.add(mc -> {
            try {
                java.io.File file = new java.io.File(mc.mcDataDir, "lads-qa/qa-background.png");
                file.getParentFile().mkdirs();
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1600, 900, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = image.createGraphics();
                for (int i = 0; i < 16; i++) { g.setColor(java.awt.Color.getHSBColor(i / 16f, .6f, .9f)); g.fillRect(i * 100, 0, 100, 900); }
                g.dispose();
                javax.imageio.ImageIO.write(image, "png", file);
                CrosshairBackground.use(file.toPath()); // the preview's next frame takes it
            } catch (Exception failure) { check(false, "your own image: " + failure); }
            return after(20);
        });
        page("xhair-bg-4-your-image", () -> check(module().background.getIndex() == CrosshairBackground.IMAGE
            && CrosshairBackground.imageFile(module().image.getValue()).isFile(), "your own image: chosen and kept in the shared folder"));
        page("xhair-bg-5-live", () -> background(CrosshairBackground.LIVE));
        STEPS.add(mc -> {
            editor = new CrosshairDrawingScreen189(null);
            mc.displayGuiScreen(editor);
            return after(30);
        });
        STEPS.add(mc -> { screenshot(mc, "xhair-bg-6-editor-live"); return after(120); });
        STEPS.add(mc -> { background(CrosshairBackground.BIOME); biome("nether_wastes"); return after(20); });
        STEPS.add(mc -> { screenshot(mc, "xhair-bg-7-editor-nether"); return after(10); });
        STEPS.add(mc -> { stop(); LOG.info("Lads crosshair preview capture END: frames saved"); return after(5); });
    }

    private static void reset() {
        for (Option option : module().getOptions()) if (!(option instanceof ActionOption)) option.reset();
    }

    private static void green() {
        ColorOption color = (ColorOption) module().getOption("Color");
        color.setUseGlobal(false);
        color.setColor(0xff55ff55);
    }

    /** One change on the open settings page (it stays open: the preview follows), then a photograph of it. */
    private static void page(String name, Runnable change) {
        STEPS.add(mc -> {
            change.run();
            return after(20);
        });
        STEPS.add(mc -> {
            check(mc.currentScreen == menu, "the settings page is still open for " + name);
            screenshot(mc, name);
            return after(120); // Essential's screenshot popup must be gone before the next one
        });
    }

    private static CrosshairModule module() { return (CrosshairModule) Options189.module("Crosshair Tweaks"); }
    private static void background(int mode) { module().background.setIndex(mode); }
    private static void biome(String id) { module().biome.setIndex(java.util.Arrays.asList(CrosshairBackground.biomeIds()).indexOf(id)); }
    private static void choice(String name, int index) { ((DropdownOption) module().getOption(name)).setIndex(index); }
    private static void slider(String name, double value) { ((SliderOption) module().getOption(name)).setValue(value); }
    private static void flag(String name, boolean value) { ((BoolOption) module().getOption(name)).set(value); }

    /** Puts the module and its options back (also from CoreProbe.finish after a failure). */
    static void stop() {
        if (!started) return;
        started = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == menu || mc.currentScreen == editor) mc.displayGuiScreen(null);
        for (Map.Entry<Option, JsonElement> entry : optionsWere.entrySet()) entry.getKey().load(entry.getValue());
        Module module = module();
        module.setEnabled(enabledWas);
        module.setLastModified(modifiedWas);
        ConfigManager.save();
    }
}
