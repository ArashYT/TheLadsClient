package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * The Action Bar module: the server's action bar (ActionBar) where the player put it, as vanilla draws it (its own colours and
 * formatting, white or a record's cycling hue, its fade) unless the module's options say otherwise. Its box is vanilla's backdrop:
 * the text with 2 px around it, by default where vanilla shows it.
 */
public class ActionBarHudElement extends HudElement {
    private static final String SAMPLE = "Action Bar";
    private Object text;
    private boolean sample;

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        text = ActionBar.text();
        sample = text == null && editor;
        if (sample) text = SAMPLE;
        width = text == null ? 0 : g.actionBarWidth(text) + 4;
        height = 13;
    }

    @Override
    public void placed(LadsGraphics g, HudGroupLayout.Rect bounds) {
        double unit = g.hudUnit();
        float scale = getScale();
        // The text inside the box, in GUI units (kill banners rise above it).
        ActionBar.placed((int) ((bounds.x() + 2 * scale) / unit), (int) ((bounds.y() + 2 * scale) / unit),
            (int) Math.ceil((bounds.right() - 2 * scale) / unit), (int) Math.ceil((bounds.bottom() - 2 * scale) / unit));
    }

    @Override
    public void render(LadsGraphics g) {
        if (text == null || sample || optBool("Hide Action Bar", false)) return;
        float left = ActionBar.left(g.getGame() != null ? g.getGame().partialTick() : 0);
        draw(g, ActionBar.alpha(left), ActionBar.rgb(left));
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        if (text != null) draw(g, 255, sample ? 0xFFFFFF : ActionBar.rgb(ActionBar.left(0)));
    }

    /** Vanilla leaves out a message this faint (old versions would draw it opaque). */
    private void draw(LadsGraphics g, int alpha, int rgb) {
        if (alpha <= 8) return;
        if (optBool("Show Background", false)) {
            int background = background();
            int a = (background >>> 24) * alpha / 255;
            if (a > 0) g.fill(x, y, x + width, y + height, a << 24 | background & 0xFFFFFF);
        }
        boolean custom = optBool("Custom Text Color", false);
        if (custom) rgb = resolveColor();
        g.drawActionBar(text, x + 2, y + 2, alpha << 24 | rgb & 0xFFFFFF, optBool("Text Shadow", true), custom);
    }

    private int background() {
        Module module = ModuleManager.getInstance().getModule(moduleName);
        return module != null && module.getOption("Background") instanceof ColorOption color && !color.isUseGlobal()
            ? color.getColor() : HudSettings.getInstance().getGlobalBackground();
    }
}
