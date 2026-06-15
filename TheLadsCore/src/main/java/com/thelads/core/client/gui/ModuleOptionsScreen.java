package com.thelads.core.client.gui;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.CycleOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.modules.HudModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** Per-module options screen, opened by the gear icon on a module card. */
public class ModuleOptionsScreen extends Screen {
    private final Screen parent;
    private final Module module;

    private static final int BG         = 0xDD050508; // Translucent dark obsidian black/red base
    private static final int CARD       = 0xCC180A0A; // Glassy obsidian cards with subtle crimson tint
    private static final int CARD_HOVER = 0xCC2A1010; // Lighter hover state with deep red highlight
    private static final int ACCENT     = 0xFFD32F2F; // Premium bright crimson/red
    private static final int TEXT       = 0xFFFFFFFF; // Bright white
    private static final int TEXT_DIM   = 0xFFCCCCCC; // Light gray
    private static final int OFF        = 0xFF2A0A0A; // Dark red-black toggle off
    private static final int ROW_H      = 28;

    public ModuleOptionsScreen(Screen parent, Module module) {
        super(Component.literal(module.getName() + " Options"));
        this.parent = parent;
        this.module = module;
    }

    /** Deterministic row order, shared by render and click handling. */
    private List<String> rows() {
        List<String> r = new ArrayList<>();
        r.add("enabled");
        if (module instanceof HudModule) {
            r.add("color");
        }
        for (Option o : module.getOptions()) {
            r.add("opt:" + o.getName());
        }
        return r;
    }

    private int rowY(int i) {
        return 60 + i * 34;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BG);
        
        // Animated star-field background
        drawStarfield(g, System.currentTimeMillis());

        g.text(this.font, "§l§c" + module.getName(), 30, 22, TEXT, true);
        g.text(this.font, "§7" + module.getDescription(), 30, 36, TEXT_DIM, false);

        int cx = 30;
        int cw = this.width - 60;
        List<String> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            String row = rows.get(i);
            int y = rowY(i);
            boolean hov = mouseX >= cx && mouseX < cx + cw && mouseY >= y && mouseY < y + ROW_H;
            g.fill(cx, y, cx + cw, y + ROW_H, hov ? CARD_HOVER : CARD);
            g.fill(cx, y + 3, cx + 3, y + ROW_H - 3, ACCENT);
            if (hov) {
                drawBorder(g, cx, y, cx + cw, y + ROW_H, 0xFFFF5252, 1);
            }

            if (row.equals("enabled")) {
                g.text(this.font, "§fEnabled", cx + 12, y + 10, TEXT, false);
                drawToggle(g, cx + cw - 50, y + 6, module.isEnabled());
            } else if (row.equals("color")) {
                g.text(this.font, "§fText Color", cx + 12, y + 10, TEXT, false);
                HudModule hm = (HudModule) module;
                int col = hm.isUseGlobalColor() ? HudSettings.getInstance().getGlobalColor() : hm.getCustomColor();
                g.fill(cx + cw - 29, y + 5, cx + cw - 11, y + 23, 0xFF000000);
                g.fill(cx + cw - 28, y + 6, cx + cw - 12, y + 22, col | 0xFF000000);
            } else {
                String optName = row.substring(4);
                Option o = module.getOption(optName);
                g.text(this.font, "§f" + optName, cx + 12, y + 10, TEXT, false);
                if (o instanceof BoolOption) {
                    drawToggle(g, cx + cw - 50, y + 6, ((BoolOption) o).get());
                } else if (o instanceof CycleOption) {
                    CycleOption c = (CycleOption) o;
                    int bw = 120;
                    int bx = cx + cw - bw - 6;
                    int by = y + 5;
                    g.fill(bx, by, bx + bw, by + 18, OFF);
                    g.text(this.font, c.getValue(), bx + bw / 2 - this.font.width(c.getValue()) / 2, by + 5, TEXT, false);
                } else if (o instanceof ColorOption) {
                    ColorOption co = (ColorOption) o;
                    g.fill(cx + cw - 29, y + 5, cx + cw - 11, y + 23, 0xFF000000);
                    g.fill(cx + cw - 28, y + 6, cx + cw - 12, y + 22, co.getColor() | 0xFF000000);
                    if (co.isUseGlobal()) {
                        g.text(this.font, "§0G", cx + cw - 24, y + 10, TEXT, false);
                    }
                }
            }
        }

        // Back button
        int by = rowY(rows.size()) + 6;
        boolean bh = mouseX >= cx && mouseX < cx + 100 && mouseY >= by && mouseY < by + 22;
        g.fill(cx, by, cx + 100, by + 22, bh ? 0xFF8B0000 : 0xFF551111);
        g.text(this.font, "§f< Back", cx + 10, by + 7, TEXT, true);
        if (bh) {
            drawBorder(g, cx, by, cx + 100, by + 22, 0xFFFF5252, 1);
        }

        // Reset Settings button
        boolean rh = mouseX >= cx + 110 && mouseX < cx + 220 && mouseY >= by && mouseY < by + 22;
        g.fill(cx + 110, by, cx + 220, by + 22, rh ? 0xFFB22222 : 0xFF7B0000);
        g.text(this.font, "§f↺ Reset Settings", cx + 120, by + 7, TEXT, true);
        if (rh) {
            drawBorder(g, cx + 110, by, cx + 220, by + 22, 0xFFFF5252, 1);
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawBorder(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color, int t) {
        g.fill(x1, y1,     x2,     y1 + t, color);
        g.fill(x1, y2 - t, x2,     y2,     color);
        g.fill(x1, y1,     x1 + t, y2,     color);
        g.fill(x2 - t, y1, x2,     y2,     color);
    }

    private void drawStarfield(GuiGraphicsExtractor g, long now) {
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < 60; i++) {
            int sx = rng.nextInt(this.width);
            int sy = rng.nextInt(this.height);
            float phase = rng.nextFloat() * 6.28f;
            float twinkle = 0.3f + 0.7f * (float)(0.5 + 0.5 * Math.sin(now / 1800.0 + phase));
            int a = (int)(twinkle * 55) & 0xFF;
            g.fill(sx, sy, sx + 1, sy + 1, (a << 24) | 0xFF3333);
        }
    }

    private void drawToggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        g.fill(x, y, x + 42, y + 16, on ? ACCENT : OFF);
        int knob = on ? x + 27 : x + 3;
        g.fill(knob, y + 2, knob + 12, y + 14, TEXT);
        g.text(this.font, on ? "§aON" : "§cOFF", x + (on ? 5 : 15), y + 4, TEXT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (event.button() != 0) {
            return super.mouseClicked(event, isDouble);
        }
        double mx = event.x();
        double my = event.y();
        int cx = 30;
        int cw = this.width - 60;
        List<String> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (mx < cx || mx >= cx + cw || my < y || my >= y + ROW_H) {
                continue;
            }
            String row = rows.get(i);
            if (row.equals("enabled")) {
                module.toggle();
                ConfigManager.save();
                return true;
            }
            if (row.equals("color")) {
                HudModule hm = (HudModule) module;
                int init = hm.isUseGlobalColor() ? HudSettings.getInstance().getGlobalColor() : hm.getCustomColor();
                this.minecraft.setScreen(new ColorPickerScreen(this, init, true, hm.isUseGlobalColor(),
                    (useGlobal, color) -> {
                        hm.setUseGlobalColor(useGlobal);
                        if (!useGlobal) hm.setCustomColor(color);
                        ConfigManager.save();
                    }));
                return true;
            }
            Option o = module.getOption(row.substring(4));
            if (o instanceof BoolOption) {
                ((BoolOption) o).toggle();
                module.touch();
                ConfigManager.save();
                return true;
            }
            if (o instanceof CycleOption) {
                ((CycleOption) o).cycle();
                module.touch();
                ConfigManager.save();
                return true;
            }
            if (o instanceof ColorOption) {
                final ColorOption co = (ColorOption) o;
                this.minecraft.setScreen(new ColorPickerScreen(this, co.getColor(), true, co.isUseGlobal(),
                    (useGlobal, color) -> {
                        co.setUseGlobal(useGlobal);
                        co.setColor(color);
                        ConfigManager.save();
                    }));
                return true;
            }
            return true;
        }
        int by = rowY(rows.size()) + 6;
        if (mx >= cx && mx < cx + 100 && my >= by && my < by + 22) {
            this.minecraft.setScreen(parent);
            return true;
        }
        // Reset Settings — restore every option (and colour) to default
        if (mx >= cx + 110 && mx < cx + 220 && my >= by && my < by + 22) {
            for (Option opt : module.getOptions()) {
                opt.reset();
            }
            if (module instanceof HudModule) {
                ((HudModule) module).setUseGlobalColor(true);
                ((HudModule) module).setCustomColor(0xFFFFFFFF);
            }
            ConfigManager.save();
            return true;
        }
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
