package com.thelads.core.client.gui;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ProfileManager;
import com.thelads.core.client.ProfileContext;
import com.thelads.core.modules.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.*;

public class LadsSettingsScreen extends Screen {
    private final Screen parent;
    private String currentTab = "Modules";
    private double scrollOffset = 0;
    private int maxScroll = 0;

    // ── Animation state ────────────────────────────────────────────────────────
    private static long   openTime        = 0;
    private static float  tabIndicatorY   = 55;
    private static final Map<String, Long>    toggleFlash  = new HashMap<>();
    private static final Map<String, Boolean> prevEnabled  = new HashMap<>();
    private static long   saveFlashTime   = -1;

    // ── Sort ──────────────────────────────────────────────────────────────────
    private static int     sortMode      = 0;
    private static boolean sortAscending = true;
    private static final String[] SORT_NAMES = {"A-Z", "Last Modified", "Favorites"};

    // ── Filter ────────────────────────────────────────────────────────────────
    private EditBox searchBox;
    private static String selectedCategory = "All";

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final int BG           = 0xDD050508; // Translucent dark obsidian black/red base
    private static final int SIDEBAR      = 0xEE0A0202; // Deep dark crimson sidebar
    private static final int ACCENT       = 0xFFD32F2F; // Premium bright crimson/red
    private static final int ACCENT2      = 0xFFFF5252; // Hover/active lighter crimson red
    private static final int CARD         = 0xCC180A0A; // Glassy obsidian cards with subtle crimson tint
    private static final int CARD_HOV     = 0xCC2A1010; // Lighter hover state with deep red highlight
    private static final int TEXT_HI      = 0xFFFFFFFF; // Bright white
    private static final int TEXT_MED     = 0xFFCCCCCC; // Light gray
    private static final int TEXT_LO      = 0xFF885555; // Muted deep red-gray
    private static final int TOGGLE_ON    = 0xFF8B0000; // Crimson toggle
    private static final int TOGGLE_OFF   = 0xFF2A0A0A; // Dark red-black toggle
    private static final int DIVIDER      = 0x22FF5555; // Dark red translucent divider

    private static final int[] PALETTE = {
        0xFFFFFFFF, 0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55,
        0xFF55FF55, 0xFF55FFFF, 0xFF5555FF, 0xFFAA00FF, 0xFFFF55FF
    };

    // ── Grid ──────────────────────────────────────────────────────────────────
    private static final int COLS      = 3;
    private static final int GAP       = 8;
    private static final int CARD_H    = 70;

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private static final String[] TABS  = {"Modules", "HUD", "Profiles", "Credits"};
    private static final String[] ICONS = {"⚙", "▭", "❏", "♥"};

    // ── Categories ────────────────────────────────────────────────────────────
    private static final Map<String, String> CAT_MAP = new HashMap<>();
    static {
        for (String s : new String[]{"FPS","Memory","DynamicFPS","RenderScale","PerformanceManager","ScalableLux"}) CAT_MAP.put(s,"Performance");
        for (String s : new String[]{"Scoreboard","TabList","PingView","BetterF3","Capes","SmoothHotbar","DiscordRPC","HideChatIndicators","TexturePacks","OldDamageTilt","VerticalBobbing","XaeroWorldmap"}) CAT_MAP.put(s,"Visual");
        for (String s : new String[]{"Coordinates","Biome","Direction","Speed","Day","Time","PingHUD","XaeroMinimap"}) CAT_MAP.put(s,"Info");
        for (String s : new String[]{"Health","Hunger","XP","ArmorHUD","Potions","KillBanner"}) CAT_MAP.put(s,"Combat");
        for (String s : new String[]{"Keystrokes","CPS"}) CAT_MAP.put(s,"Input");
        for (String s : new String[]{"Fullbright","ToggleSprint","ToggleSneak","Zoom","AutoReconnect","Nametags","VoiceChat"}) CAT_MAP.put(s,"Utility");
    }
    private static final Map<String, Integer> CAT_COLOR = new HashMap<>();
    static {
        CAT_COLOR.put("Performance", 0xFFFF8844);
        CAT_COLOR.put("Visual",      0xFF44BBFF);
        CAT_COLOR.put("Info",        0xFF88FF88);
        CAT_COLOR.put("Combat",      0xFFFF4455);
        CAT_COLOR.put("Input",       0xFFFFDD44);
        CAT_COLOR.put("Utility",     0xFFAA88FF);
    }

    // ── Credits ───────────────────────────────────────────────────────────────
    private static final String[][] CREDITS = {
        {"BetterF3","cominixo","Recreated: cleaner debug screen"},
        {"Dynamic FPS","juliand665","Recreated: reduces FPS when unfocused"},
        {"PingView","Grayray75","Recreated: numeric ping in tab list"},
        {"FPS HUD","The Lads","Native on-screen FPS counter"},
        {"Coordinates HUD","The Lads","Native XYZ position overlay"},
        {"Biome HUD","The Lads","Current-biome overlay"},
        {"Armor HUD","The Lads","Armor & durability overlay"},
        {"Memory HUD","The Lads","JVM memory overlay"},
        {"Direction HUD","The Lads","Facing-direction overlay"},
        {"Speed HUD","The Lads","Blocks/second overlay"},
        {"Day HUD","The Lads","World-day counter"},
        {"Time HUD","The Lads","World-time clock"},
        {"Health HUD","The Lads","Health overlay"},
        {"Hunger HUD","The Lads","Food-level overlay"},
        {"XP HUD","The Lads","Experience overlay"},
        {"Zoom","The Lads","Hold-to-zoom FOV"},
        {"Fullbright","The Lads","Max-brightness toggle"},
        {"ToggleSprint","The Lads","Auto-sprint toggle"},
        {"ToggleSneak","The Lads","Auto-sneak toggle"},
    };

    public LadsSettingsScreen(Screen parent) {
        super(Component.literal("The Lads Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openTime = System.currentTimeMillis();
        tabIndicatorY = tabTargetY();
        scrollOffset = 0;

        // Search box (top-right of content area, visible only on Modules tab)
        int sbW = 130, sbH = 16;
        int sbX = this.width - sbW - 8;
        int sbY = 16;
        searchBox = new EditBox(this.font, sbX, sbY, sbW, sbH, Component.literal("Search modules…"));
        searchBox.setMaxLength(40);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFCCCCCC);
        searchBox.setHint(Component.literal("§8Search…"));
        this.addRenderableWidget(searchBox);
    }

    // ── Main render ───────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        long now = System.currentTimeMillis();
        float age = (now - openTime) / 1000.0f; // seconds since open

        // Full background
        g.fill(0, 0, this.width, this.height, BG);

        // Animated star-field background (very subtle)
        drawStarfield(g, now);

        // Sidebar
        int sw = 120;
        g.fill(0, 0, sw, this.height, SIDEBAR);
        g.fill(sw, 0, sw + 1, this.height, DIVIDER);

        // Logo
        long logoPulse = (long)(Math.abs(Math.sin(now / 2000.0)) * 30);
        int logoColor = (0xFF << 24) | blendInt(ACCENT, ACCENT2, (float) logoPulse / 30f);
        g.text(this.font, "§lTHE LADS", 14, 14, logoColor, true);
        g.text(this.font, "§8CLIENT", 14, 24, TEXT_LO, false);
        g.fill(10, 40, sw - 10, 41, DIVIDER);

        // Smooth tab indicator
        float targetY = tabTargetY();
        tabIndicatorY += (targetY - tabIndicatorY) * 0.18f;
        int tabIndY = (int) tabIndicatorY;
        // Glowing indicator bar
        for (int i = 0; i < 4; i++) {
            int a = Math.max(0, 200 - i * 50);
            g.fill(0, tabIndY + i, 3 - i, tabIndY + 22 - i, (a << 24) | (ACCENT & 0x00FFFFFF));
        }

        // Tab list
        int tabY = 55;
        for (int ti = 0; ti < TABS.length; ti++) {
            String tab = TABS[ti];
            boolean sel = tab.equals(currentTab);
            boolean hov = mouseX >= 0 && mouseX < sw && mouseY >= tabY && mouseY < tabY + 22;
            if (sel) g.fill(3, tabY, sw, tabY + 22, 0x22FFFFFF);
            else if (hov) g.fill(3, tabY, sw, tabY + 22, 0x11FFFFFF);
            int col = sel ? TEXT_HI : (hov ? TEXT_MED : TEXT_LO);
            g.text(this.font, ICONS[ti] + " " + tab, 14, tabY + 7, col, false);
            tabY += 26;
        }

        // Save button (sidebar, above cosmetics) — always visible
        int saveY = this.height - 68;
        boolean saveHov = mouseX >= 0 && mouseX < sw && mouseY >= saveY && mouseY < saveY + 22;
        boolean saveFlashing = saveFlashTime > 0 && (System.currentTimeMillis() - saveFlashTime) < 900;
        int saveBg = saveFlashing ? 0x44003300 : (saveHov ? 0x2200CC44 : 0x11FFFFFF);
        g.fill(3, saveY, sw, saveY + 22, saveBg);
        g.fill(3, saveY, 5, saveY + 22, saveFlashing ? 0xFF55FF55 : (saveHov ? 0xFF44DD88 : 0xFF555570));
        String saveLabel = saveFlashing ? "✓ Saved!" : "💾 Save";
        int saveLabelCol = saveFlashing ? 0xFF55FF55 : (saveHov ? TEXT_HI : TEXT_LO);
        g.text(this.font, saveLabel, 14, saveY + 7, saveLabelCol, saveFlashing);

        // Cosmetics button (sidebar bottom)
        int cosY = this.height - 40;
        boolean cosHov = mouseX >= 0 && mouseX < sw && mouseY >= cosY && mouseY < cosY + 22;
        if (cosHov) g.fill(3, cosY, sw, cosY + 22, 0x11FFFFFF);
        g.text(this.font, "👕 Cosmetics", 14, cosY + 7, cosHov ? TEXT_HI : TEXT_LO, false);
        g.text(this.font, "§8v1.0.0", 14, this.height - 16, TEXT_LO, false);

        // Content area
        int cx = sw + 14, cw = this.width - sw - 28, cy = 14;
        g.text(this.font, "§l" + currentTab, cx, cy, TEXT_HI, true);
        cy += 16;
        g.fill(cx, cy, cx + cw, cy + 1, DIVIDER);
        cy += 6;

        // Search box background (only Modules)
        if ("Modules".equals(currentTab)) {
            searchBox.setVisible(true);
            int sbX = this.width - 138, sbY2 = 14;
            g.fill(sbX - 4, sbY2 - 2, sbX + 134, sbY2 + 18, 0xBB120505);
            g.fill(sbX - 4, sbY2 + 16, sbX + 134, sbY2 + 18, ACCENT);
        } else {
            searchBox.setVisible(false);
        }

        if ("Modules".equals(currentTab))  renderModules(g, mouseX, mouseY, cx, cy, cw, now, age);
        else if ("HUD".equals(currentTab)) renderHud(g, mouseX, mouseY, cx, cy, cw);
        else if ("Profiles".equals(currentTab)) renderProfiles(g, mouseX, mouseY, cx, cy, cw);
        else if ("Credits".equals(currentTab))  renderCredits(g, mouseX, mouseY, cx, cy, cw, now);

        // Close button (top-right)
        boolean closeHov = mouseX >= this.width - 22 && mouseX < this.width - 4 && mouseY >= 4 && mouseY < 22;
        g.fill(this.width - 22, 4, this.width - 4, 22, closeHov ? 0xCC7D2E2E : 0x44FFFFFF);
        g.centeredText(this.font, "✕", this.width - 13, 9, closeHov ? 0xFFFF5555 : TEXT_MED);

        super.extractRenderState(g, mouseX, mouseY, pt);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Background is drawn manually in extractRenderState; skip the blur to prevent
        // "Can only blur once per frame" crash when FancyMenu wraps extractRenderState.
    }

    // ── Starfield background ──────────────────────────────────────────────────

    private void drawStarfield(GuiGraphicsExtractor g, long now) {
        Random rng = new Random(42);
        for (int i = 0; i < 60; i++) {
            int sx = rng.nextInt(this.width);
            int sy = rng.nextInt(this.height);
            float phase = rng.nextFloat() * 6.28f;
            float twinkle = 0.3f + 0.7f * (float)(0.5 + 0.5 * Math.sin(now / 1800.0 + phase));
            int a = (int)(twinkle * 55) & 0xFF;
            g.fill(sx, sy, sx + 1, sy + 1, (a << 24) | 0xFF3333);
        }
    }

    // ── Modules tab ───────────────────────────────────────────────────────────

    private void renderModules(GuiGraphicsExtractor g, int mx, int my, int startX, int startY, int cw, long now, float age) {
        // Sort header
        g.text(this.font, "§7Sort:", startX, startY + 3, TEXT_MED, false);
        int sortX = startX + 32;
        boolean sortHov = mx >= sortX && mx < sortX + 100 && my >= startY && my < startY + 16;
        drawPanel(g, sortX, startY, sortX + 100, startY + 16, sortHov ? CARD_HOV : CARD);
        g.text(this.font, "§f" + SORT_NAMES[sortMode], sortX + 6, startY + 3, TEXT_HI, false);
        int dirX = sortX + 104;
        drawPanel(g, dirX, startY, dirX + 16, startY + 16, CARD_HOV);
        g.centeredText(this.font, sortAscending ? "▲" : "▼", dirX + 8, startY + 3, TEXT_HI);

        // Category filter chips
        int chipX = startX;
        int chipY = startY + 22;
        String[] cats = {"All", "Performance", "Visual", "Info", "Combat", "Input", "Utility"};
        for (String cat : cats) {
            int textW = this.font.width(cat);
            int chipW = textW + 12;
            if (chipX + chipW > startX + cw) {
                chipX = startX;
                chipY += 16;
            }
            boolean selected = cat.equals(selectedCategory);
            boolean hov = mx >= chipX && mx < chipX + chipW && my >= chipY && my < chipY + 12;
            int bg = selected ? ACCENT : (hov ? CARD_HOV : CARD);
            int border = selected ? ACCENT2 : (hov ? ACCENT : DIVIDER);
            drawPanel(g, chipX, chipY, chipX + chipW, chipY + 12, bg);
            drawBorder(g, chipX, chipY, chipX + chipW, chipY + 12, border, 1);
            int txtCol = selected ? TEXT_HI : (hov ? TEXT_HI : TEXT_LO);
            g.centeredText(this.font, cat, chipX + chipW / 2, chipY + 2, txtCol);
            chipX += chipW + 4;
        }

        // Grid
        String filter = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT) : "";
        List<Module> modules = sortedModules(filter);

        int top = chipY + 18;
        int cardW = (cw - (COLS - 1) * GAP) / COLS;
        int rows = (modules.size() + COLS - 1) / COLS;
        maxScroll = Math.max(0, rows * (CARD_H + GAP) - (this.height - top - 8));

        int baseY = top - (int) scrollOffset;
        for (int i = 0; i < modules.size(); i++) {
            int col = i % COLS, row = i / COLS;
            int cx = startX + col * (cardW + GAP);
            int cy = baseY + row * (CARD_H + GAP);
            if (cy + CARD_H < top - 4 || cy > this.height - 8) continue;

            // Stagger entrance animation
            float delay = i * 0.035f;
            float t = Mth.clamp((age - delay) / 0.28f, 0f, 1f);
            float ease = 1f - (1f - t) * (1f - t); // ease-out quad
            int ySlide = (int)((1f - ease) * 28);
            int aScale = (int)(ease * 255);
            if (aScale <= 0) continue;

            drawModuleCard(g, modules.get(i), cx, cy + ySlide, cardW, CARD_H, mx, my, now, aScale);
        }

        // Empty-state message
        if (modules.isEmpty() && !filter.isEmpty()) {
            g.centeredText(this.font, "No modules match: " + filter, startX + cw / 2, top + 30, TEXT_LO);
        }
    }

    private void drawModuleCard(GuiGraphicsExtractor g, Module mod, int cx, int cy, int cw, int ch,
                                 int mx, int my, long now, int alpha) {
        boolean hover = mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;
        boolean enabled = mod.isEnabled();

        // Detect toggle flash
        Boolean prev = prevEnabled.get(mod.getName());
        if (prev != null && prev != enabled) {
            toggleFlash.put(mod.getName(), now);
        }
        prevEnabled.put(mod.getName(), enabled);

        long flashElapsed = now - toggleFlash.getOrDefault(mod.getName(), 0L);
        boolean flashing = flashElapsed < 350;
        float flashT = flashing ? (1f - flashElapsed / 350f) : 0f;
        int flashA = (int)(flashT * 80);

        // Card background with alpha
        int bgColor = blendWithAlpha(hover ? CARD_HOV : CARD, alpha);
        drawPanel(g, cx, cy, cx + cw, cy + ch, bgColor);

        // Toggle flash overlay
        if (flashing) {
            int flashColor = enabled ? ((flashA << 24) | 0x00FF44) : ((flashA << 24) | 0xFF4444);
            g.fill(cx, cy, cx + cw, cy + ch, flashColor);
        }

        // Hover glow border
        if (hover) {
            float glow = 0.5f + 0.5f * (float)Math.sin(now / 280.0);
            int glowA = (int)(glow * 120);
            drawBorder(g, cx, cy, cx + cw, cy + ch, (glowA << 24) | (ACCENT2 & 0x00FFFFFF), 1);
        }

        // Category chip (top-left corner)
        String cat = CAT_MAP.getOrDefault(mod.getName(), "");
        if (!cat.isEmpty()) {
            int catCol = CAT_COLOR.getOrDefault(cat, ACCENT);
            int catA = (int)(alpha * 0.7f) & 0xFF;
            g.fill(cx, cy, cx + 3, cy + ch, ((catA) << 24) | (catCol & 0x00FFFFFF));
        }

        // Module name
        String name = mod.getName();
        int nameX = cx + (cat.isEmpty() ? 8 : 10);
        g.text(this.font, "§f" + name, nameX, cy + 8, blendWithAlpha(TEXT_HI, alpha), true);

        // Category label (small, under name)
        if (!cat.isEmpty()) {
            int catCol2 = CAT_COLOR.getOrDefault(cat, TEXT_LO);
            g.text(this.font, "§8" + cat, nameX, cy + 20, blendWithAlpha(catCol2, (int)(alpha * 0.6f)), false);
        }

        // Favorite star
        String star = mod.isFavorite() ? "§e★" : "§8☆";
        g.text(this.font, star, cx + cw - 14, cy + 6, TEXT_HI, false);

        // Color swatch (HUD modules)
        if (mod instanceof HudModule hm) {
            boolean global = hm.isUseGlobalColor();
            int swCol = global ? 0xFF555577 : (hm.getCustomColor() | 0xFF000000);
            drawSwatch(g, cx + cw - 14, cy + 20, swCol, global);
        }

        // Enable/disable bar (bottom)
        int barY = cy + ch - 16;
        int barColor = blendWithAlpha(enabled ? TOGGLE_ON : TOGGLE_OFF, alpha);
        drawPanel(g, cx + 3, barY, cx + cw - 3, barY + 13, barColor);

        // Animated toggle dot
        float dotProgress = enabled ? 1f : 0f;
        Long ft = toggleFlash.get(mod.getName());
        if (ft != null) {
            float prog = Mth.clamp(flashElapsed / 200f, 0f, 1f);
            // Use current state for smooth transition
        }
        int dotRange = cw - 24;
        int dotX = cx + 6 + (int)(dotProgress * dotRange * 0.3f);
        g.fill(dotX, barY + 3, dotX + 6, barY + 10,
               blendWithAlpha(enabled ? 0xFF88FF88 : 0xFFFF8888, alpha));

        // Label
        String label = enabled ? "ENABLED" : "DISABLED";
        int lblX = cx + cw / 2 - this.font.width(label) / 2;
        g.text(this.font, "§f" + label, lblX, barY + 2, blendWithAlpha(TEXT_HI, alpha), false);
    }

    // ── HUD tab ───────────────────────────────────────────────────────────────

    private void renderHud(GuiGraphicsExtractor g, int mx, int my, int sx, int sy, int cw) {
        int rh = 34, sp = 6, y = sy;

        // Global text color row
        y = renderHudRow(g, mx, my, sx, y, cw, rh, "Global Text Color", "Used by modules set to Global");
        drawSwatch(g, sx + cw - 28, y - rh + 8 - sp, HudSettings.getInstance().getGlobalColor() | 0xFF000000, false);
        g.text(this.font, "§7↺", sx + cw - 48, y - rh + 12 - sp, TEXT_LO, false);
        y += sp;

        // Global background
        y = renderHudRow(g, mx, my, sx, y, cw, rh, "Global Background", "Box behind modules set to Global");
        drawSwatch(g, sx + cw - 28, y - rh + 8 - sp, HudSettings.getInstance().getGlobalBackground() | 0xFF000000, false);
        g.text(this.font, "§7↺", sx + cw - 48, y - rh + 12 - sp, TEXT_LO, false);
        y += sp;

        // Text shadow toggle
        boolean shadow = HudSettings.getInstance().isTextShadow();
        y = renderHudRow(g, mx, my, sx, y, cw, rh, "Text Shadow", "Drop shadow on HUD text");
        drawToggleSwitch(g, sx + cw - 52, y - rh + 8 - sp, shadow);
        y += sp;

        // Edit layout
        boolean hovEdit = mx >= sx && mx < sx + cw && my >= y && my < y + rh;
        drawPanel(g, sx, y, sx + cw, y + rh, hovEdit ? CARD_HOV : CARD);
        g.fill(sx, y + 4, sx + 3, y + rh - 4, ACCENT);
        g.text(this.font, "§f✎ Edit HUD Layout", sx + 10, y + 8, TEXT_HI, true);
        g.text(this.font, "Drag and reposition overlays", sx + 10, y + 20, TEXT_MED, false);
        y += rh + sp;

        // Fade playlist
        drawPanel(g, sx, y, sx + cw, y + rh, CARD);
        g.fill(sx, y + 4, sx + 3, y + rh - 4, ACCENT);
        g.text(this.font, "§fFade Playlist", sx + 10, y + 8, TEXT_HI, true);
        g.text(this.font, "Colors for the Fade mode (click to remove)", sx + 10, y + 20, TEXT_MED, false);
        List<Integer> pl = HudSettings.getInstance().getFadePlaylist();
        int swx = sx + 10, swy = y + rh + 2;
        for (int i = 0; i < pl.size() && i < 12; i++) { drawSwatch(g, swx, swy, pl.get(i) | 0xFF000000, false); swx += 20; }
        g.fill(swx - 1, swy - 1, swx + 17, swy + 17, 0xFF000000);
        g.fill(swx, swy, swx + 16, swy + 16, CARD_HOV);
        g.text(this.font, "§f+", swx + 5, swy + 4, TEXT_HI, false);
    }

    private int renderHudRow(GuiGraphicsExtractor g, int mx, int my, int sx, int y, int cw, int rh, String label, String desc) {
        boolean hov = mx >= sx && mx < sx + cw && my >= y && my < y + rh;
        drawPanel(g, sx, y, sx + cw, y + rh, hov ? CARD_HOV : CARD);
        g.fill(sx, y + 4, sx + 3, y + rh - 4, ACCENT);
        g.text(this.font, "§f" + label, sx + 10, y + 8, TEXT_HI, true);
        g.text(this.font, desc, sx + 10, y + 20, TEXT_MED, false);
        return y + rh;
    }

    private void drawToggleSwitch(GuiGraphicsExtractor g, int x, int y, boolean on) {
        g.fill(x, y, x + 42, y + 14, on ? 0xFF2A5C2A : 0xFF3A2A2A);
        int dotX = on ? x + 27 : x + 3;
        g.fill(dotX, y + 2, dotX + 12, y + 12, on ? 0xFF55FF55 : 0xFFFF5555);
    }

    // ── Credits tab ───────────────────────────────────────────────────────────

    private void renderCredits(GuiGraphicsExtractor g, int mx, int my, int sx, int sy, int cw, long now) {
        g.text(this.font, "§7Reimplementations of open-source mods.", sx, sy, TEXT_MED, false);
        int y = sy + 18, cardH = 44, sp = 6;
        for (int i = 0; i < CREDITS.length; i++) {
            String[] cr = CREDITS[i];
            boolean hov = mx >= sx && mx < sx + cw && my >= y && my < y + cardH;
            drawPanel(g, sx, y, sx + cw, y + cardH, hov ? CARD_HOV : CARD);

            // Animated left accent on hover
            if (hov) {
                float glow = 0.5f + 0.5f * (float)Math.sin(now / 300.0);
                int a = (int)(glow * 255);
                g.fill(sx, y + 3, sx + 3, y + cardH - 3, (a << 24) | (ACCENT2 & 0x00FFFFFF));
            } else {
                g.fill(sx, y + 3, sx + 3, y + cardH - 3, ACCENT);
            }

            g.text(this.font, "§b" + cr[0], sx + 10, y + 6, TEXT_HI, true);
            g.text(this.font, "§7by §f" + cr[1], sx + 12 + this.font.width(cr[0]), y + 6, TEXT_MED, false);
            g.text(this.font, cr[2], sx + 10, y + 20, TEXT_LO, false);
            y += cardH + sp;
        }
    }

    // ── Profiles tab ─────────────────────────────────────────────────────────

    private void renderProfiles(GuiGraphicsExtractor g, int mx, int my, int sx, int sy, int cw) {
        String ctx = ProfileContext.current(Minecraft.getInstance());
        g.text(this.font, "§7In: §f" + ProfileContext.label(Minecraft.getInstance()), sx, sy + 2, TEXT_MED, false);
        int by = sy + 16;
        boolean nh = mx >= sx && mx < sx + cw && my >= by && my < by + 22;
        drawPanel(g, sx, by, sx + cw, by + 22, nh ? CARD_HOV : CARD);
        g.fill(sx, by + 3, sx + 3, by + 19, 0xFF55FF55);
        g.text(this.font, "§a+ Save current HUD as new profile", sx + 10, by + 7, TEXT_HI, false);
        List<String> names = ProfileManager.get().getProfileNames();
        int rowH = 36, listTop = by + 30;
        maxScroll = Math.max(0, names.size() * rowH - (this.height - listTop - 10));
        int y = listTop - (int)scrollOffset;
        int applyX = sx + cw - 180, bindX = sx + cw - 118, delX = sx + cw - 28;
        for (String name : names) {
            if (y + rowH >= listTop && y < this.height - 10) {
                drawPanel(g, sx, y, sx + cw, y + rowH - 4, CARD);
                g.text(this.font, "§f" + name, sx + 10, y + 4, TEXT_HI, true);
                boolean bound = name.equals(ProfileManager.get().getBinding(ctx));
                g.text(this.font, bound ? "§a● bound" : "§8not bound", sx + 10, y + 18, TEXT_LO, false);
                drawMiniBtn(g, applyX, y + 6, 56, "Apply", mx, my, ACCENT);
                drawMiniBtn(g, bindX, y + 6, 56, bound ? "Unbind" : "Bind", mx, my, 0xFF444466);
                drawMiniBtn(g, delX, y + 6, 22, "✕", mx, my, 0xFF7D2E2E);
            }
            y += rowH;
        }
        if (names.isEmpty())
            g.text(this.font, "§8No profiles yet.", sx, listTop + 4, TEXT_LO, false);
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void drawPanel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    private void drawBorder(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color, int t) {
        g.fill(x1, y1,     x2,     y1 + t, color);
        g.fill(x1, y2 - t, x2,     y2,     color);
        g.fill(x1, y1,     x1 + t, y2,     color);
        g.fill(x2 - t, y1, x2,     y2,     color);
    }

    private void drawSwatch(GuiGraphicsExtractor g, int x, int y, int color, boolean global) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF000000);
        g.fill(x, y, x + 16, y + 16, color);
        if (global) g.text(this.font, "§0G", x + 5, y + 4, TEXT_HI, false);
    }

    private void drawMiniBtn(GuiGraphicsExtractor g, int x, int y, int w, String label, int mx, int my, int color) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 18;
        g.fill(x, y, x + w, y + 18, hov ? (color | 0x33FFFFFF) : color);
        g.text(this.font, "§f" + label, x + w / 2 - this.font.width(label) / 2, y + 5, TEXT_HI, false);
    }

    private static int blendInt(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = ar + (int)((br - ar) * t), gv = ag + (int)((bg - ag) * t), bv = ab + (int)((bb - ab) * t);
        return (r << 16) | (gv << 8) | bv;
    }

    private static int blendWithAlpha(int color, int alpha) {
        int origA = (color >> 24) & 0xFF;
        int newA  = (origA * alpha) / 255;
        return (newA << 24) | (color & 0x00FFFFFF);
    }

    private float tabTargetY() {
        for (int i = 0; i < TABS.length; i++) {
            if (TABS[i].equals(currentTab)) return 55 + i * 26f;
        }
        return 55;
    }

    // ── Sorted module list ────────────────────────────────────────────────────

    private List<Module> sortedModules(String filter) {
        List<Module> list = new ArrayList<>();
        for (Module m : ModuleManager.getInstance().getModules()) {
            boolean matchesSearch = filter.isEmpty() || m.getName().toLowerCase(Locale.ROOT).contains(filter)
                    || CAT_MAP.getOrDefault(m.getName(), "").toLowerCase(Locale.ROOT).contains(filter);
            boolean matchesCategory = "All".equals(selectedCategory)
                    || selectedCategory.equals(CAT_MAP.get(m.getName()));
            if (matchesSearch && matchesCategory) {
                list.add(m);
            }
        }
        Comparator<Module> cmp = switch (sortMode) {
            case 1  -> Comparator.comparingLong(Module::getLastModified).reversed();
            case 2  -> Comparator.comparingInt((Module m) -> m.isFavorite() ? 0 : 1)
                                 .thenComparing(Module::getName, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER);
        };
        list.sort(cmp);
        if (!sortAscending) Collections.reverse(list);
        return list;
    }

    private int gridCardW(int cw) { return (cw - (COLS - 1) * GAP) / COLS; }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if ("Modules".equals(currentTab) || "Profiles".equals(currentTab)) {
            scrollOffset = Mth.clamp(scrollOffset - va * 20, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, ha, va);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean active) {
        double mx = event.x(), my = event.y();
        if (event.button() != 0) return super.mouseClicked(event, active);
        int sw = 120;

        // Close
        if (mx >= this.width - 22 && mx < this.width - 4 && my >= 4 && my < 22) { onClose(); return true; }

        // Save button
        int saveYClick = this.height - 68;
        if (mx >= 0 && mx < sw && my >= saveYClick && my < saveYClick + 22) {
            ConfigManager.save();
            saveFlashTime = System.currentTimeMillis();
            return true;
        }

        // Cosmetics
        int cosY = this.height - 40;
        if (mx >= 0 && mx < sw && my >= cosY && my < cosY + 22) {
            this.minecraft.setScreen(new com.thelads.core.client.gui.cosmetics.CosmeticsScreen(this)); return true;
        }

        // Sidebar tabs
        int tabY = 55;
        for (String tab : TABS) {
            if (mx >= 0 && mx < sw && my >= tabY && my < tabY + 22) {
                currentTab = tab; scrollOffset = 0; return true;
            }
            tabY += 26;
        }

        // HUD tab controls
        if ("HUD".equals(currentTab)) {
            int cx = sw + 14, cw = this.width - sw - 28;
            int rh = 34, sp = 6, yy = 36;
            // Global text color reset + swatch
            if (mx >= cx + cw - 50 && mx < cx + cw - 34 && my >= yy + 8 && my < yy + 24) { HudSettings.getInstance().setGlobalColor(0xFFFFFFFF); ConfigManager.save(); return true; }
            if (mx >= cx + cw - 30 && mx < cx + cw - 12 && my >= yy + 7 && my < yy + 23) { this.minecraft.setScreen(new ColorPickerScreen(this, HudSettings.getInstance().getGlobalColor(), false, false, (u, c) -> { HudSettings.getInstance().setGlobalColor(c); ConfigManager.save(); })); return true; }
            yy += rh + sp;
            // Global background reset + swatch
            if (mx >= cx + cw - 50 && mx < cx + cw - 34 && my >= yy + 8 && my < yy + 24) { HudSettings.getInstance().setGlobalBackground(0x80000000); ConfigManager.save(); return true; }
            if (mx >= cx + cw - 30 && mx < cx + cw - 12 && my >= yy + 7 && my < yy + 23) { this.minecraft.setScreen(new ColorPickerScreen(this, HudSettings.getInstance().getGlobalBackground(), false, false, (u, c) -> { HudSettings.getInstance().setGlobalBackground(c); ConfigManager.save(); })); return true; }
            yy += rh + sp;
            // Text shadow toggle
            if (mx >= cx + cw - 52 && mx < cx + cw - 10 && my >= yy + 8 && my < yy + 22) { HudSettings.getInstance().setTextShadow(!HudSettings.getInstance().isTextShadow()); ConfigManager.save(); return true; }
            yy += rh + sp;
            // Edit HUD layout
            if (mx >= cx && mx < cx + cw && my >= yy && my < yy + rh) { this.minecraft.setScreen(new DraggableHudScreen()); return true; }
            yy += rh + sp;
            // Fade playlist
            List<Integer> pl = HudSettings.getInstance().getFadePlaylist();
            int fswx = cx + 10, fswy = yy + rh + 2;
            for (int i = 0; i < pl.size() && i < 12; i++) {
                if (mx >= fswx && mx < fswx + 16 && my >= fswy && my < fswy + 16) { pl.remove(i); ConfigManager.save(); return true; }
                fswx += 20;
            }
            if (mx >= fswx && mx < fswx + 16 && my >= fswy && my < fswy + 16) { this.minecraft.setScreen(new ColorPickerScreen(this, 0xFFFF0000, false, false, (u, c) -> { HudSettings.getInstance().getFadePlaylist().add(c); ConfigManager.save(); })); return true; }
        }

        // Modules grid
        if ("Modules".equals(currentTab)) {
            int cx = sw + 14, cw2 = this.width - sw - 28, sy = 36;
            String filter = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT) : "";
            List<Module> modules = sortedModules(filter);
            // Sort header
            int sortX = cx + 32;
            if (mx >= sortX && mx < sortX + 100 && my >= sy && my < sy + 16) { sortMode = (sortMode + 1) % SORT_NAMES.length; return true; }
            int dirX = sortX + 104;
            if (mx >= dirX && mx < dirX + 16 && my >= sy && my < sy + 16) { sortAscending = !sortAscending; return true; }

            // Category filter chips click detection
            int chipX = cx;
            int chipY = sy + 22;
            String[] cats = {"All", "Performance", "Visual", "Info", "Combat", "Input", "Utility"};
            for (String cat : cats) {
                int textW = this.font.width(cat);
                int chipW = textW + 12;
                if (chipX + chipW > cx + cw2) {
                    chipX = cx;
                    chipY += 16;
                }
                if (mx >= chipX && mx < chipX + chipW && my >= chipY && my < chipY + 12) {
                    selectedCategory = cat;
                    scrollOffset = 0; // reset scroll
                    return true;
                }
                chipX += chipW + 4;
            }

            int top = chipY + 18, cardW = gridCardW(cw2);
            int baseY = top - (int)scrollOffset;
            for (int i = 0; i < modules.size(); i++) {
                Module mod = modules.get(i);
                int col = i % COLS, row = i / COLS;
                int x = cx + col * (cardW + GAP);
                int y = baseY + row * (CARD_H + GAP);
                if (mx < x || mx >= x + cardW || my < y || my >= y + CARD_H) continue;
                // Favorite star
                if (mx >= x + cardW - 17 && my < y + 18) { mod.setFavorite(!mod.isFavorite()); ConfigManager.save(); return true; }
                // Color swatch (HUD)
                if (mod instanceof HudModule hm && mx >= x + cardW - 14 && mx < x + cardW && my >= y + 18 && my < y + 36) {
                    int init = hm.isUseGlobalColor() ? HudSettings.getInstance().getGlobalColor() : hm.getCustomColor();
                    this.minecraft.setScreen(new ColorPickerScreen(this, init, true, hm.isUseGlobalColor(), (u, c) -> { hm.setUseGlobalColor(u); if (!u) hm.setCustomColor(c); ConfigManager.save(); }));
                    return true;
                }
                // Toggle bar
                if (my >= y + CARD_H - 16) { mod.toggle(); toggleFlash.put(mod.getName(), System.currentTimeMillis()); ConfigManager.save(); return true; }
                // Options
                if (mod.getName().equals("TexturePacks")) {
                    this.minecraft.setScreen(new net.minecraft.client.gui.screens.packs.PackSelectionScreen(
                        this.minecraft.getResourcePackRepository(),
                        (repository) -> {
                            this.minecraft.options.updateResourcePacks(repository);
                            this.minecraft.setScreen(this);
                        },
                        this.minecraft.getResourcePackDirectory(),
                        net.minecraft.network.chat.Component.translatable("resourcePack.title")
                    ));
                    return true;
                }
                this.minecraft.setScreen(new ModuleOptionsScreen(this, mod));
                return true;
            }
        }

        // Profiles
        if ("Profiles".equals(currentTab)) {
            int cx = sw + 14, cw2 = this.width - sw - 28, sy = 36;
            String ctx = ProfileContext.current(Minecraft.getInstance());
            int by = sy + 16;
            if (mx >= cx && mx < cx + cw2 && my >= by && my < by + 22) { ProfileManager.get().saveAs(ProfileManager.get().nextDefaultName()); return true; }
            List<String> names = ProfileManager.get().getProfileNames();
            int rowH = 36, y = (by + 30) - (int)scrollOffset;
            int applyX = cx + cw2 - 180, bindX = cx + cw2 - 118, delX = cx + cw2 - 28;
            for (String name : names) {
                if (my >= y + 6 && my < y + 24) {
                    if (mx >= applyX && mx < applyX + 56) { ProfileManager.get().apply(name); return true; }
                    if (mx >= bindX  && mx < bindX + 56)  { boolean b = name.equals(ProfileManager.get().getBinding(ctx)); ProfileManager.get().bind(ctx, b ? null : name); return true; }
                    if (mx >= delX   && mx < delX + 22)   { ProfileManager.get().delete(name); return true; }
                }
                y += rowH;
            }
        }

        return super.mouseClicked(event, active);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == 256 || event.key() == 344) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() { ConfigManager.save(); this.minecraft.setScreen(this.parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
