/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.gui.screens.packs.PackSelectionScreen
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.Mth
 */
package com.thelads.core.client.gui;

import com.thelads.core.client.ProfileContext;
import com.thelads.core.client.gui.ColorPickerScreen;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.gui.ModuleOptionsScreen;
import com.thelads.core.client.gui.cosmetics.CosmeticsScreen;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ProfileManager;
import com.thelads.core.modules.HudModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class LadsSettingsScreen
extends Screen {
    private final Screen parent;
    private String currentTab = "Modules";
    private double scrollOffset = 0.0;
    private int maxScroll = 0;
    private static long openTime = 0L;
    private static float tabIndicatorY = 55.0f;
    private static final Map<String, Long> toggleFlash = new HashMap<String, Long>();
    private static final Map<String, Boolean> prevEnabled = new HashMap<String, Boolean>();
    private static long saveFlashTime = -1L;
    private static int sortMode = 0;
    private static boolean sortAscending = true;
    private static final String[] SORT_NAMES = new String[]{"A-Z", "Last Modified", "Favorites"};
    private EditBox searchBox;
    private static final int BG = -586544620;
    private static final int SIDEBAR = -301200358;
    private static final int ACCENT = -9673729;
    private static final int ACCENT2 = -6515713;
    private static final int CARD = -871099352;
    private static final int CARD_HOV = -870441416;
    private static final int TEXT_HI = -1;
    private static final int TEXT_MED = -5592406;
    private static final int TEXT_LO = -11184784;
    private static final int TOGGLE_ON = -13996498;
    private static final int TOGGLE_OFF = -9557462;
    private static final int DIVIDER = 0x33FFFFFF;
    private static final int[] PALETTE = new int[]{-1, -43691, -22016, -171, -11141291, -11141121, -11184641, -5635841, -43521};
    private static final int COLS = 3;
    private static final int GAP = 8;
    private static final int CARD_H = 70;
    private static final String[] TABS = new String[]{"Modules", "HUD", "Profiles", "Credits"};
    private static final String[] ICONS = new String[]{"\u2699", "\u25ad", "\u274f", "\u2665"};
    private static final Map<String, String> CAT_MAP = new HashMap<String, String>();
    private static final Map<String, Integer> CAT_COLOR;
    private static final String[][] CREDITS;

    public LadsSettingsScreen(Screen parent) {
        super((Component)Component.literal((String)"The Lads Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openTime = System.currentTimeMillis();
        tabIndicatorY = this.tabTargetY();
        this.scrollOffset = 0.0;
        int sbW = 130;
        int sbH = 16;
        int sbX = this.width - sbW - 8;
        int sbY = 16;
        this.searchBox = new EditBox(this.font, sbX, sbY, sbW, sbH, (Component)Component.literal((String)"Search modules\u2026"));
        this.searchBox.setMaxLength(40);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(-3355444);
        this.searchBox.setHint((Component)Component.literal((String)"\u00a78Search\u2026"));
        this.addRenderableWidget(this.searchBox);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        boolean cosHov;
        String saveLabel;
        boolean saveFlashing;
        long now = System.currentTimeMillis();
        float age = (float)(now - openTime) / 1000.0f;
        g.fill(0, 0, this.width, this.height, -586544620);
        this.drawStarfield(g, now);
        int sw = 120;
        g.fill(0, 0, sw, this.height, -301200358);
        g.fill(sw, 0, sw + 1, this.height, 0x33FFFFFF);
        long logoPulse = (long)(Math.abs(Math.sin((double)now / 2000.0)) * 30.0);
        int logoColor = 0xFF000000 | LadsSettingsScreen.blendInt(7103487, 10261503, (float)logoPulse / 30.0f);
        g.text(this.font, "\u00a7lTHE LADS", 14, 14, logoColor, true);
        g.text(this.font, "\u00a78CLIENT", 14, 24, -11184784, false);
        g.fill(10, 40, sw - 10, 41, 0x33FFFFFF);
        float targetY = this.tabTargetY();
        tabIndicatorY += (targetY - tabIndicatorY) * 0.18f;
        int tabIndY = (int)tabIndicatorY;
        for (int i = 0; i < 4; ++i) {
            int a = Math.max(0, 200 - i * 50);
            g.fill(0, tabIndY + i, 3 - i, tabIndY + 22 - i, a << 24 | 0x6C63FF);
        }
        int tabY = 55;
        for (int ti = 0; ti < TABS.length; ++ti) {
            boolean hov;
            String tab = TABS[ti];
            boolean sel = tab.equals(this.currentTab);
            boolean bl = hov = mouseX >= 0 && mouseX < sw && mouseY >= tabY && mouseY < tabY + 22;
            if (sel) {
                g.fill(3, tabY, sw, tabY + 22, 0x22FFFFFF);
            } else if (hov) {
                g.fill(3, tabY, sw, tabY + 22, 0x11FFFFFF);
            }
            int col = sel ? -1 : (hov ? -5592406 : -11184784);
            g.text(this.font, ICONS[ti] + " " + tab, 14, tabY + 7, col, false);
            tabY += 26;
        }
        int saveY = this.height - 68;
        boolean saveHov = mouseX >= 0 && mouseX < sw && mouseY >= saveY && mouseY < saveY + 22;
        boolean bl = saveFlashing = saveFlashTime > 0L && System.currentTimeMillis() - saveFlashTime < 900L;
        int saveBg = saveFlashing ? 0x44003300 : (saveHov ? 570477636 : 0x11FFFFFF);
        g.fill(3, saveY, sw, saveY + 22, saveBg);
        g.fill(3, saveY, 5, saveY + 22, saveFlashing ? -11141291 : (saveHov ? -12264056 : -11184784));
        String string = saveLabel = saveFlashing ? "\u2713 Saved!" : "\ud83d\udcbe Save";
        int saveLabelCol = saveFlashing ? -11141291 : (saveHov ? -1 : -11184784);
        g.text(this.font, saveLabel, 14, saveY + 7, saveLabelCol, saveFlashing);
        int cosY = this.height - 40;
        boolean bl2 = cosHov = mouseX >= 0 && mouseX < sw && mouseY >= cosY && mouseY < cosY + 22;
        if (cosHov) {
            g.fill(3, cosY, sw, cosY + 22, 0x11FFFFFF);
        }
        g.text(this.font, "\ud83d\udc55 Cosmetics", 14, cosY + 7, cosHov ? -1 : -11184784, false);
        g.text(this.font, "\u00a78v1.0.0", 14, this.height - 16, -11184784, false);
        int cx = sw + 14;
        int cw = this.width - sw - 28;
        int cy = 14;
        g.text(this.font, "\u00a7l" + this.currentTab, cx, cy, -1, true);
        g.fill(cx, cy += 16, cx + cw, cy + 1, 0x33FFFFFF);
        cy += 6;
        if ("Modules".equals(this.currentTab)) {
            this.searchBox.setVisible(true);
            int sbX = this.width - 138;
            int sbY2 = 14;
            g.fill(sbX - 4, sbY2 - 2, sbX + 134, sbY2 + 18, -1155917266);
            g.fill(sbX - 4, sbY2 + 16, sbX + 134, sbY2 + 18, -12303190);
        } else {
            this.searchBox.setVisible(false);
        }
        if ("Modules".equals(this.currentTab)) {
            this.renderModules(g, mouseX, mouseY, cx, cy, cw, now, age);
        } else if ("HUD".equals(this.currentTab)) {
            this.renderHud(g, mouseX, mouseY, cx, cy, cw);
        } else if ("Profiles".equals(this.currentTab)) {
            this.renderProfiles(g, mouseX, mouseY, cx, cy, cw);
        } else if ("Credits".equals(this.currentTab)) {
            this.renderCredits(g, mouseX, mouseY, cx, cy, cw, now);
        }
        boolean closeHov = mouseX >= this.width - 22 && mouseX < this.width - 4 && mouseY >= 4 && mouseY < 22;
        g.fill(this.width - 22, 4, this.width - 4, 22, closeHov ? -864211410 : 0x44FFFFFF);
        g.centeredText(this.font, "\u2715", this.width - 13, 9, closeHov ? -43691 : -5592406);
        super.extractRenderState(g, mouseX, mouseY, pt);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    }

    private void drawStarfield(GuiGraphicsExtractor g, long now) {
        Random rng = new Random(42L);
        for (int i = 0; i < 60; ++i) {
            int sx = rng.nextInt(this.width);
            int sy = rng.nextInt(this.height);
            float phase = rng.nextFloat() * 6.28f;
            float twinkle = 0.3f + 0.7f * (float)(0.5 + 0.5 * Math.sin((double)now / 1800.0 + (double)phase));
            int a = (int)(twinkle * 55.0f) & 0xFF;
            g.fill(sx, sy, sx + 1, sy + 1, a << 24 | 0x8888FF);
        }
    }

    private void renderModules(GuiGraphicsExtractor g, int mx, int my, int startX, int startY, int cw, long now, float age) {
        g.text(this.font, "\u00a77Sort:", startX, startY + 3, -5592406, false);
        int sortX = startX + 32;
        boolean sortHov = mx >= sortX && mx < sortX + 100 && my >= startY && my < startY + 16;
        this.drawPanel(g, sortX, startY, sortX + 100, startY + 16, sortHov ? -870441416 : -871099352);
        g.text(this.font, "\u00a7f" + SORT_NAMES[sortMode], sortX + 6, startY + 3, -1, false);
        int dirX = sortX + 104;
        this.drawPanel(g, dirX, startY, dirX + 16, startY + 16, -870441416);
        g.centeredText(this.font, sortAscending ? "\u25b2" : "\u25bc", dirX + 8, startY + 3, -1);
        String filter = this.searchBox != null ? this.searchBox.getValue().toLowerCase(Locale.ROOT) : "";
        List<Module> modules = this.sortedModules(filter);
        int top = startY + 22;
        int cardW = (cw - 16) / 3;
        int rows = (modules.size() + 3 - 1) / 3;
        this.maxScroll = Math.max(0, rows * 78 - (this.height - top - 8));
        int baseY = top - (int)this.scrollOffset;
        for (int i = 0; i < modules.size(); ++i) {
            int col = i % 3;
            int row = i / 3;
            int cx = startX + col * (cardW + 8);
            int cy = baseY + row * 78;
            if (cy + 70 < top - 4 || cy > this.height - 8) continue;
            float delay = (float)i * 0.035f;
            float t = Mth.clamp((float)((age - delay) / 0.28f), (float)0.0f, (float)1.0f);
            float ease = 1.0f - (1.0f - t) * (1.0f - t);
            int ySlide = (int)((1.0f - ease) * 28.0f);
            int aScale = (int)(ease * 255.0f);
            if (aScale <= 0) continue;
            this.drawModuleCard(g, modules.get(i), cx, cy + ySlide, cardW, 70, mx, my, now, aScale);
        }
        if (modules.isEmpty() && !filter.isEmpty()) {
            g.centeredText(this.font, "No modules match: " + filter, startX + cw / 2, top + 30, -11184784);
        }
    }

    private void drawModuleCard(GuiGraphicsExtractor g, Module mod, int cx, int cy, int cw, int ch, int mx, int my, long now, int alpha) {
        String cat;
        boolean hover = mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;
        boolean enabled = mod.isEnabled();
        Boolean prev = prevEnabled.get(mod.getName());
        if (prev != null && prev != enabled) {
            toggleFlash.put(mod.getName(), now);
        }
        prevEnabled.put(mod.getName(), enabled);
        long flashElapsed = now - toggleFlash.getOrDefault(mod.getName(), 0L);
        boolean flashing = flashElapsed < 350L;
        float flashT = flashing ? 1.0f - (float)flashElapsed / 350.0f : 0.0f;
        int flashA = (int)(flashT * 80.0f);
        int bgColor = LadsSettingsScreen.blendWithAlpha(hover ? -870441416 : -871099352, alpha);
        this.drawPanel(g, cx, cy, cx + cw, cy + ch, bgColor);
        if (flashing) {
            int flashColor = enabled ? flashA << 24 | 0xFF44 : flashA << 24 | 0xFF4444;
            g.fill(cx, cy, cx + cw, cy + ch, flashColor);
        }
        if (hover) {
            float glow = 0.5f + 0.5f * (float)Math.sin((double)now / 280.0);
            int glowA = (int)(glow * 120.0f);
            this.drawBorder(g, cx, cy, cx + cw, cy + ch, glowA << 24 | 0x6C63FF, 1);
        }
        if (!(cat = CAT_MAP.getOrDefault(mod.getName(), "")).isEmpty()) {
            int catCol = CAT_COLOR.getOrDefault(cat, -9673729);
            int catA = (int)((float)alpha * 0.7f) & 0xFF;
            g.fill(cx, cy, cx + 3, cy + ch, catA << 24 | catCol & 0xFFFFFF);
        }
        String name = mod.getName();
        int nameX = cx + (cat.isEmpty() ? 8 : 10);
        g.text(this.font, "\u00a7f" + name, nameX, cy + 8, LadsSettingsScreen.blendWithAlpha(-1, alpha), true);
        if (!cat.isEmpty()) {
            int catCol2 = CAT_COLOR.getOrDefault(cat, -11184784);
            g.text(this.font, "\u00a78" + cat, nameX, cy + 20, LadsSettingsScreen.blendWithAlpha(catCol2, (int)((float)alpha * 0.6f)), false);
        }
        String star = mod.isFavorite() ? "\u00a7e\u2605" : "\u00a78\u2606";
        g.text(this.font, star, cx + cw - 14, cy + 6, -1, false);
        if (mod instanceof HudModule) {
            HudModule hm = (HudModule)mod;
            boolean global = hm.isUseGlobalColor();
            int swCol = global ? -11184777 : hm.getCustomColor() | 0xFF000000;
            this.drawSwatch(g, cx + cw - 14, cy + 20, swCol, global);
        }
        int barY = cy + ch - 16;
        int barColor = LadsSettingsScreen.blendWithAlpha(enabled ? -13996498 : -9557462, alpha);
        this.drawPanel(g, cx + 3, barY, cx + cw - 3, barY + 13, barColor);
        float dotProgress = enabled ? 1.0f : 0.0f;
        Long ft = toggleFlash.get(mod.getName());
        if (ft != null) {
            float f = Mth.clamp((float)((float)flashElapsed / 200.0f), (float)0.0f, (float)1.0f);
        }
        int dotRange = cw - 24;
        int dotX = cx + 6 + (int)(dotProgress * (float)dotRange * 0.3f);
        g.fill(dotX, barY + 3, dotX + 6, barY + 10, LadsSettingsScreen.blendWithAlpha(enabled ? -7798904 : -30584, alpha));
        String label = enabled ? "ENABLED" : "DISABLED";
        int lblX = cx + cw / 2 - this.font.width(label) / 2;
        g.text(this.font, "\u00a7f" + label, lblX, barY + 2, LadsSettingsScreen.blendWithAlpha(-1, alpha), false);
    }

    private void renderHud(GuiGraphicsExtractor g, int mx, int my, int sx, int sy, int cw) {
        int rh = 34;
        int sp = 6;
        int y = sy;
        y = this.renderHudRow(g, mx, my, sx, y, cw, rh, "Global Text Color", "Used by modules set to Global");
        this.drawSwatch(g, sx + cw - 28, y - rh + 8 - sp, HudSettings.getInstance().getGlobalColor() | 0xFF000000, false);
        g.text(this.font, "\u00a77\u21ba", sx + cw - 48, y - rh + 12 - sp, -11184784, false);
        y += sp;
        y = this.renderHudRow(g, mx, my, sx, y, cw, rh, "Global Background", "Box behind modules set to Global");
        this.drawSwatch(g, sx + cw - 28, y - rh + 8 - sp, HudSettings.getInstance().getGlobalBackground() | 0xFF000000, false);
        g.text(this.font, "\u00a77\u21ba", sx + cw - 48, y - rh + 12 - sp, -11184784, false);
        y += sp;
        boolean shadow = HudSettings.getInstance().isTextShadow();
        y = this.renderHudRow(g, mx, my, sx, y, cw, rh, "Text Shadow", "Drop shadow on HUD text");
        this.drawToggleSwitch(g, sx + cw - 52, y - rh + 8 - sp, shadow);
        boolean hovEdit = mx >= sx && mx < sx + cw && my >= (y += sp) && my < y + rh;
        this.drawPanel(g, sx, y, sx + cw, y + rh, hovEdit ? -870441416 : -871099352);
        g.fill(sx, y + 4, sx + 3, y + rh - 4, -9673729);
        g.text(this.font, "\u00a7f\u270e Edit HUD Layout", sx + 10, y + 8, -1, true);
        g.text(this.font, "Drag and reposition overlays", sx + 10, y + 20, -5592406, false);
        this.drawPanel(g, sx, y += rh + sp, sx + cw, y + rh, -871099352);
        g.fill(sx, y + 4, sx + 3, y + rh - 4, -9673729);
        g.text(this.font, "\u00a7fFade Playlist", sx + 10, y + 8, -1, true);
        g.text(this.font, "Colors for the Fade mode (click to remove)", sx + 10, y + 20, -5592406, false);
        List<Integer> pl = HudSettings.getInstance().getFadePlaylist();
        int swx = sx + 10;
        int swy = y + rh + 2;
        for (int i = 0; i < pl.size() && i < 12; ++i) {
            this.drawSwatch(g, swx, swy, (int)(pl.get(i) | 0xFF000000), false);
            swx += 20;
        }
        g.fill(swx - 1, swy - 1, swx + 17, swy + 17, -16777216);
        g.fill(swx, swy, swx + 16, swy + 16, -870441416);
        g.text(this.font, "\u00a7f+", swx + 5, swy + 4, -1, false);
    }

    private int renderHudRow(GuiGraphicsExtractor g, int mx, int my, int sx, int y, int cw, int rh, String label, String desc) {
        boolean hov = mx >= sx && mx < sx + cw && my >= y && my < y + rh;
        this.drawPanel(g, sx, y, sx + cw, y + rh, hov ? -870441416 : -871099352);
        g.fill(sx, y + 4, sx + 3, y + rh - 4, -9673729);
        g.text(this.font, "\u00a7f" + label, sx + 10, y + 8, -1, true);
        g.text(this.font, desc, sx + 10, y + 20, -5592406, false);
        return y + rh;
    }

    private void drawToggleSwitch(GuiGraphicsExtractor g, int x, int y, boolean on) {
        g.fill(x, y, x + 42, y + 14, on ? -14001110 : -12965334);
        int dotX = on ? x + 27 : x + 3;
        g.fill(dotX, y + 2, dotX + 12, y + 12, on ? -11141291 : -43691);
    }

    private void renderCredits(GuiGraphicsExtractor g, int mx, int my, int sx, int sy, int cw, long now) {
        g.text(this.font, "\u00a77Reimplementations of open-source mods.", sx, sy, -5592406, false);
        int y = sy + 18;
        int cardH = 44;
        int sp = 6;
        for (int i = 0; i < CREDITS.length; ++i) {
            String[] cr = CREDITS[i];
            boolean hov = mx >= sx && mx < sx + cw && my >= y && my < y + cardH;
            this.drawPanel(g, sx, y, sx + cw, y + cardH, hov ? -870441416 : -871099352);
            if (hov) {
                float glow = 0.5f + 0.5f * (float)Math.sin((double)now / 300.0);
                int a = (int)(glow * 255.0f);
                g.fill(sx, y + 3, sx + 3, y + cardH - 3, a << 24 | 0x6C63FF);
            } else {
                g.fill(sx, y + 3, sx + 3, y + cardH - 3, -9673729);
            }
            g.text(this.font, "\u00a7b" + cr[0], sx + 10, y + 6, -1, true);
            g.text(this.font, "\u00a77by \u00a7f" + cr[1], sx + 12 + this.font.width(cr[0]), y + 6, -5592406, false);
            g.text(this.font, cr[2], sx + 10, y + 20, -11184784, false);
            y += cardH + sp;
        }
    }

    private void renderProfiles(GuiGraphicsExtractor g, int mx, int my, int sx, int sy, int cw) {
        String ctx = ProfileContext.current(Minecraft.getInstance());
        g.text(this.font, "\u00a77In: \u00a7f" + ProfileContext.label(Minecraft.getInstance()), sx, sy + 2, -5592406, false);
        int by = sy + 16;
        boolean nh = mx >= sx && mx < sx + cw && my >= by && my < by + 22;
        this.drawPanel(g, sx, by, sx + cw, by + 22, nh ? -870441416 : -871099352);
        g.fill(sx, by + 3, sx + 3, by + 19, -11141291);
        g.text(this.font, "\u00a7a+ Save current HUD as new profile", sx + 10, by + 7, -1, false);
        List<String> names = ProfileManager.get().getProfileNames();
        int rowH = 36;
        int listTop = by + 30;
        this.maxScroll = Math.max(0, names.size() * rowH - (this.height - listTop - 10));
        int y = listTop - (int)this.scrollOffset;
        int applyX = sx + cw - 180;
        int bindX = sx + cw - 118;
        int delX = sx + cw - 28;
        for (String name : names) {
            if (y + rowH >= listTop && y < this.height - 10) {
                this.drawPanel(g, sx, y, sx + cw, y + rowH - 4, -871099352);
                g.text(this.font, "\u00a7f" + name, sx + 10, y + 4, -1, true);
                boolean bound = name.equals(ProfileManager.get().getBinding(ctx));
                g.text(this.font, bound ? "\u00a7a\u25cf bound" : "\u00a78not bound", sx + 10, y + 18, -11184784, false);
                this.drawMiniBtn(g, applyX, y + 6, 56, "Apply", mx, my, -9673729);
                this.drawMiniBtn(g, bindX, y + 6, 56, bound ? "Unbind" : "Bind", mx, my, -12303258);
                this.drawMiniBtn(g, delX, y + 6, 22, "\u2715", mx, my, -8573394);
            }
            y += rowH;
        }
        if (names.isEmpty()) {
            g.text(this.font, "\u00a78No profiles yet.", sx, listTop + 4, -11184784, false);
        }
    }

    private void drawPanel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    private void drawBorder(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color, int t) {
        g.fill(x1, y1, x2, y1 + t, color);
        g.fill(x1, y2 - t, x2, y2, color);
        g.fill(x1, y1, x1 + t, y2, color);
        g.fill(x2 - t, y1, x2, y2, color);
    }

    private void drawSwatch(GuiGraphicsExtractor g, int x, int y, int color, boolean global) {
        g.fill(x - 1, y - 1, x + 17, y + 17, -16777216);
        g.fill(x, y, x + 16, y + 16, color);
        if (global) {
            g.text(this.font, "\u00a70G", x + 5, y + 4, -1, false);
        }
    }

    private void drawMiniBtn(GuiGraphicsExtractor g, int x, int y, int w, String label, int mx, int my, int color) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 18;
        g.fill(x, y, x + w, y + 18, hov ? color | 0x33FFFFFF : color);
        g.text(this.font, "\u00a7f" + label, x + w / 2 - this.font.width(label) / 2, y + 5, -1, false);
    }

    private static int blendInt(int a, int b, float t) {
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        int r = ar + (int)((float)(br - ar) * t);
        int gv = ag + (int)((float)(bg - ag) * t);
        int bv = ab + (int)((float)(bb - ab) * t);
        return r << 16 | gv << 8 | bv;
    }

    private static int blendWithAlpha(int color, int alpha) {
        int origA = color >> 24 & 0xFF;
        int newA = origA * alpha / 255;
        return newA << 24 | color & 0xFFFFFF;
    }

    private float tabTargetY() {
        for (int i = 0; i < TABS.length; ++i) {
            if (!TABS[i].equals(this.currentTab)) continue;
            return 55.0f + (float)i * 26.0f;
        }
        return 55.0f;
    }

    private List<Module> sortedModules(String filter) {
        ArrayList<Module> list = new ArrayList<Module>();
        for (Module m2 : ModuleManager.getInstance().getModules()) {
            if (!filter.isEmpty() && !m2.getName().toLowerCase(Locale.ROOT).contains(filter) && !CAT_MAP.getOrDefault(m2.getName(), "").toLowerCase(Locale.ROOT).contains(filter)) continue;
            list.add(m2);
        }
        Comparator<Module> cmp = switch (sortMode) {
            case 1 -> Comparator.comparingLong(Module::getLastModified).reversed();
            case 2 -> Comparator.comparingInt(m -> m.isFavorite() ? 0 : 1).thenComparing(Module::getName, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER);
        };
        list.sort(cmp);
        if (!sortAscending) {
            Collections.reverse(list);
        }
        return list;
    }

    private int gridCardW(int cw) {
        return (cw - 16) / 3;
    }

    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if ("Modules".equals(this.currentTab) || "Profiles".equals(this.currentTab)) {
            this.scrollOffset = Mth.clamp((double)(this.scrollOffset - va * 20.0), (double)0.0, (double)this.maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, ha, va);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean active) {
        int sy;
        int cw2;
        double mx = event.x();
        double my = event.y();
        if (event.button() != 0) {
            return super.mouseClicked(event, active);
        }
        int sw = 120;
        if (mx >= (double)(this.width - 22) && mx < (double)(this.width - 4) && my >= 4.0 && my < 22.0) {
            this.onClose();
            return true;
        }
        int saveYClick = this.height - 68;
        if (mx >= 0.0 && mx < (double)sw && my >= (double)saveYClick && my < (double)(saveYClick + 22)) {
            ConfigManager.save();
            saveFlashTime = System.currentTimeMillis();
            return true;
        }
        int cosY = this.height - 40;
        if (mx >= 0.0 && mx < (double)sw && my >= (double)cosY && my < (double)(cosY + 22)) {
            this.minecraft.setScreen((Screen)new CosmeticsScreen(this));
            return true;
        }
        int tabY = 55;
        for (String tab : TABS) {
            if (mx >= 0.0 && mx < (double)sw && my >= (double)tabY && my < (double)(tabY + 22)) {
                this.currentTab = tab;
                this.scrollOffset = 0.0;
                return true;
            }
            tabY += 26;
        }
        if ("HUD".equals(this.currentTab)) {
            int cx = sw + 14;
            int cw = this.width - sw - 28;
            int rh = 34;
            int sp = 6;
            int yy = 36;
            if (mx >= (double)(cx + cw - 50) && mx < (double)(cx + cw - 34) && my >= (double)(yy + 8) && my < (double)(yy + 24)) {
                HudSettings.getInstance().setGlobalColor(-1);
                ConfigManager.save();
                return true;
            }
            if (mx >= (double)(cx + cw - 30) && mx < (double)(cx + cw - 12) && my >= (double)(yy + 7) && my < (double)(yy + 23)) {
                this.minecraft.setScreen((Screen)new ColorPickerScreen(this, HudSettings.getInstance().getGlobalColor(), false, false, (u, c) -> {
                    HudSettings.getInstance().setGlobalColor(c);
                    ConfigManager.save();
                }));
                return true;
            }
            yy += rh + sp;
            if (mx >= (double)(cx + cw - 50) && mx < (double)(cx + cw - 34) && my >= (double)(yy + 8) && my < (double)(yy + 24)) {
                HudSettings.getInstance().setGlobalBackground(Integer.MIN_VALUE);
                ConfigManager.save();
                return true;
            }
            if (mx >= (double)(cx + cw - 30) && mx < (double)(cx + cw - 12) && my >= (double)(yy + 7) && my < (double)(yy + 23)) {
                this.minecraft.setScreen((Screen)new ColorPickerScreen(this, HudSettings.getInstance().getGlobalBackground(), false, false, (u, c) -> {
                    HudSettings.getInstance().setGlobalBackground(c);
                    ConfigManager.save();
                }));
                return true;
            }
            yy += rh + sp;
            if (mx >= (double)(cx + cw - 52) && mx < (double)(cx + cw - 10) && my >= (double)(yy + 8) && my < (double)(yy + 22)) {
                HudSettings.getInstance().setTextShadow(!HudSettings.getInstance().isTextShadow());
                ConfigManager.save();
                return true;
            }
            yy += rh + sp;
            if (mx >= (double)cx && mx < (double)(cx + cw) && my >= (double)yy && my < (double)(yy + rh)) {
                this.minecraft.setScreen((Screen)new DraggableHudScreen());
                return true;
            }
            List<Integer> pl = HudSettings.getInstance().getFadePlaylist();
            int fswx = cx + 10;
            int fswy = (yy += rh + sp) + rh + 2;
            for (int i = 0; i < pl.size() && i < 12; ++i) {
                if (mx >= (double)fswx && mx < (double)(fswx + 16) && my >= (double)fswy && my < (double)(fswy + 16)) {
                    pl.remove(i);
                    ConfigManager.save();
                    return true;
                }
                fswx += 20;
            }
            if (mx >= (double)fswx && mx < (double)(fswx + 16) && my >= (double)fswy && my < (double)(fswy + 16)) {
                this.minecraft.setScreen((Screen)new ColorPickerScreen(this, -65536, false, false, (u, c) -> {
                    HudSettings.getInstance().getFadePlaylist().add(c);
                    ConfigManager.save();
                }));
                return true;
            }
        }
        if ("Modules".equals(this.currentTab)) {
            int cx = sw + 14;
            cw2 = this.width - sw - 28;
            sy = 36;
            String filter = this.searchBox != null ? this.searchBox.getValue().toLowerCase(Locale.ROOT) : "";
            List<Module> modules = this.sortedModules(filter);
            int sortX = cx + 32;
            if (mx >= (double)sortX && mx < (double)(sortX + 100) && my >= (double)sy && my < (double)(sy + 16)) {
                sortMode = (sortMode + 1) % SORT_NAMES.length;
                return true;
            }
            int dirX = sortX + 104;
            if (mx >= (double)dirX && mx < (double)(dirX + 16) && my >= (double)sy && my < (double)(sy + 16)) {
                sortAscending = !sortAscending;
                return true;
            }
            int top = sy + 22;
            int cardW = this.gridCardW(cw2);
            int baseY = top - (int)this.scrollOffset;
            for (int i = 0; i < modules.size(); ++i) {
                Module mod = modules.get(i);
                int col = i % 3;
                int row = i / 3;
                int x = cx + col * (cardW + 8);
                int y = baseY + row * 78;
                if (mx < (double)x || mx >= (double)(x + cardW) || my < (double)y || my >= (double)(y + 70)) continue;
                if (mx >= (double)(x + cardW - 17) && my < (double)(y + 18)) {
                    mod.setFavorite(!mod.isFavorite());
                    ConfigManager.save();
                    return true;
                }
                if (mod instanceof HudModule) {
                    HudModule hm = (HudModule)mod;
                    if (mx >= (double)(x + cardW - 14) && mx < (double)(x + cardW) && my >= (double)(y + 18) && my < (double)(y + 36)) {
                        int init = hm.isUseGlobalColor() ? HudSettings.getInstance().getGlobalColor() : hm.getCustomColor();
                        this.minecraft.setScreen((Screen)new ColorPickerScreen(this, init, true, hm.isUseGlobalColor(), (u, c) -> {
                            hm.setUseGlobalColor(u);
                            if (!u) {
                                hm.setCustomColor(c);
                            }
                            ConfigManager.save();
                        }));
                        return true;
                    }
                }
                if (my >= (double)(y + 70 - 16)) {
                    mod.toggle();
                    toggleFlash.put(mod.getName(), System.currentTimeMillis());
                    ConfigManager.save();
                    return true;
                }
                if (mod.getName().equals("TexturePacks")) {
                    this.minecraft.setScreen((Screen)new PackSelectionScreen(this.minecraft.getResourcePackRepository(), repository -> {
                        this.minecraft.options.updateResourcePacks(repository);
                        this.minecraft.setScreen((Screen)this);
                    }, this.minecraft.getResourcePackDirectory(), (Component)Component.translatable((String)"resourcePack.title")));
                    return true;
                }
                this.minecraft.setScreen((Screen)new ModuleOptionsScreen(this, mod));
                return true;
            }
        }
        if ("Profiles".equals(this.currentTab)) {
            int cx = sw + 14;
            cw2 = this.width - sw - 28;
            sy = 36;
            String ctx = ProfileContext.current(Minecraft.getInstance());
            int by = sy + 16;
            if (mx >= (double)cx && mx < (double)(cx + cw2) && my >= (double)by && my < (double)(by + 22)) {
                ProfileManager.get().saveAs(ProfileManager.get().nextDefaultName());
                return true;
            }
            List<String> names = ProfileManager.get().getProfileNames();
            int rowH = 36;
            int y = by + 30 - (int)this.scrollOffset;
            int applyX = cx + cw2 - 180;
            int bindX = cx + cw2 - 118;
            int delX = cx + cw2 - 28;
            for (String name : names) {
                if (my >= (double)(y + 6) && my < (double)(y + 24)) {
                    if (mx >= (double)applyX && mx < (double)(applyX + 56)) {
                        ProfileManager.get().apply(name);
                        return true;
                    }
                    if (mx >= (double)bindX && mx < (double)(bindX + 56)) {
                        boolean b = name.equals(ProfileManager.get().getBinding(ctx));
                        ProfileManager.get().bind(ctx, b ? null : name);
                        return true;
                    }
                    if (mx >= (double)delX && mx < (double)(delX + 22)) {
                        ProfileManager.get().delete(name);
                        return true;
                    }
                }
                y += rowH;
            }
        }
        return super.mouseClicked(event, active);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256 || event.key() == 344) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static {
        for (String s : new String[]{"FPS", "Memory", "DynamicFPS"}) {
            CAT_MAP.put(s, "Performance");
        }
        for (String s : new String[]{"Scoreboard", "TabList", "PingView", "BetterF3"}) {
            CAT_MAP.put(s, "Visual");
        }
        for (String s : new String[]{"Coordinates", "Biome", "Direction", "Speed", "Day", "Time", "PingHUD"}) {
            CAT_MAP.put(s, "Info");
        }
        for (String s : new String[]{"Health", "Hunger", "XP", "ArmorHUD", "Potions"}) {
            CAT_MAP.put(s, "Combat");
        }
        for (String s : new String[]{"Keystrokes", "CPS"}) {
            CAT_MAP.put(s, "Input");
        }
        for (String s : new String[]{"FullBright", "ToggleSprint", "ToggleSneak", "Zoom", "AutoReconnect"}) {
            CAT_MAP.put(s, "Utility");
        }
        CAT_COLOR = new HashMap<String, Integer>();
        CAT_COLOR.put("Performance", -30652);
        CAT_COLOR.put("Visual", -12272641);
        CAT_COLOR.put("Info", -7798904);
        CAT_COLOR.put("Combat", -48043);
        CAT_COLOR.put("Input", -8892);
        CAT_COLOR.put("Utility", -5601025);
        CREDITS = new String[][]{{"BetterF3", "cominixo", "Recreated: cleaner debug screen"}, {"Dynamic FPS", "juliand665", "Recreated: reduces FPS when unfocused"}, {"PingView", "Grayray75", "Recreated: numeric ping in tab list"}, {"FPS HUD", "The Lads", "Native on-screen FPS counter"}, {"Coordinates HUD", "The Lads", "Native XYZ position overlay"}, {"Biome HUD", "The Lads", "Current-biome overlay"}, {"Armor HUD", "The Lads", "Armor & durability overlay"}, {"Memory HUD", "The Lads", "JVM memory overlay"}, {"Direction HUD", "The Lads", "Facing-direction overlay"}, {"Speed HUD", "The Lads", "Blocks/second overlay"}, {"Day HUD", "The Lads", "World-day counter"}, {"Time HUD", "The Lads", "World-time clock"}, {"Health HUD", "The Lads", "Health overlay"}, {"Hunger HUD", "The Lads", "Food-level overlay"}, {"XP HUD", "The Lads", "Experience overlay"}, {"Zoom", "The Lads", "Hold-to-zoom FOV"}, {"Fullbright", "The Lads", "Max-brightness toggle"}, {"ToggleSprint", "The Lads", "Auto-sprint toggle"}, {"ToggleSneak", "The Lads", "Auto-sneak toggle"}};
    }
}

