package com.thelads.core.client.gui;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.TextOption;
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
    private Option activeDropdownOption = null;
    private double scrollOffset = 0;
    private SliderOption draggingSlider = null;

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
        g.textWithWordWrap(this.font, Component.literal(module.getDescription()), 30, 36, this.width - 60, TEXT_DIM, false);

        int cx = 30;
        int cw = this.width - 60;
        List<String> rows = rows();

        int visibleH = (this.height - 40) - 55;
        int totalH = rows.size() * 34;
        int maxScroll = Math.max(0, totalH - visibleH);
        this.scrollOffset = Math.max(0.0, Math.min(this.scrollOffset, maxScroll));

        g.enableScissor(30, 55, this.width - 30, this.height - 40);

        for (int i = 0; i < rows.size(); i++) {
            String row = rows.get(i);
            int y = 60 + i * 34 - (int)scrollOffset;
            boolean hov = mouseX >= cx && mouseX < cx + cw && mouseY >= y && mouseY < y + ROW_H && mouseY >= 55 && mouseY < this.height - 40;
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
                } else if (o instanceof DropdownOption) {
                    DropdownOption c = (DropdownOption) o;
                    int bw = 120;
                    int bx = cx + cw - bw - 6;
                    int by = y + 5;
                    g.fill(bx, by, bx + bw, by + 18, OFF);
                    String valText = c.getValue() + " ▼";
                    g.text(this.font, valText, bx + bw / 2 - this.font.width(valText) / 2, by + 5, TEXT, false);
                } else if (o instanceof SliderOption) {
                    SliderOption s = (SliderOption) o;
                    int sw = 120;
                    int sx = cx + cw - sw - 6;
                    int sy = y + 5;
                    g.fill(sx, sy, sx + sw, sy + 18, OFF);
                    double min = s.getMin();
                    double max = s.getMax();
                    double val = s.getValue();
                    int fillWidth = (int) (((val - min) / (max - min)) * sw);
                    g.fill(sx, sy, sx + fillWidth, sy + 18, ACCENT);
                    String vText = s.getStep() >= 1.0 || s.getStep() == 0 ? String.valueOf(s.getIntValue()) : String.format("%.1f", val);
                    g.text(this.font, vText, sx + sw / 2 - this.font.width(vText) / 2, sy + 5, TEXT, false);
                } else if (o instanceof TextOption) {
                    TextOption t = (TextOption) o;
                    int tw = 120;
                    int tx = cx + cw - tw - 6;
                    int ty = y + 5;
                    g.fill(tx, ty, tx + tw, ty + 18, OFF);
                    String display = t.getValue();
                    if (display.length() > 15) display = display.substring(0, 15) + "...";
                    g.text(this.font, display, tx + 4, ty + 5, TEXT, false);
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

        g.disableScissor();

        // Back button
        int backX = 30;
        int backY = this.height - 30;
        boolean bh = mouseX >= backX && mouseX < backX + 100 && mouseY >= backY && mouseY < backY + 22;
        g.fill(backX, backY, backX + 100, backY + 22, bh ? 0xFF8B0000 : 0xFF551111);
        g.text(this.font, "§f< Back", backX + 10, backY + 7, TEXT, true);
        if (bh) {
            drawBorder(g, backX, backY, backX + 100, backY + 22, 0xFFFF5252, 1);
        }

        // Reset Settings button
        int resetX = 140;
        int resetY = this.height - 30;
        boolean rh = mouseX >= resetX && mouseX < resetX + 110 && mouseY >= resetY && mouseY < resetY + 22;
        g.fill(resetX, resetY, resetX + 110, resetY + 22, rh ? 0xFFB22222 : 0xFF7B0000);
        g.text(this.font, "§f↺ Reset Settings", resetX + 10, resetY + 7, TEXT, true);
        if (rh) {
            drawBorder(g, resetX, resetY, resetX + 110, resetY + 22, 0xFFFF5252, 1);
        }

        if (activeDropdownOption instanceof DropdownOption) {
            DropdownOption activeOpt = (DropdownOption) activeDropdownOption;
            int rowIdx = rows().indexOf("opt:" + activeOpt.getName());
            if (rowIdx != -1) {
                int ry = 60 + rowIdx * 34 - (int)scrollOffset;
                int rby = ry + 5;
                int rbw = 120;
                int rbx = cx + cw - rbw - 6;
                
                String[] choices = activeOpt.getChoices();
                int itemH = 18;
                int listH = choices.length * itemH;
                boolean renderUp = (rby + 18 + listH > this.height);
                int listY = renderUp ? (rby - listH) : (rby + 18);
                
                // Render dropdown background and border
                g.pose().pushMatrix();
                g.fill(rbx, listY, rbx + rbw, listY + listH, 0xFF151515);
                drawBorder(g, rbx, listY, rbx + rbw, listY + listH, 0xFFFF5555, 1);
                
                for (int k = 0; k < choices.length; k++) {
                    int iy = listY + k * itemH;
                    boolean itemHov = mouseX >= rbx && mouseX < rbx + rbw && mouseY >= iy && mouseY < iy + itemH;
                    if (itemHov) {
                        g.fill(rbx + 1, iy + 1, rbx + rbw - 1, iy + itemH - 1, 0xFFCC0000); // Solid red highlight
                    }
                    g.text(this.font, choices[k], rbx + rbw / 2 - this.font.width(choices[k]) / 2, iy + 5, TEXT, false);
                }
                g.pose().popMatrix();
            }
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

        if (activeDropdownOption instanceof DropdownOption) {
            DropdownOption activeOpt = (DropdownOption) activeDropdownOption;
            int rowIdx = rows().indexOf("opt:" + activeOpt.getName());
            if (rowIdx != -1) {
                int ry = 60 + rowIdx * 34 - (int)scrollOffset;
                int rby = ry + 5;
                int rbw = 120;
                int rbx = cx + cw - rbw - 6;
                
                String[] choices = activeOpt.getChoices();
                int itemH = 18;
                int listH = choices.length * itemH;
                boolean renderUp = (rby + 18 + listH > this.height);
                int listY = renderUp ? (rby - listH) : (rby + 18);
                
                if (mx >= rbx && mx < rbx + rbw && my >= listY && my < listY + listH) {
                    int clickedIdx = (int) ((my - listY) / itemH);
                    if (clickedIdx >= 0 && clickedIdx < choices.length) {
                        activeOpt.setIndex(clickedIdx);
                        module.touch();
                        ConfigManager.save();
                    }
                }
            }
            activeDropdownOption = null;
            return true;
        }

        // Check Back button click: X = [30, 130], Y = [this.height - 30, this.height - 8]
        if (mx >= 30 && mx < 130 && my >= this.height - 30 && my < this.height - 8) {
            this.minecraft.setScreenAndShow(parent);
            return true;
        }

        // Check Reset button click: X = [140, 250], Y = [this.height - 30, this.height - 8]
        if (mx >= 140 && mx < 250 && my >= this.height - 30 && my < this.height - 8) {
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

        // For option row clicks: check if mouse Y is within [55, this.height - 40].
        if (my >= 55 && my < this.height - 40) {
            double adjustedY = my + scrollOffset;
            List<String> rows = rows();
            for (int i = 0; i < rows.size(); i++) {
                int y = rowY(i);
                if (mx < cx || mx >= cx + cw || adjustedY < y || adjustedY >= y + ROW_H) {
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
                    this.minecraft.setScreenAndShow(new ColorPickerScreen(this, init, true, hm.isUseGlobalColor(),
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
                if (o instanceof DropdownOption) {
                    activeDropdownOption = o;
                    return true;
                }
                if (o instanceof SliderOption) {
                    SliderOption s = (SliderOption) o;
                    int sw = 120;
                    int sx = cx + cw - sw - 6;
                    if (mx >= sx && mx < sx + sw) {
                        draggingSlider = s;
                        double pct = (mx - sx) / (double) sw;
                        s.setValue(s.getMin() + pct * (s.getMax() - s.getMin()));
                        module.touch();
                    }
                    return true;
                }
                if (o instanceof TextOption) {
                    return true;
                }
                if (o instanceof ColorOption) {
                    final ColorOption co = (ColorOption) o;
                    this.minecraft.setScreenAndShow(new ColorPickerScreen(this, co.getColor(), true, co.isUseGlobal(),
                        (useGlobal, color) -> {
                            co.setUseGlobal(useGlobal);
                            co.setColor(color);
                            ConfigManager.save();
                        }));
                    return true;
                }
                return true;
            }
        }
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingSlider != null && event.button() == 0) {
            int rowIdx = rows().indexOf("opt:" + draggingSlider.getName());
            int cx = 30;
            int cw = this.width - 60;
            int sw = 120;
            int sx = cx + cw - sw - 6;
            double pct = Math.max(0.0, Math.min(1.0, (event.x() - sx) / (double) sw));
            draggingSlider.setValue(draggingSlider.getMin() + pct * (draggingSlider.getMax() - draggingSlider.getMin()));
            module.touch();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSlider != null && event.button() == 0) {
            ConfigManager.save();
            draggingSlider = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visibleH = (this.height - 40) - 55;
        int totalH = rows().size() * 34;
        int maxScroll = Math.max(0, totalH - visibleH);
        this.scrollOffset -= scrollY * 16;
        if (this.scrollOffset < 0) this.scrollOffset = 0;
        if (this.scrollOffset > maxScroll) this.scrollOffset = maxScroll;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            this.minecraft.setScreenAndShow(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
