/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.Component
 */
package com.thelads.core.client.gui;

import com.thelads.core.client.gui.ColorPickerScreen;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.CycleOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.modules.HudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ModuleOptionsScreen
extends Screen {
    private final Screen parent;
    private final Module module;
    private static final int BG = -586149856;
    private static final int CARD = -870704594;
    private static final int CARD_HOVER = -869980864;
    private static final int ACCENT = -9673729;
    private static final int TEXT = -1;
    private static final int TEXT_DIM = -5592406;
    private static final int OFF = -13421744;
    private static final int ROW_H = 28;

    public ModuleOptionsScreen(Screen parent, Module module) {
        super((Component)Component.literal((String)(module.getName() + " Options")));
        this.parent = parent;
        this.module = module;
    }

    private List<String> rows() {
        ArrayList<String> r = new ArrayList<String>();
        r.add("enabled");
        if (this.module instanceof HudModule) {
            r.add("color");
        }
        for (Option o : this.module.getOptions()) {
            r.add("opt:" + o.getName());
        }
        return r;
    }

    private int rowY(int i) {
        return 60 + i * 34;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, -586149856);
        g.text(this.font, "\u00a7l\u00a75" + this.module.getName(), 30, 22, -1, true);
        g.text(this.font, "\u00a77" + this.module.getDescription(), 30, 36, -5592406, false);
        int cx = 30;
        int cw = this.width - 60;
        List<String> rows = this.rows();
        for (int i = 0; i < rows.size(); ++i) {
            String row = rows.get(i);
            int y = this.rowY(i);
            boolean hov = mouseX >= cx && mouseX < cx + cw && mouseY >= y && mouseY < y + 28;
            g.fill(cx, y, cx + cw, y + 28, hov ? -869980864 : -870704594);
            g.fill(cx, y + 3, cx + 3, y + 28 - 3, -9673729);
            if (row.equals("enabled")) {
                g.text(this.font, "\u00a7fEnabled", cx + 12, y + 10, -1, false);
                this.drawToggle(g, cx + cw - 50, y + 6, this.module.isEnabled());
                continue;
            }
            if (row.equals("color")) {
                g.text(this.font, "\u00a7fText Color", cx + 12, y + 10, -1, false);
                HudModule hm = (HudModule)this.module;
                int col = hm.isUseGlobalColor() ? HudSettings.getInstance().getGlobalColor() : hm.getCustomColor();
                g.fill(cx + cw - 29, y + 5, cx + cw - 11, y + 23, -16777216);
                g.fill(cx + cw - 28, y + 6, cx + cw - 12, y + 22, col | 0xFF000000);
                continue;
            }
            String optName = row.substring(4);
            Option o = this.module.getOption(optName);
            g.text(this.font, "\u00a7f" + optName, cx + 12, y + 10, -1, false);
            if (o instanceof BoolOption) {
                this.drawToggle(g, cx + cw - 50, y + 6, ((BoolOption)o).get());
                continue;
            }
            if (o instanceof CycleOption) {
                CycleOption c = (CycleOption)o;
                int bw = 120;
                int bx = cx + cw - bw - 6;
                int by = y + 5;
                g.fill(bx, by, bx + bw, by + 18, -13421744);
                g.text(this.font, c.getValue(), bx + bw / 2 - this.font.width(c.getValue()) / 2, by + 5, -1, false);
                continue;
            }
            if (!(o instanceof ColorOption)) continue;
            ColorOption co = (ColorOption)o;
            g.fill(cx + cw - 29, y + 5, cx + cw - 11, y + 23, -16777216);
            g.fill(cx + cw - 28, y + 6, cx + cw - 12, y + 22, co.getColor() | 0xFF000000);
            if (!co.isUseGlobal()) continue;
            g.text(this.font, "\u00a70G", cx + cw - 24, y + 10, -1, false);
        }
        int by = this.rowY(rows.size()) + 6;
        boolean bh = mouseX >= cx && mouseX < cx + 100 && mouseY >= by && mouseY < by + 22;
        g.fill(cx, by, cx + 100, by + 22, bh ? -11184777 : -12303258);
        g.text(this.font, "\u00a7f< Back", cx + 10, by + 7, -1, true);
        boolean rh = mouseX >= cx + 110 && mouseX < cx + 220 && mouseY >= by && mouseY < by + 22;
        g.fill(cx + 110, by, cx + 220, by + 22, rh ? -7846844 : -10079437);
        g.text(this.font, "\u00a7f\u21ba Reset Settings", cx + 120, by + 7, -1, true);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawToggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        g.fill(x, y, x + 42, y + 16, on ? -9673729 : -13421744);
        int knob = on ? x + 27 : x + 3;
        g.fill(knob, y + 2, knob + 12, y + 14, -1);
        g.text(this.font, on ? "\u00a7aON" : "\u00a7cOFF", x + (on ? 5 : 15), y + 4, -1, false);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (event.button() != 0) {
            return super.mouseClicked(event, isDouble);
        }
        double mx = event.x();
        double my = event.y();
        int cx = 30;
        int cw = this.width - 60;
        List<String> rows = this.rows();
        for (int i = 0; i < rows.size(); ++i) {
            int y = this.rowY(i);
            if (mx < (double)cx || mx >= (double)(cx + cw) || my < (double)y || my >= (double)(y + 28)) continue;
            String row = rows.get(i);
            if (row.equals("enabled")) {
                this.module.toggle();
                ConfigManager.save();
                return true;
            }
            if (row.equals("color")) {
                HudModule hm = (HudModule)this.module;
                int init = hm.isUseGlobalColor() ? HudSettings.getInstance().getGlobalColor() : hm.getCustomColor();
                this.minecraft.setScreen((Screen)new ColorPickerScreen(this, init, true, hm.isUseGlobalColor(), (useGlobal, color) -> {
                    hm.setUseGlobalColor(useGlobal);
                    if (!useGlobal) {
                        hm.setCustomColor(color);
                    }
                    ConfigManager.save();
                }));
                return true;
            }
            Option o = this.module.getOption(row.substring(4));
            if (o instanceof BoolOption) {
                ((BoolOption)o).toggle();
                this.module.touch();
                ConfigManager.save();
                return true;
            }
            if (o instanceof CycleOption) {
                ((CycleOption)o).cycle();
                this.module.touch();
                ConfigManager.save();
                return true;
            }
            if (o instanceof ColorOption) {
                ColorOption co = (ColorOption)o;
                this.minecraft.setScreen((Screen)new ColorPickerScreen(this, co.getColor(), true, co.isUseGlobal(), (useGlobal, color) -> {
                    co.setUseGlobal(useGlobal);
                    co.setColor(color);
                    ConfigManager.save();
                }));
                return true;
            }
            return true;
        }
        int by = this.rowY(rows.size()) + 6;
        if (mx >= (double)cx && mx < (double)(cx + 100) && my >= (double)by && my < (double)(by + 22)) {
            this.minecraft.setScreen(this.parent);
            return true;
        }
        if (mx >= (double)(cx + 110) && mx < (double)(cx + 220) && my >= (double)by && my < (double)(by + 22)) {
            for (Option opt : this.module.getOptions()) {
                opt.reset();
            }
            if (this.module instanceof HudModule) {
                ((HudModule)this.module).setUseGlobalColor(true);
                ((HudModule)this.module).setCustomColor(-1);
            }
            ConfigManager.save();
            return true;
        }
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            this.minecraft.setScreen(this.parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

