/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.ChromaUtil;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.CycleOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.HudModule;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class HudElement {
    protected int x = 10;
    protected int y = 10;
    protected int width = 50;
    protected int height = 15;
    protected boolean enabled = true;
    protected String moduleName = null;
    private static final float[] SCALES = new float[]{0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    public abstract void render(GuiGraphicsExtractor var1);

    protected void drawBackground(GuiGraphicsExtractor g) {
        int bg = this.resolveBackground();
        if ((bg & 0xFF000000) != 0) {
            g.fill(this.x, this.y, this.x + this.width, this.y + this.height, bg);
        }
    }

    protected int resolveBackground() {
        Option o;
        Module m;
        int c = HudSettings.getInstance().getGlobalBackground();
        if (this.moduleName != null && (m = ModuleManager.getInstance().getModule(this.moduleName)) != null && (o = m.getOption("Background")) instanceof ColorOption) {
            ColorOption co = (ColorOption)o;
            c = co.isUseGlobal() ? HudSettings.getInstance().getGlobalBackground() : co.getColor();
        }
        return c;
    }

    public float getScale() {
        int idx = this.optCycle("Size", 2);
        if (idx < 0 || idx >= SCALES.length) {
            return 1.0f;
        }
        return SCALES[idx];
    }

    public int getRenderWidth() {
        return Math.round((float)this.width * this.getScale());
    }

    public int getRenderHeight() {
        return Math.round((float)this.height * this.getScale());
    }

    protected int resolveColor() {
        Module m;
        int mode = this.optCycle("Color mode", 0);
        if (mode != 0) {
            return ChromaUtil.forMode(mode, (long)this.x * 12L);
        }
        int c = HudSettings.getInstance().getGlobalColor();
        if (this.moduleName != null && (m = ModuleManager.getInstance().getModule(this.moduleName)) instanceof HudModule) {
            HudModule hm = (HudModule)m;
            int n = c = hm.isUseGlobalColor() ? HudSettings.getInstance().getGlobalColor() : hm.getCustomColor();
        }
        if ((c & 0xFF000000) == 0) {
            c |= 0xFF000000;
        }
        return c;
    }

    protected void drawCenteredText(GuiGraphicsExtractor g, String text) {
        Minecraft mc = Minecraft.getInstance();
        int tw = mc.font.width(text);
        int tx = this.x + (this.width - tw) / 2;
        Objects.requireNonNull(mc.font);
        int ty = this.y + (this.height - 9) / 2 + 1;
        g.text(mc.font, text, tx, ty, this.resolveColor(), HudSettings.getInstance().isTextShadow());
    }

    @Deprecated
    protected void drawCenteredText(GuiGraphicsExtractor g, String text, int ignoredColor) {
        this.drawCenteredText(g, text);
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getModuleName() {
        return this.moduleName;
    }

    public boolean isEnabled() {
        if (this.moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(this.moduleName);
            return m != null && m.isEnabled();
        }
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    protected boolean optBool(String name, boolean def) {
        Option o;
        Module m;
        if (this.moduleName != null && (m = ModuleManager.getInstance().getModule(this.moduleName)) != null && (o = m.getOption(name)) instanceof BoolOption) {
            return ((BoolOption)o).get();
        }
        return def;
    }

    protected int optCycle(String name, int def) {
        Option o;
        Module m;
        if (this.moduleName != null && (m = ModuleManager.getInstance().getModule(this.moduleName)) != null && (o = m.getOption(name)) instanceof CycleOption) {
            return ((CycleOption)o).getIndex();
        }
        return def;
    }

    protected int optColor(String name, int def) {
        Option o;
        Module m;
        if (this.moduleName != null && (m = ModuleManager.getInstance().getModule(this.moduleName)) != null && (o = m.getOption(name)) instanceof ColorOption) {
            ColorOption co = (ColorOption)o;
            return co.isUseGlobal() ? this.resolveColor() : co.getColor() | 0xFF000000;
        }
        return def;
    }
}

