package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Option;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.client.ChromaUtil;
import com.thelads.core.modules.HudModule;

public abstract class HudElement {
    protected int x = 10;
    protected int y = 10;
    protected int width = 50;
    protected int height = 15;
    protected boolean enabled = true;
    // When set, this element's visibility is driven by the matching Module's
    // enabled state, so every HUD feature is toggled from the settings screen
    // and persisted by ConfigManager. Disabled by default (Modules start off).
    protected String moduleName = null;

    public abstract void render(GuiGraphicsExtractor g);

    private static final float[] SCALES = { 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f };

    protected void drawBackground(GuiGraphicsExtractor g) {
        int bg = resolveBackground();
        if ((bg & 0xFF000000) != 0) { // skip drawing a fully-transparent background
            g.fill(x, y, x + width, y + height, bg);
        }
    }

    /** Background colour: per-module "Background" option, or the global background. */
    protected int resolveBackground() {
        int c = HudSettings.getInstance().getGlobalBackground();
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m != null) {
                Option o = m.getOption("Background");
                if (o instanceof ColorOption) {
                    ColorOption co = (ColorOption) o;
                    c = co.isUseGlobal() ? HudSettings.getInstance().getGlobalBackground() : co.getColor();
                }
            }
        }
        return c;
    }

    /** Render scale from the "Size" option (25% steps). */
    public float getScale() {
        int idx = optCycle("Size", 2);
        if (idx < 0 || idx >= SCALES.length) {
            return 1.0f;
        }
        return SCALES[idx];
    }

    public int getRenderWidth() { return Math.round(width * getScale()); }
    public int getRenderHeight() { return Math.round(height * getScale()); }

    /**
     * Resolved text colour for this element: the module's custom colour, or the
     * shared global colour when the module is set to "Global". Always forces a
     * full alpha byte — passing a colour like 0xFFFFFF (alpha 00) renders fully
     * transparent in 26.1.2, which is why HUD text was previously invisible.
     */
    protected int resolveColor() {
        // Animated colour modes (chroma / fade) override the static colour.
        int mode = optCycle("Color mode", 0);
        if (mode != 0) {
            return ChromaUtil.forMode(mode, this.x * 12L);
        }
        int c = HudSettings.getInstance().getGlobalColor();
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m instanceof HudModule) {
                HudModule hm = (HudModule) m;
                c = hm.isUseGlobalColor() ? HudSettings.getInstance().getGlobalColor() : hm.getCustomColor();
            }
        }
        if ((c & 0xFF000000) == 0) {
            c |= 0xFF000000;
        }
        return c;
    }

    protected void drawCenteredText(GuiGraphicsExtractor g, String text) {
        Minecraft mc = Minecraft.getInstance();
        int tw = mc.font.width(text);
        int tx = x + (width - tw) / 2;
        int ty = y + (height - mc.font.lineHeight) / 2 + 1;
        g.text(mc.font, text, tx, ty, resolveColor(), HudSettings.getInstance().isTextShadow());
    }

    /** @deprecated colour is now controlled per-module; the colour arg is ignored. */
    @Deprecated
    protected void drawCenteredText(GuiGraphicsExtractor g, String text, int ignoredColor) {
        drawCenteredText(g, text);
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public String getModuleName() { return moduleName; }

    public boolean isEnabled() {
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            return m != null && m.isEnabled();
        }
        return enabled;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    // --- Convenience accessors for this element's module options ---

    protected boolean optBool(String name, boolean def) {
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m != null) {
                Option o = m.getOption(name);
                if (o instanceof BoolOption) {
                    return ((BoolOption) o).get();
                }
            }
        }
        return def;
    }

    protected int optCycle(String name, int def) {
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m != null) {
                Option o = m.getOption(name);
                if (o instanceof DropdownOption) {
                    return ((DropdownOption) o).getIndex();
                }
            }
        }
        return def;
    }

    protected int optColor(String name, int def) {
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m != null) {
                Option o = m.getOption(name);
                if (o instanceof ColorOption) {
                    ColorOption co = (ColorOption) o;
                    return co.isUseGlobal() ? resolveColor() : (co.getColor() | 0xFF000000);
                }
            }
        }
        return def;
    }
}
