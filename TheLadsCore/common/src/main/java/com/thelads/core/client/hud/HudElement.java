package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.ChromaUtil;
import com.thelads.core.config.Module;
import com.thelads.core.config.*;
import com.thelads.core.modules.HudModule;

public abstract class HudElement {
    protected int x = 10;
    protected int y = 10;
    protected int width = 50;
    protected int height = 15;
    protected boolean enabled = true;
    protected String moduleName = null;

    private static final float[] SCALES = { 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f };

    public abstract void render(LadsGraphics g);

    protected void drawBackground(LadsGraphics g) {
        int bg = resolveBackground();
        if ((bg & 0xFF000000) != 0) {
            g.fill(x, y, x + width, y + height, bg);
        }
    }

    protected int resolveBackground() {
        int c = HudSettings.getInstance().getGlobalBackground();
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m != null) {
                Option o = m.getOption("Background");
                if (o instanceof ColorOption co) {
                    c = co.isUseGlobal() ? HudSettings.getInstance().getGlobalBackground() : co.getColor();
                }
            }
        }
        return c;
    }

    public float getScale() {
        int idx = optCycle("Size", 2);
        if (idx < 0 || idx >= SCALES.length) {
            return 1.0f;
        }
        return SCALES[idx];
    }

    public int getRenderWidth() { return Math.round(width * getScale()); }
    public int getRenderHeight() { return Math.round(height * getScale()); }

    protected int resolveColor() {
        int mode = optCycle("Color mode", 0);
        if (mode != 0) {
            return ChromaUtil.forMode(mode, this.x * 12L);
        }
        int c = HudSettings.getInstance().getGlobalColor();
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m instanceof HudModule hm) {
                c = hm.isUseGlobalColor() ? HudSettings.getInstance().getGlobalColor() : hm.getCustomColor();
            }
        }
        if ((c & 0xFF000000) == 0) {
            c |= 0xFF000000;
        }
        return c;
    }

    protected void drawCenteredText(LadsGraphics g, String text) {
        int tw = g.textWidth(text);
        int tx = x + (width - tw) / 2;
        int ty = y + (height - g.fontHeight()) / 2 + 1;
        g.drawText(text, tx, ty, resolveColor(), HudSettings.getInstance().isTextShadow());
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

    protected boolean optBool(String name, boolean def) {
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m != null) {
                Option o = m.getOption(name);
                if (o instanceof BoolOption b) {
                    return b.get();
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
                if (o instanceof DropdownOption d) {
                    return d.getIndex();
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
                if (o instanceof ColorOption co) {
                    return co.isUseGlobal() ? resolveColor() : (co.getColor() | 0xFF000000);
                }
            }
        }
        return def;
    }
}
