package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.*;
import com.thelads.core.config.Module;
import com.thelads.core.mods.ModDependencyPlanner;
import com.thelads.core.mods.ModInventoryModel;
import com.thelads.core.mods.ModStateStore;
import com.thelads.core.modules.KillBannerModule;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import static com.thelads.core.client.gui.MenuGraphics.*;

/** Shared settings: one set of bounds drives drawing, mouse input and keyboard focus. */
public final class LadsSettingsScreen {
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    private record Control(String id, String label, Rect rect, Runnable action, boolean enabled) {}
    private String currentCategory = "All", searchQuery = "", focusId = "", notice = "";
    private boolean enabledOnly, favoritesOnly, filterDirty = true, dirty;
    private boolean fpsDialogOpen, editingFps, draggingFpsSlider;
    private Rect fpsSliderTrack;
    private String fpsBuffer = "";
    private long ownershipRevision = -1;
    private int scrollOffset, maxScroll, width = 640, height = 360;
    private Rect viewport = new Rect(0, 0, 640, 360);
    private final List<Control> controls = new ArrayList<>();
    private final Map<String, Float> hoverStates = new HashMap<>();
    private long previousFrameNanos;
    private float frameBlend = 1;
    private boolean reducedMotion;
    private final ColorPicker colorPicker = new ColorPicker();
    private final ActionDropdown actions = new ActionDropdown();
    private double displayedScroll;
    private int renderScroll;
    private List<Module> filtered = List.of();
    private Module detail;
    private Option editingOption, dragging;
    private Rect dragTrack;
    private boolean editingSearch, selectAll;
    private String editBuffer = "";
    private int cursor;
    private Runnable onOpenHudEditor = () -> {}, onClose = () -> {}, onOpenResourcePacks = () -> {}, onOpenVideoSettings = () -> {};
    private Consumer<String> onNarrate = ignored -> {};
    private Supplier<String> clipboardReader = () -> "";
    private static final String[] CATEGORIES = {"All", "HUD", "Gameplay", "Performance", "Server"};
    private static final Set<String> PERFORMANCE = Set.of("Performance", "DynamicFPS", "Exordium", "RenderScale", "ScalableLux", "Clumps", "FarBlockEntities", "EntityCulling", "Lithium", "FerriteCore");
    // Installed mods view: its own state, search and "mods:" row ids, so the native catalog above stays native-only.
    private record ModLine(ModInventoryModel.Row row, int depth) {}
    private boolean modsView, detailFromMods;
    private String modsSearch = "", modsStamp = "", modsCounts = "";
    private ModInventoryModel.Filter modsFilter = ModInventoryModel.Filter.ALL;
    private ModInventoryModel modsModel;
    private ModDependencyPlanner.Plan modsPlan;
    private final Set<String> modsToggled = new HashSet<>();
    private long modsCheckedNanos;
    private Consumer<String> onOpenModSettings;
    // Module description tooltip: shown once the pointer rests on one catalog card for 750 ms.
    private final HoverDelay tip = new HoverDelay(750_000_000L);
    private Module tipModule;

    /** Menu category of a Lads module, shared with the launcher catalog. */
    public static String categoryOf(Module m) {
        return m.getCategory() == Module.Category.HUD ? "HUD" : m.getCategory() == Module.Category.SERVER ? "Server"
            : PERFORMANCE.contains(m.getName()) ? "Performance" : "Gameplay";
    }

    public void setOnOpenHudEditor(Runnable action) { onOpenHudEditor = action; }
    /** Opens an upstream mod's own settings; without it third-party rows show no settings link. */
    public void setOnOpenModSettings(Consumer<String> action) { onOpenModSettings = action; }
    public boolean isModsViewOpen() { return modsView; }
    public void setReducedMotion(boolean value) { reducedMotion = value; }
    public void setOnClose(Runnable action) { onClose = action; }
    public void setOnOpenResourcePacks(Runnable action) { onOpenResourcePacks = action; }
    public void setOnOpenVideoSettings(Runnable action) { onOpenVideoSettings = action; }
    public void setOnNarrate(Consumer<String> action) { onNarrate = action; }
    public void refreshCapabilities() {
        if (ownershipRevision != ModuleSupport.revision()) {
            ownershipRevision = ModuleSupport.revision(); filterDirty = true; controls.clear();
            if (modsView) reloadMods();
        }
        if (detail != null && !ModuleSupport.isBuiltIn(detail.getName())) {
            detail = null; editingOption = null; dragging = null; editingSearch = false;
            editBuffer = ""; cursor = 0; notice = ""; invalidate();
        }
    }
    public void refreshCatalog() { filterDirty = true; refreshCapabilities(); }
    public void setClipboardReader(Supplier<String> reader) { clipboardReader = reader; }
    public void setSearchQuery(String value) { searchQuery = value == null ? "" : value; filterDirty = true; scrollOffset = 0; displayedScroll = 0; renderScroll = 0; }
    public String getSearchQuery() { return searchQuery; }
    public boolean isEditingText() { return editingSearch || editingOption != null || editingFps || actions.isOpen() || colorPicker.isOpen() || fpsDialogOpen; }
    public String getCurrentCategory() { return currentCategory; }
    public String getCurrentTab() { return "MODS"; }
    public List<Rect> getControlBounds() { return controls.stream().map(Control::rect).toList(); }
    /** Bounds of a control drawn by the last render (e.g. "search", "option:Size"), or null; QA drives real input at them. */
    public Rect controlBounds(String id) { return controls.stream().filter(c -> c.id.equals(id)).map(Control::rect).findFirst().orElse(null); }
    public int getScrollOffset() { return scrollOffset; }
    public List<String> visibleModuleNames() { return getFilteredModules().stream().map(Module::getName).toList(); }

    public void render(LadsGraphics g, int mouseX, int mouseY) {
        refreshCapabilities();
        long now = System.nanoTime();
        double elapsed = previousFrameNanos == 0 ? 0 : Math.min(.1, (now - previousFrameNanos) / 1e9);
        previousFrameNanos = now;
        frameBlend = reducedMotion ? 1 : (float)(1 - Math.exp(-elapsed * 18));
        displayedScroll += (scrollOffset - displayedScroll) * frameBlend;
        if (Math.abs(scrollOffset - displayedScroll) < .2) displayedScroll = scrollOffset;
        renderScroll = (int)Math.round(displayedScroll);
        if (hoverStates.size() > 1024) hoverStates.clear();
        width = g.getScaledWidth(); height = g.getScaledHeight(); controls.clear(); tipModule = null;
        g.fill(0, 0, width, height, BG);
        int pad = width < 450 ? 10 : 20, side = width >= 530 && height >= 340 ? 118 : 0;
        g.fill(0, 0, width, 2, ACCENT);
        g.drawText("THE LADS", pad, 15, ACCENT);
        if (width >= 400) g.drawText(modsView ? "INSTALLED MODS" : detail == null ? "MODS / MAKE IT YOURS" : "MODULE SETTINGS", pad + 68, 15, MUTED);
        String fpsBtnLabel = "HUD FPS: " + (HudSettings.getInstance().isHudFpsCapEnabled() ? HudSettings.formatFpsLimit(HudSettings.getInstance().getHudFpsLimit()) : "Off");
        int fpsBtnW = Math.max(78, g.textWidth(fpsBtnLabel) + 14);
        button(g, "global-hud-fps", fpsBtnLabel, new Rect(width - pad - 116 - fpsBtnW, 8, fpsBtnW, 23), this::openHudFpsDialog, true, mouseX, mouseY, fpsDialogOpen);
        button(g, "global-colors", "Colors", new Rect(width - pad - 110, 8, 52, 23), colorPicker::openGlobal, true, mouseX, mouseY, false);
        button(g, "close", "Done", new Rect(width - pad - 52, 8, 52, 23), this::close, true, mouseX, mouseY, false);
        int x = side == 0 ? pad : side + 16, w = width - x - pad;
        if (side > 0) {
            g.fill(0, 40, side, height, PANEL);
            for (int i = 0; i < CATEGORIES.length; i++) {
                String category = CATEGORIES[i];
                button(g, "category:" + category, category, new Rect(8, 53 + i * 29, side - 16, 24), () -> category(category), true, mouseX, mouseY, !modsView && category.equals(currentCategory));
            }
            button(g, "installed-mods", "Installed mods", new Rect(8, 59 + CATEGORIES.length * 29, side - 16, 24), this::openMods, true, mouseX, mouseY, modsView);
            button(g, "hud", "Edit HUD", new Rect(8, height - 93, side - 16, 23), () -> leave(onOpenHudEditor), true, mouseX, mouseY, false);
            button(g, "packs", "Resource packs", new Rect(8, height - 65, side - 16, 23), () -> leave(onOpenResourcePacks), true, mouseX, mouseY, false);
            button(g, "video", "Video settings", new Rect(8, height - 37, side - 16, 23), () -> leave(onOpenVideoSettings), true, mouseX, mouseY, false);
        }
        if (modsView) renderMods(g, x, w, mouseX, mouseY);
        else if (detail == null) renderCatalog(g, x, w, side == 0, mouseX, mouseY);
        else renderDetails(g, x, w, mouseX, mouseY);
        g.drawText(fit(g, notice.isEmpty() ? "Ctrl+F search / Tab navigate / Esc back" : notice, w), x, height - 15, MUTED);
        colorPicker.render(g, mouseX, mouseY);
        actions.render(g, mouseX, mouseY);
        renderHudFpsDialog(g, mouseX, mouseY);
        boolean overlay = colorPicker.isOpen() || actions.isOpen() || fpsDialogOpen;
        if (tip.update(overlay || tipModule == null ? null : tipModule.getName(), now)) tooltip(g, tipModule.getDescription(), mouseX, mouseY);
    }
    /** Description tooltip beside the pointer, kept on screen and drawn last so it sits above the frame. */
    private void tooltip(LadsGraphics g, String text, int mx, int my) {
        List<String> lines = MenuGraphics.lines(g, text, 208, 8);
        if (lines.isEmpty()) return;
        int w = lines.stream().mapToInt(g::textWidth).max().orElse(0) + 12, h = lines.size() * 12 + 6;
        int x = Math.max(4, Math.min(mx + 10, width - w - 4)), y = my + 14 + h <= height - 4 ? my + 14 : Math.max(4, my - h - 6);
        round(g, x, y + 1, w, h + 1, 0x40000000);
        round(g, x, y, w, h, LadsPalette.BORDER);
        round(g, x + 1, y + 1, w - 2, h - 2, PANEL);
        for (int i = 0; i < lines.size(); i++) g.drawText(lines.get(i), x + 6, y + 5 + i * 12, TEXT);
    }
    /** True once the same key has been hovered for the delay; a new key restarts it, dismiss() hides it until the key changes. */
    static final class HoverDelay {
        private final long delay; private String key; private long since; private boolean dismissed;
        HoverDelay(long delayNanos) { delay = delayNanos; }
        boolean update(String key, long now) {
            if (!Objects.equals(key, this.key)) { this.key = key; since = now; dismissed = false; }
            return key != null && !dismissed && now - since >= delay;
        }
        void dismiss() { dismissed = true; }
    }
    private void renderCatalog(LadsGraphics g, int x, int w, boolean compact, int mx, int my) {
        boolean dense = height < 230;
        int top = 42;
        if (compact) {
            int cell = (w - (CATEGORIES.length - 1) * 4) / CATEGORIES.length;
            for (int i = 0; i < CATEGORIES.length; i++) {
                String cat = CATEGORIES[i];
                button(g, "category:" + cat, cat, new Rect(x + i * (cell + 4), top, cell, 20), () -> category(cat), true, mx, my, cat.equals(currentCategory));
            }
            top += dense ? 22 : 26;
        }
        int filterW = w >= 360 ? 78 : 54;
        Rect search = new Rect(x, top, w - filterW * 2 - 12, 24);
        button(g, "search", editingSearch ? inputDisplay() : searchQuery.isEmpty() ? "Search modules..." : searchQuery, search, this::startSearch, true, mx, my, editingSearch);
        button(g, "enabled", "Enabled", new Rect(search.x + search.width + 6, top, filterW, 24), () -> { enabledOnly = !enabledOnly; invalidate(); }, true, mx, my, enabledOnly);
        button(g, "favorites", "Favorites", new Rect(x + w - filterW, top, filterW, 24), () -> { favoritesOnly = !favoritesOnly; invalidate(); }, true, mx, my, favoritesOnly);
        top += dense ? 27 : 33;
        List<Module> modules = getFilteredModules();
        g.drawText(modules.size() + " MODULES", x, top, MUTED);
        if (compact) {
            button(g, "installed-mods", "Installed mods", new Rect(x + w - 219, top - 4, 84, 18), this::openMods, true, mx, my, false);
            button(g, "hud", "HUD", new Rect(x + w - 131, top - 4, 39, 18), () -> leave(onOpenHudEditor), true, mx, my, false);
            button(g, "packs", "Packs", new Rect(x + w - 87, top - 4, 39, 18), () -> leave(onOpenResourcePacks), true, mx, my, false);
            button(g, "video", "Video", new Rect(x + w - 43, top - 4, 43, 18), () -> leave(onOpenVideoSettings), true, mx, my, false);
        }
        if (currentCategory.equals("HUD") && !modsView && w >= 280) {
            String fpsStatus = "HUD FPS: " + (HudSettings.getInstance().isHudFpsCapEnabled() ? HudSettings.formatFpsLimit(HudSettings.getInstance().getHudFpsLimit()) : "Off");
            button(g, "cat-hud-fps", fpsStatus, new Rect(x + w - 160, top - 4, 158, 18), this::openHudFpsDialog, true, mx, my, HudSettings.getInstance().isHudFpsCapEnabled());
        }
        top += dense ? 17 : 22;
        viewport = new Rect(x, top, w, Math.max(20, height - top - 28));
        int cols = w >= 450 ? 3 : w >= 305 ? 2 : 1, gap = 8;
        // Cards: name row + a full-width 23 px Settings bar; the description is the hover tooltip and the card itself toggles.
        int cardW = (w - (cols - 1) * gap - 6) / cols, cardH = dense ? 52 : 58, pad = dense ? 4 : 5;
        maxScroll = Math.max(0, ((modules.size() + cols - 1) / cols) * (cardH + gap) - gap - viewport.height);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        g.enableScissor(x - 2, top - 2, x + w, top + viewport.height + 2); // 2 px for the hover halo
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            int cx = x + (i % cols) * (cardW + gap), cy = top + (i / cols) * (cardH + gap) - renderScroll;
            if (cy + cardH <= top || cy >= top + viewport.height) continue;
            String id = "card:" + m.getName();
            boolean toggleable = ModuleSupport.isToggleable(m.getName()), on = toggleable && m.isEnabled(), focused = focusId.equals(id);
            boolean cardHovered = new Rect(cx, cy, cardW, cardH).contains(mx, my) && viewport.contains(mx, my);
            if (cardHovered) tipModule = m;
            float hover = animate(id, cardHovered);
            int base = !toggleable ? CARD : on ? LadsPalette.CARD_ON : LadsPalette.CARD_OFF;
            int glow = !toggleable ? LadsPalette.DISABLED : on ? LadsPalette.CARD_ON_GLOW : LadsPalette.CARD_OFF_GLOW, fill = mix(base, glow, .14f * hover);
            round(g, cx - 1, cy + 2, cardW + 2, cardH + 2, 0x14000000);
            round(g, cx, cy + 1, cardW, cardH + 1, 0x24000000);
            if (toggleable && hover > 0) { // glow: a two-step halo outside the edge
                round(g, cx - 2, cy - 2, cardW + 4, cardH + 4, (int)(0x50 * hover) << 24 | glow & 0xFFFFFF);
                round(g, cx - 1, cy - 1, cardW + 2, cardH + 2, (int)(0x90 * hover) << 24 | glow & 0xFFFFFF);
            }
            if (focused) round(g, cx - 1, cy - 1, cardW + 2, cardH + 2, TEXT);
            round(g, cx, cy, cardW, cardH, mix(base, glow, .35f + .65f * hover));
            round(g, cx + 1, cy + 1, cardW - 2, cardH - 2, fill);
            String soon = toggleable ? "" : "Soon";
            if (toggleable) { // state pip, filled when on, so the state is not colour alone
                round(g, cx + 8, cy + pad + 6, 7, 7, TEXT);
                if (!on) round(g, cx + 9, cy + pad + 7, 5, 5, fill);
            } else g.drawText(soon, cx + cardW - 29 - g.textWidth(soon), cy + pad + 6, MUTED);
            g.drawText(fit(g, m.getName(), cardW - 49 - (toggleable ? 0 : g.textWidth(soon) + 4)), cx + 20, cy + pad + 6, toggleable ? TEXT : MUTED);
            chip(g, "favorite:" + m.getName(), m.isFavorite() ? "*" : "+", new Rect(cx + cardW - 25, cy + pad, 20, 20),
                () -> { m.setFavorite(!m.isFavorite()); changed(m); }, mx, my, m.isFavorite() ? TEXT : MUTED, false);
            chip(g, "detail:" + m.getName(), "Settings", new Rect(cx + pad, cy + cardH - pad - 23, cardW - 2 * pad, 23), () -> openDetails(m), mx, my, TEXT, true);
            // Added after the star and Settings so those win the click.
            controls.add(new Control(id, m.getName() + (on ? ", On" : toggleable ? ", Off" : ", Soon"), new Rect(cx, cy, cardW, cardH), () -> {
                if (ModuleSupport.isToggleable(m.getName())) { m.toggle(); changed(m); onNarrate.accept(m.getName() + (m.isEnabled() ? ", On" : ", Off")); }
            }, toggleable));
        }
        if (modules.isEmpty()) {
            g.drawText("No matching modules", x + 12, top + 18, TEXT);
            g.drawText("Try another search or filter.", x + 12, top + 34, MUTED);
        }
        g.disableScissor(); scrollbar(g);
    }
    private void renderDetails(LadsGraphics g, int x, int w, int mx, int my) {
        boolean wide = w >= 360;
        int leftW = wide ? Math.min(270, (w - 14) / 2) : w;
        int previewX = x + leftW + 12;
        int previewW = w - leftW - 12;

        button(g, "back", "< Modules", new Rect(x, 43, 86, 22), this::back, true, mx, my, false);
        g.drawText(fit(g, detail.getName(), leftW - 102), x + 96, 51, TEXT);
        int descriptionH = height < 230 ? 0 : MenuGraphics.wrap(g, detail.getDescription(), x, 76, leftW, 2, MUTED);
        int stateY = height < 230 ? 68 : 81 + descriptionH;
        g.drawText("LADS MODULE", x, stateY + 6, ACCENT);
        button(g, "toggle:detail", detail.getName().equals("DiscordRPC") ? "Soon" : detail.isEnabled() ? "ON" : "OFF", new Rect(x + leftW - 52, stateY, 52, 22),
            () -> { detail.toggle(); changed(detail); }, !detail.getName().equals("DiscordRPC"), mx, my, detail.isEnabled());
        int top = stateY + 30;
        if(detail.getOptions().stream().anyMatch(o -> o instanceof PlayerActionOption)) {
            button(g,"display-actions","Display actions...",new Rect(x,top,leftW,25),
                () -> actions.open(detail.getOptions().stream().filter(o -> o instanceof PlayerActionOption).map(o -> (PlayerActionOption)o).toList(), () -> changed(detail)),true,mx,my,false);
            top += 32;
        }
        viewport = new Rect(x, top, leftW, Math.max(20, height - top - 28));
        int rowH = 43;
        List<Option> options = activeOptions();
        g.enableScissor(x, top, x + leftW, top + viewport.height);
        // The Kill Banner picker scrolls with the options, above them.
        int picker = detail instanceof KillBannerModule banner ? killBannerPicker(g, banner, x, top - renderScroll, leftW - 8, mx, my) : 0;
        maxScroll = Math.max(0, picker + (options.size() + 1) * rowH - viewport.height);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        for (int i = 0; i < options.size(); i++) {
            int y = top + picker + i * rowH - renderScroll;
            if (y + rowH <= top || y >= top + viewport.height) continue;
            optionRow(g, options.get(i), x, y, leftW - 8, mx, my);
        }
        int resetY = top + picker + options.size() * rowH - renderScroll;
        if (resetY < top + viewport.height && resetY + 24 > top)
            button(g, "reset", "Reset options", new Rect(x, resetY + 4, Math.min(130, leftW - 8), 24),
                () -> { detail.getOptions().forEach(Option::reset); changed(detail); notice = "Options reset"; }, true, mx, my, false);
        g.disableScissor(); scrollbar(g);

        if (wide && previewW >= 80) {
            renderModulePreview(g, detail, previewX, 43, previewW, height - 68, mx, my);
        }
    }
    private static final String[] SKINS = {"base", "reaver", "rogue"};
    private static final String[] RANDOM_HINTS = {"Every kill shows the banner above.", "Each kill: another variant of the skin.",
        "Each kill: a random skin and variant.", "Each kill: one of the banners ticked below."};
    /** Kill Banner settings as pictures: skin tiles with their one-kill art, their variants, Custom, Randomize and triggers. Returns its height. */
    private int killBannerPicker(LadsGraphics g, KillBannerModule banner, int x, int y, int w, int mx, int my) {
        int start = y, gap = 4, tileW = (w - 3 * gap) / 4, tileH = Math.max(40, tileW * 3 / 4 + 12);
        int style = banner.bannerStyle.getIndex();
        y = section(g, "SKIN", x, y);
        String[] names = {"Base", "Reaver", "Rogue", "Custom"};
        KillBannerModule.Pick custom = banner.chosen();
        for (int i = 0; i < 4; i++) {
            int index = i;
            String skin = i < 3 ? SKINS[i] : custom.style() == null ? "base" : custom.style().id;
            int variant = i == 1 ? banner.reaverVariant.getIndex() : i == 2 ? banner.rogueVariant.getIndex() : i == 3 ? custom.variant() : 0;
            tile(g, "kb:skin:" + i, names[i], new Rect(x + i * (tileW + gap), y, tileW, tileH), skin, variant, style == i, mx, my,
                () -> { banner.bannerStyle.setIndex(index); changed(detail); });
        }
        y += tileH + 8;
        if (style == KillBannerModule.REAVER || style == KillBannerModule.ROGUE)
            y = variantTiles(g, banner, KillBannerModule.skin(style), x, y, tileW, tileH, mx, my);
        if (style == KillBannerModule.CUSTOM) {
            y = section(g, "CUSTOM BANNER", x, y);
            for (int i = 0; i < 3; i++) {
                int index = i;
                KillBannerStyle skin = KillBannerModule.skin(i);
                tile(g, "kb:visual:" + i, names[i], new Rect(x + i * (tileW + gap), y, tileW, tileH), SKINS[i], skin == null ? 0 : banner.variantOf(skin).getIndex(),
                    banner.customVisual.getIndex() == i, mx, my, () -> { banner.customVisual.setIndex(index); changed(detail); });
            }
            y += tileH + 8;
            KillBannerStyle visual = KillBannerModule.skin(banner.customVisual.getIndex());
            if (visual != null) y = variantTiles(g, banner, visual, x, y, tileW, tileH, mx, my);
            y = section(g, "CUSTOM SOUND (CLICK TO HEAR)", x, y);
            for (int i = 0; i < 3; i++) {
                int index = i;
                KillBannerStyle skin = KillBannerModule.skin(i);
                tile(g, "kb:sound:" + i, i == 0 ? "Chime" : names[i], new Rect(x + i * (tileW + gap), y, tileW, tileH), SKINS[i],
                    skin == null ? 0 : banner.variantOf(skin).getIndex(), banner.customSound.getIndex() == i, mx, my, () -> {
                        banner.customSound.setIndex(index); changed(detail);
                        g.getGame().previewKillBannerSound(SKINS[index], (float) banner.volume.getValue());
                    });
            }
            y += tileH + 8;
        }
        y = section(g, "RANDOMIZE", x, y);
        String[] modes = {"Off", "Variant", "Skin + variant", "Chosen"};
        int perRow = w >= 300 ? 4 : 2, cell = (w - (perRow - 1) * gap) / perRow;
        for (int i = 0; i < modes.length; i++) {
            int index = i;
            button(g, "kb:random:" + i, modes[i], new Rect(x + i % perRow * (cell + gap), y + i / perRow * 24, cell, 20),
                () -> { banner.randomize.setIndex(index); changed(detail); }, true, mx, my, banner.randomize.getIndex() == i);
        }
        y += (modes.length / perRow) * 24 + 2;
        g.drawText(fit(g, RANDOM_HINTS[banner.randomize.getIndex()], w), x, y, MUTED);
        y += 14;
        if (banner.randomize.getIndex() == KillBannerModule.RANDOM_CHOSEN) {
            var pool = banner.pool();
            for (KillBannerStyle skin : KillBannerStyle.values()) {
                String skinName = skinName(skin);
                for (int v = 0; v < skin.variantNames.length; v++) {
                    int variant = v;
                    Rect r = new Rect(x + v * (tileW + gap), y, tileW, tileH);
                    boolean on = pool.contains(skin.id + ":" + v);
                    tile(g, "kb:pool:" + skin.id + ":" + v, v == 0 ? skinName : skin.variantNames[v], r, skin.id, v, on, mx, my,
                        () -> { banner.togglePool(skin, variant); changed(detail); });
                    if (r.y + r.height > viewport.y && r.y < viewport.y + viewport.height) checkmark(g, r.x + r.width - 13, r.y + 3, on);
                }
                y += tileH + gap;
            }
            y += 4;
        }
        y = section(g, "SHOW A BANNER FOR", x, y);
        BoolOption[] kinds = {banner.players, banner.mobs, banner.bosses};
        int checkW = (w - 2 * gap) / 3;
        for (int i = 0; i < kinds.length; i++) {
            BoolOption kind = kinds[i];
            Rect r = new Rect(x + i * (checkW + gap), y, checkW, 22);
            button(g, "kb:kind:" + kind.getName(), "    " + kind.getName(), r, () -> { kind.toggle(); changed(detail); }, true, mx, my, false);
            if (r.y + r.height > viewport.y && r.y < viewport.y + viewport.height) checkmark(g, r.x + 7, r.y + 6, kind.get());
        }
        return y + 22 + 10 - start;
    }
    private static String skinName(KillBannerStyle skin) { return skin.name().charAt(0) + skin.name().substring(1).toLowerCase(Locale.ROOT); }
    private int variantTiles(LadsGraphics g, KillBannerModule banner, KillBannerStyle skin, int x, int y, int tileW, int tileH, int mx, int my) {
        y = section(g, skin.name() + " VARIANT", x, y);
        DropdownOption option = banner.variantOf(skin);
        for (int v = 0; v < skin.variantNames.length; v++) {
            int variant = v;
            tile(g, "kb:variant:" + skin.id + ":" + v, skin.variantNames[v], new Rect(x + v * (tileW + 4), y, tileW, tileH), skin.id, v,
                option.getIndex() == v, mx, my, () -> { option.setIndex(variant); changed(detail); });
        }
        return y + tileH + 8;
    }
    private static int section(LadsGraphics g, String label, int x, int y) {
        g.drawText(label, x, y, ACCENT);
        return y + 13;
    }
    private void tile(LadsGraphics g, String id, String label, Rect r, String skin, int variant, boolean selected, int mx, int my, Runnable action) {
        if (r.y + r.height <= viewport.y || r.y >= viewport.y + viewport.height) return;
        boolean hover = r.contains(mx, my) && viewport.contains(mx, my);
        float progress = animate(id, hover || focusId.equals(id));
        round(g, r.x, r.y, r.width, r.height, selected ? ACCENT : focusId.equals(id) ? LadsPalette.PRIMARY_HOVER : LadsPalette.BORDER);
        round(g, r.x + 1, r.y + 1, r.width - 2, r.height - 2, mix(CARD, LadsPalette.HOVER, progress));
        int artH = r.height - 13;
        if (!g.drawKillBanner(skin, variant, r.x + 3, r.y + 3, r.width - 6, artH - 3))
            g.drawCenteredText(skin.substring(0, 1).toUpperCase(Locale.ROOT), r.x + r.width / 2, r.y + artH / 2 - 3, MUTED);
        g.drawCenteredText(fit(g, label, r.width - 4), r.x + r.width / 2, r.y + r.height - 11, selected ? TEXT : MUTED);
        controls.add(new Control(id, label, r, action, true));
    }
    private static void checkmark(LadsGraphics g, int x, int y, boolean on) {
        g.fill(x, y, x + 10, y + 10, on ? ACCENT : LadsPalette.BORDER);
        g.fill(x + 1, y + 1, x + 9, y + 9, on ? ACCENT : CARD);
        if (on) { // a tick
            g.fill(x + 2, y + 5, x + 4, y + 7, TEXT); g.fill(x + 4, y + 6, x + 5, y + 8, TEXT);
            g.fill(x + 5, y + 5, x + 6, y + 7, TEXT); g.fill(x + 6, y + 4, x + 7, y + 6, TEXT); g.fill(x + 7, y + 2, x + 8, y + 5, TEXT);
        }
    }
    private void renderModulePreview(LadsGraphics g, Module m, int x, int y, int w, int h, int mx, int my) {
        if (m == null) return;
        round(g, x, y, w, h, PANEL);
        round(g, x, y, w, h, LadsPalette.BORDER);

        g.drawText("PREVIEW", x + 10, y + 9, ACCENT);
        String statusText = m.isEnabled() ? "ACTIVE" : "DISABLED";
        int statusColor = m.isEnabled() ? ACCENT : MUTED;
        g.drawText(statusText, x + w - g.textWidth(statusText) - 10, y + 9, statusColor);
        g.fill(x + 8, y + 24, x + w - 8, y + 25, LadsPalette.BORDER);

        int boxX = x + 8, boxY = y + 29, boxW = w - 16, boxH = h - 52;
        if (boxW < 20 || boxH < 20) return;

        round(g, boxX, boxY, boxW, boxH, CARD);
        round(g, boxX, boxY, boxW, boxH, 0x33000000);

        HudElement hudEl = HudManager.getInstance().getElements().stream()
            .filter(e -> e.getModuleName() != null && e.getModuleName().equalsIgnoreCase(m.getName()))
            .findFirst().orElse(null);

        if (hudEl != null) {
            g.enableScissor(boxX + 2, boxY + 2, boxX + boxW - 2, boxY + boxH - 2);
            var bounds = hudEl.measureBounds(g, true);
            int bw = Math.max(1, bounds.width());
            int bh = Math.max(1, bounds.height());
            float scale = 1.0f;
            if (bw > boxW - 16 || bh > boxH - 16) {
                scale = Math.min((float)(boxW - 16) / bw, (float)(boxH - 16) / bh);
            }
            int drawW = (int)(bw * scale);
            int drawH = (int)(bh * scale);
            int drawX = boxX + (boxW - drawW) / 2;
            int drawY = boxY + (boxH - drawH) / 2;

            g.pushPose();
            g.translate(drawX, drawY);
            if (scale != 1.0f) g.scale(scale, scale);
            hudEl.renderAt(g, 0, 0, true);
            g.popPose();
            g.disableScissor();

            g.drawCenteredText(fit(g, "Live HUD Preview · Real-time", boxW - 4), boxX + boxW / 2, y + h - 14, MUTED);
        } else {
            int centerX = boxX + boxW / 2;
            int centerY = boxY + boxH / 2;
            String name = m.getName();

            if (m instanceof KillBannerModule banner) {
                KillBannerModule.Pick pick = banner.chosen();
                if (!g.drawKillBanner(pick.style() == null ? "base" : pick.style().id, pick.variant(), boxX + 6, boxY + 6, boxW - 12, boxH - 30))
                    g.drawCenteredText("KILL BANNER", centerX, centerY - 4, ACCENT);
                String caption = pick.style() == null ? "Base" : skinName(pick.style()) + " · " + pick.style().variantNames[pick.variant()];
                if (pick.soundStyle() != pick.style()) caption += " · " + (pick.soundStyle() == null ? "Chime" : skinName(pick.soundStyle())) + " sound";
                if (banner.randomize.getIndex() != KillBannerModule.RANDOM_OFF) caption += " · Randomized";
                g.drawCenteredText(fit(g, caption, boxW - 8), centerX, boxY + boxH - 18, TEXT);
            } else if ("Crosshair".equalsIgnoreCase(name)) {
                int chColor = m.isEnabled() ? ACCENT : TEXT;
                int chSize = 7, chGap = 3;
                g.fill(centerX - chSize - chGap, centerY - 1, centerX - chGap, centerY + 1, chColor);
                g.fill(centerX + chGap, centerY - 1, centerX + chSize + chGap, centerY + 1, chColor);
                g.fill(centerX - 1, centerY - chSize - chGap, centerX + 1, centerY - chGap, chColor);
                g.fill(centerX - 1, centerY + chGap, centerX + 1, centerY + chSize + chGap, chColor);
                g.fill(centerX, centerY, centerX + 1, centerY + 1, 0xFFFFFFFF);
                g.drawCenteredText("Crosshair Reticle", centerX, centerY + 24, MUTED);
            } else if ("Fullbright".equalsIgnoreCase(name)) {
                round(g, centerX - 40, centerY - 25, 80, 50, 0x30FFFFFF);
                g.drawCenteredText("GAMMA BOOST", centerX, centerY - 10, ACCENT);
                g.drawCenteredText(m.isEnabled() ? "Max 1000% (Daylight)" : "Standard Gamma", centerX, centerY + 6, TEXT);
            } else if ("Zoom".equalsIgnoreCase(name)) {
                round(g, centerX - 36, centerY - 36, 72, 72, 0x40FFFFFF);
                round(g, centerX - 32, centerY - 32, 64, 64, CARD);
                g.drawCenteredText("ZOOM FOV", centerX, centerY - 8, ACCENT);
                g.drawCenteredText("Smooth Optic", centerX, centerY + 6, TEXT);
            } else if ("OldAnimations".equalsIgnoreCase(name) || "1.7 Animations".equalsIgnoreCase(name) || "LegacySwing".equalsIgnoreCase(name)) {
                round(g, centerX - 48, centerY - 25, 96, 50, 0x20FFFFFF);
                g.drawCenteredText("1.7 COMBAT STYLES", centerX, centerY - 10, ACCENT);
                g.drawCenteredText("Blockhit & Swing Active", centerX, centerY + 6, TEXT);
            } else if (name.toLowerCase(Locale.ROOT).contains("sprint") || name.toLowerCase(Locale.ROOT).contains("sneak")) {
                round(g, centerX - 55, centerY - 16, 110, 32, CARD);
                round(g, centerX - 55, centerY - 16, 110, 32, m.isEnabled() ? ACCENT : LadsPalette.BORDER);
                g.drawCenteredText(m.isEnabled() ? "[ " + name.toUpperCase(Locale.ROOT) + " (KEY) ]" : "[ DISABLED ]", centerX, centerY - 4, m.isEnabled() ? ACCENT : MUTED);
            } else {
                g.drawModIcon(name.toLowerCase(Locale.ROOT), centerX - 16, centerY - 36, 32);
                g.drawCenteredText(fit(g, name, boxW - 12), centerX, centerY + 6, TEXT);
                g.drawCenteredText(m.getCategory().name(), centerX, centerY + 20, ACCENT);
            }

            g.drawCenteredText(fit(g, "Live Settings Preview", boxW - 4), boxX + boxW / 2, y + h - 14, MUTED);
        }
    }
    public void openHudFpsDialog() {
        fpsDialogOpen = true;
        editingFps = false;
        draggingFpsSlider = false;
        int limit = HudSettings.getInstance().getHudFpsLimit();
        fpsBuffer = limit == 0 ? "0" : String.valueOf(limit);
    }
    public void closeHudFpsDialog() {
        commitFpsEdit();
        fpsDialogOpen = false;
        editingFps = false;
        draggingFpsSlider = false;
    }
    private void startEditFps() {
        editingFps = true;
        int limit = HudSettings.getInstance().getHudFpsLimit();
        fpsBuffer = limit == 0 ? "0" : String.valueOf(limit);
    }
    private void commitFpsEdit() {
        if (editingFps && !fpsBuffer.trim().isEmpty()) {
            try {
                int val = Integer.parseInt(fpsBuffer.trim());
                if (val >= 0 && val <= 1000) {
                    HudSettings.getInstance().setHudFpsLimit(val);
                    HudSettings.getInstance().setHudFpsCapEnabled(true);
                    ConfigManager.save();
                    dirty = true;
                }
            } catch (NumberFormatException ignored) {}
        }
        editingFps = false;
    }
    private int getClosestFpsLevelIndex(int currentFps) {
        int[] levels = HudSettings.HUD_FPS_LEVELS;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == currentFps || (levels[i] == 0 && (currentFps <= 0 || currentFps > 240))) return i;
        }
        int closestIdx = 2;
        int minDiff = Integer.MAX_VALUE;
        for (int i = 0; i < levels.length - 1; i++) {
            int diff = Math.abs(levels[i] - currentFps);
            if (diff < minDiff) {
                minDiff = diff;
                closestIdx = i;
            }
        }
        return closestIdx;
    }
    private void updateFpsSlider(double mouseX) {
        if (fpsSliderTrack == null || fpsSliderTrack.width <= 0) return;
        float fraction = (float)Math.max(0.0, Math.min(1.0, (mouseX - fpsSliderTrack.x) / fpsSliderTrack.width));
        int[] levels = HudSettings.HUD_FPS_LEVELS;
        int index = Math.round(fraction * (levels.length - 1));
        index = Math.max(0, Math.min(levels.length - 1, index));
        int chosen = levels[index];
        HudSettings.getInstance().setHudFpsLimit(chosen);
        if (!HudSettings.getInstance().isHudFpsCapEnabled()) HudSettings.getInstance().setHudFpsCapEnabled(true);
        fpsBuffer = chosen == 0 ? "0" : String.valueOf(chosen);
        ConfigManager.save();
        dirty = true;
    }
    private void renderHudFpsDialog(LadsGraphics g, int mx, int my) {
        if (!fpsDialogOpen) return;
        int dw = Math.min(370, width - 24);
        int dh = Math.min(235, height - 24);
        int dx = (width - dw) / 2;
        int dy = (height - dh) / 2;

        g.fill(0, 0, width, height, 0xB0000000);
        round(g, dx - 2, dy + 3, dw + 4, dh + 4, 0x14000000);
        round(g, dx, dy + 1, dw, dh, 0x28000000);

        round(g, dx, dy, dw, dh, PANEL);
        round(g, dx, dy, dw, dh, LadsPalette.BORDER);
        g.fill(dx, dy, dx + dw, dy + 2, ACCENT);

        g.drawText("GLOBAL HUD FRAME RATE CAP", dx + 12, dy + 11, ACCENT);
        g.drawText("Controls refresh rate of all HUD modules & vanilla HUD", dx + 12, dy + 23, MUTED);

        boolean closeHover = new Rect(dx + dw - 52, dy + 8, 42, 20).contains(mx, my);
        round(g, dx + dw - 52, dy + 8, 42, 20, closeHover ? LadsPalette.HOVER : CARD);
        g.drawCenteredText("Done", dx + dw - 31, dy + 13, TEXT);

        boolean capEnabled = HudSettings.getInstance().isHudFpsCapEnabled();
        int currentFps = HudSettings.getInstance().getHudFpsLimit();
        Rect toggleRect = new Rect(dx + 12, dy + 40, dw - 24, 22);
        boolean toggleHover = toggleRect.contains(mx, my);
        round(g, toggleRect.x, toggleRect.y, toggleRect.width, toggleRect.height,
            capEnabled ? (toggleHover ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY) : (toggleHover ? LadsPalette.HOVER : CARD));
        String statusLabel = capEnabled ? "HUD FPS LIMIT: ENABLED (" + HudSettings.formatFpsLimit(currentFps) + ")" : "HUD FPS LIMIT: OFF (UNCAPPED)";
        g.drawCenteredText(statusLabel, toggleRect.x + toggleRect.width / 2, toggleRect.y + 6, TEXT);

        fpsSliderTrack = new Rect(dx + 12, dy + 78, dw - 24, 14);
        int[] levels = HudSettings.HUD_FPS_LEVELS;
        int activeIdx = getClosestFpsLevelIndex(currentFps);
        float progress = (float) activeIdx / (levels.length - 1);

        round(g, fpsSliderTrack.x, fpsSliderTrack.y + 4, fpsSliderTrack.width, 6, CARD);
        int thumbX = (int)(fpsSliderTrack.x + progress * (fpsSliderTrack.width - 12));
        g.fill(fpsSliderTrack.x, fpsSliderTrack.y + 4, thumbX + 6, fpsSliderTrack.y + 10, ACCENT);

        for (int i = 0; i < levels.length; i++) {
            int tx = (int)(fpsSliderTrack.x + ((float)i / (levels.length - 1)) * (fpsSliderTrack.width - 2));
            g.fill(tx, fpsSliderTrack.y + 2, tx + 1, fpsSliderTrack.y + 12, i == activeIdx ? ACCENT : MUTED);
        }
        round(g, thumbX, fpsSliderTrack.y, 12, 14, draggingFpsSlider ? LadsPalette.PRIMARY_HOVER : ACCENT);
        round(g, thumbX + 2, fpsSliderTrack.y + 2, 8, 10, TEXT);

        int btnW = (dw - 24 - (levels.length - 1) * 3) / levels.length;
        int py = dy + 100;
        for (int i = 0; i < levels.length; i++) {
            int bx = dx + 12 + i * (btnW + 3);
            Rect btnRect = new Rect(bx, py, btnW, 18);
            boolean active = levels[i] == currentFps || (levels[i] == 0 && (currentFps <= 0 || currentFps > 240));
            boolean hover = btnRect.contains(mx, my);
            round(g, bx, py, btnW, 18, active ? LadsPalette.PRIMARY : (hover ? LadsPalette.HOVER : CARD));
            String lbl = levels[i] == 0 ? "Max" : levels[i] == 60 ? "60*" : String.valueOf(levels[i]);
            g.drawCenteredText(lbl, bx + btnW / 2, py + 4, active ? TEXT : MUTED);
        }

        g.drawText("Or type custom FPS:", dx + 12, dy + 130, MUTED);
        Rect textRect = new Rect(dx + 12, dy + 144, 76, 22);
        round(g, textRect.x, textRect.y, textRect.width, textRect.height, editingFps ? CARD : PANEL);
        round(g, textRect.x, textRect.y, textRect.width, textRect.height, editingFps ? ACCENT : LadsPalette.BORDER);
        String displayVal = editingFps ? (fpsBuffer + "|") : (currentFps == 0 ? "0" : String.valueOf(currentFps));
        g.drawCenteredText(displayVal, textRect.x + textRect.width / 2, textRect.y + 6, TEXT);

        Rect applyRect = new Rect(dx + 94, dy + 144, 48, 22);
        boolean applyHover = applyRect.contains(mx, my);
        round(g, applyRect.x, applyRect.y, applyRect.width, applyRect.height, applyHover ? LadsPalette.HOVER : CARD);
        g.drawCenteredText("Apply", applyRect.x + applyRect.width / 2, applyRect.y + 6, TEXT);

        g.drawText("60 FPS (Recommended) prevents tearing.", dx + 150, dy + 150, MUTED);

        String rateText = "Current HUD Rate: " + HudManager.getInstance().getMeasuredHudFps() + " FPS";
        g.drawText(rateText, dx + 12, dy + 185, ACCENT);
        g.drawText("Updates all HUD modules & vanilla HUD in real time.", dx + 12, dy + 200, MUTED);
    }
    private void renderMods(LadsGraphics g, int x, int w, int mx, int my) {
        pollModState();
        int top = 42, resetW = 56;
        button(g, "mods-back", "< Modules", new Rect(x, top, 86, 22), this::closeMods, true, mx, my, false);
        button(g, "mods-search", editingSearch ? inputDisplay() : modsSearch.isEmpty() ? "Search name or mod id..." : modsSearch,
            new Rect(x + 92, top, w - 98 - resetW, 22), this::startSearch, true, mx, my, editingSearch);
        button(g, "mods-reset", "Reset", new Rect(x + w - resetW, top, resetW, 22), this::resetModsFilters, true, mx, my, false);
        boolean dense = height < 230;
        top += dense ? 25 : 28;
        var filters = ModInventoryModel.Filter.values();
        int widest = java.util.Arrays.stream(filters).mapToInt(filter -> g.textWidth(filter.label())).max().orElse(0) + 10;
        int perRow = dense || (w - (filters.length - 1) * 4) / filters.length >= widest ? filters.length : 3, cell = (w - (perRow - 1) * 4) / perRow;
        for (int i = 0; i < filters.length; i++) {
            var filter = filters[i];
            button(g, "mods-filter:" + filter.name(), filter.label(), new Rect(x + i % perRow * (cell + 4), top + i / perRow * 22, cell, 18),
                () -> { modsFilter = filter; scrollOffset = 0; displayedScroll = 0; renderScroll = 0; }, true, mx, my, filter == modsFilter);
        }
        top += (filters.length + perRow - 1) / perRow * 22 + 2;
        if (!dense) { g.drawText(fit(g, modsCounts, w), x, top, MUTED); top += 12; }
        if (modsModel.notice() != null) { g.drawText(fit(g, modsModel.notice(), w), x, top, ACCENT); top += 12; }
        top += 4;
        viewport = new Rect(x, top, w, Math.max(20, height - top - 28));
        if (modsPlan != null) { renderModsPlan(g, x, top, w, mx, my); return; }
        List<ModLine> lines = new ArrayList<>();
        for (var row : modsModel.visible(modsFilter, modsSearch)) addModLines(row, 0, lines);
        int gap = 4, total = 0;
        for (ModLine line : lines) total += modRowHeight(line.row()) + gap;
        maxScroll = Math.max(0, total - gap - viewport.height);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        g.enableScissor(x, top, x + w, top + viewport.height);
        int y = top - renderScroll;
        for (ModLine line : lines) {
            int h = modRowHeight(line.row());
            if (y + h > top && y < top + viewport.height) modRow(g, line, x, y, w - 8, h, mx, my);
            y += h + gap;
        }
        if (lines.isEmpty()) {
            g.drawText("No matching entries", x + 12, top + 18, TEXT);
            g.drawText("Reset shows the full inventory.", x + 12, top + 34, MUTED);
        }
        g.disableScissor(); scrollbar(g);
    }
    private void addModLines(ModInventoryModel.Row row, int depth, List<ModLine> lines) {
        lines.add(new ModLine(row, depth));
        if (modExpanded(row)) for (var child : row.children()) addModLines(child, depth + 1, lines);
    }
    /** Parents shown only for a matching child open automatically; the +/- button flips either state. */
    private boolean modExpanded(ModInventoryModel.Row row) {
        return modsModel.autoExpanded(row, modsFilter, modsSearch) != modsToggled.contains(row.key());
    }
    private static String modExtraLine(ModInventoryModel.Row row) {
        String reason = row.canToggle() ? null : row.blockedReason();
        if (row.note() == null) return reason;
        return reason == null || row.note().contains(reason) ? row.note() : row.note() + " " + reason;
    }
    private static int modRowHeight(ModInventoryModel.Row row) { return modExtraLine(row) == null ? 34 : 45; }
    private void modRow(LadsGraphics g, ModLine line, int x, int y, int w, int h, int mx, int my) {
        var row = line.row();
        int rx = x + line.depth() * 14, rw = w - line.depth() * 14, textX = rx + 38, right = rx + rw - 6;
        round(g, rx, y, rw, h, line.depth() == 0 ? CARD : PANEL);
        if (!row.children().isEmpty()) {
            boolean open = modExpanded(row);
            button(g, "mods:expand:" + row.key(), open ? "-" : "+", new Rect(rx + 5, y + 5, 16, 16),
                () -> { if (!modsToggled.remove(row.key())) modsToggled.add(row.key()); }, true, mx, my, open);
            textX = rx + 54;
        }
        g.drawModIcon(row.nativeModule() ? "theladscore" : row.id(), textX - 28, y + 6, 24);
        if (row.embedded()) {
            var root = modsModel.find("mod/" + row.rootId());
            String label = "Disable " + (root == null ? row.rootId() : root.displayName()) + "...";
            int bw = Math.min(rw / 2, g.textWidth(label) + 14);
            right -= bw;
            button(g, "mods:parent:" + row.key(), label, new Rect(right, y + 6, bw, 18),
                () -> modsPlan = modsModel.plan(List.of(row.rootId()), false), root != null && root.requested() && root.canToggle(), mx, my, false);
            right -= 6;
        } else if (!"platform".equals(row.ownership())) {
            String state = row.nativeModule() && row.id().equals("DiscordRPC") ? "Soon" : row.requested() ? "ON" : "OFF";
            right -= 45;
            button(g, "mods:toggle:" + row.key(), state, new Rect(right, y + 6, 45, 18), () -> toggleModRow(row), row.canToggle(), mx, my, row.requested());
            right -= 6;
        }
        Runnable settings = modSettingsAction(row);
        if (settings != null) {
            right -= 58;
            button(g, "mods:settings:" + row.key(), "Settings", new Rect(right, y + 6, 58, 18), settings, true, mx, my, false);
            right -= 6;
        }
        g.drawText(fit(g, row.displayName() + (row.version() == null ? "" : "  " + row.version()), right - textX - 4), textX, y + 8, TEXT);
        int stateX = textX;
        if (row.restartRequired()) {
            g.drawText("Restart required", stateX, y + 21, ACCENT);
            stateX += g.textWidth("Restart required  ");
        }
        String state = ModInventoryModel.ownershipLabel(row) + " · " + (row.nativeModule() ? "" : row.id() + " · ") + modsModel.statusLabel(row)
            + (row.nativeModule() ? row.available() ? row.requested() ? " · On" : " · Off" : ""
                : (row.loaded() ? " · Loaded" : " · Not loaded") + (row.available() ? " · Next launch: " + (row.requested() ? "On" : "Off")
                    : " · Not available for " + java.util.Objects.requireNonNullElse(modsModel.minecraftVersion(), "this version")));
        g.drawText(fit(g, state, rx + rw - 8 - stateX), stateX, y + 21, MUTED);
        String extra = modExtraLine(row);
        if (extra != null) g.drawText(fit(g, extra, rw - 16), rx + 8, y + 33, MUTED);
    }
    private Runnable modSettingsAction(ModInventoryModel.Row row) {
        if (row.nativeModule()) {
            Module module = ModuleManager.getInstance().getModule(row.id());
            return module == null || !ModuleSupport.isBuiltIn(row.id()) ? null
                : () -> { openDetails(module); if (detail == module) { modsView = false; detailFromMods = true; } };
        }
        if (onOpenModSettings == null || !row.loaded() || row.embedded() || "platform".equals(row.ownership())
            || ModDependencyPlanner.CORE_ID.equals(row.id())) return null;
        return () -> leave(() -> onOpenModSettings.accept(row.id()));
    }
    private void renderModsPlan(LadsGraphics g, int x, int top, int w, int mx, int my) {
        var plan = modsPlan;
        maxScroll = 0; scrollOffset = 0; displayedScroll = 0; renderScroll = 0;
        round(g, x, top, w, viewport.height, PANEL);
        int y = top + 10, textW = w - 20;
        g.drawText(fit(g, (plan.enable() ? "Enable " : "Disable ") + modNames(plan.targetIds()) + " at the next launch?", textW), x + 10, y, TEXT);
        y += 16;
        if (!plan.alsoDisable().isEmpty()) y += MenuGraphics.wrap(g, "Also disable (they need it): " + modNames(plan.alsoDisable()), x + 10, y, textW, 4, MUTED) + 4;
        if (!plan.alsoEnable().isEmpty()) y += MenuGraphics.wrap(g, "Also enable (required): " + modNames(plan.alsoEnable()), x + 10, y, textW, 4, MUTED) + 4;
        for (String blocker : plan.blockers()) y += MenuGraphics.wrap(g, blocker, x + 10, y, textW, 3, ACCENT) + 4;
        for (String warning : plan.warnings()) y += MenuGraphics.wrap(g, warning, x + 10, y, textW, 3, MUTED) + 4;
        boolean allowed = plan.blockers().isEmpty();
        if (allowed) button(g, "mods-confirm", "Confirm", new Rect(x + 10, y + 6, 90, 22), () -> applyModsPlan(plan), true, mx, my, true);
        button(g, "mods-cancel", allowed ? "Cancel" : "Close", new Rect(x + (allowed ? 106 : 10), y + 6, 80, 22), () -> modsPlan = null, true, mx, my, false);
    }
    private String modNames(List<String> ids) {
        return ids.stream().map(id -> { var row = modsModel.find("mod/" + id); return row == null ? id : row.displayName(); })
            .collect(java.util.stream.Collectors.joining(", "));
    }
    private void toggleModRow(ModInventoryModel.Row row) {
        if (row.nativeModule()) {
            // Lads modules apply immediately through the same path as their catalog card.
            Module module = ModuleManager.getInstance().getModule(row.id());
            if (module == null || !ModuleSupport.isToggleable(row.id())) return;
            module.toggle(); changed(module); reloadMods();
            return;
        }
        var plan = modsModel.plan(List.of(row.id()), !row.requested());
        if (plan.needsConfirmation()) modsPlan = plan; else applyModsPlan(plan);
    }
    /** Records the whole plan as next-launch requests in one locked write; jars are renamed by the launcher before the next start. */
    private void applyModsPlan(ModDependencyPlanner.Plan plan) {
        modsPlan = null;
        if (!plan.blockers().isEmpty()) return;
        var store = new ModStateStore(ClientPaths.getBaseDir());
        try {
            store.setRequested(plan.requests(), modsModel.projectIds());
            notice = "Saved for the next launch. Restart the game to apply it.";
        } catch (IOException e) {
            LoggerFactory.getLogger("TheLadsCore").warn("Could not save mod requests to {}", store.file(), e);
            notice = "Could not save the mod request (" + e.getClass().getSimpleName() + ": " + e.getMessage() + "). Showing the saved state.";
        }
        reloadMods();
    }
    public void openMods() {
        if (!finish()) return;
        modsView = true; detail = null; detailFromMods = false; modsPlan = null;
        scrollOffset = 0; displayedScroll = 0; renderScroll = 0; focusId = ""; notice = "";
        reloadMods();
    }
    private void closeMods() { if (!finish()) return; modsView = false; modsPlan = null; scrollOffset = 0; displayedScroll = 0; renderScroll = 0; focusId = ""; notice = ""; }
    private void resetModsFilters() {
        modsFilter = ModInventoryModel.Filter.ALL; modsSearch = ""; modsToggled.clear();
        editingSearch = false; editBuffer = ""; cursor = 0; scrollOffset = 0; displayedScroll = 0; renderScroll = 0;
    }
    private void reloadMods() {
        var store = new ModStateStore(ClientPaths.getBaseDir());
        modsStamp = store.stamp(); modsCheckedNanos = System.nanoTime();
        modsModel = ModInventoryModel.load();
        modsCounts = modsModel.fileCounts().text();
        modsModel.logFirstViewForQa();
    }
    /** The launcher may change requests while the game runs; re-read at most every 2 s while the view is open. */
    private void pollModState() {
        long now = System.nanoTime();
        if (now - modsCheckedNanos < 2_000_000_000L) return;
        modsCheckedNanos = now;
        if (!new ModStateStore(ClientPaths.getBaseDir()).stamp().equals(modsStamp)) reloadMods();
    }
    private void optionRow(LadsGraphics g, Option option, int x, int y, int w, int mx, int my) {
        round(g, x, y, w, 37, CARD);
        int controlW = Math.min(155, w / 2);
        g.drawText(fit(g, option.getName(), w - controlW - 20), x + 9, y + 14, TEXT);
        Rect r = new Rect(x + w - controlW - 6, y + 7, controlW, 23);
        String id = "option:" + option.getName();
        if (option instanceof ActionOption action) {
            button(g, id, action.getLabel(), r, () -> leave(action::run), action.isAvailable(), mx, my, false);
        } else if (option instanceof BoolOption b) {
            button(g, id, b.get() ? "ON" : "OFF", r, () -> { b.toggle(); changed(detail); }, true, mx, my, b.get());
        } else if (option instanceof DropdownOption d) {
            button(g, id, "< " + d.getValue() + " >", r, () -> { d.cycle(); changed(detail); }, true, mx, my, false);
        } else if (option instanceof SliderOption || option instanceof DoubleOption) {
            double min = option instanceof SliderOption s ? s.getMin() : ((DoubleOption)option).getMin();
            double max = option instanceof SliderOption s ? s.getMax() : ((DoubleOption)option).getMax();
            double value = option instanceof SliderOption s ? s.getValue() : ((DoubleOption)option).get();
            button(g, id, String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", ""), r, () -> {}, true, mx, my, false);
            int fillW = (int)((controlW - 8) * (value - min) / Math.max(.001, max - min));
            g.fill(r.x + 4, r.y + 20, r.x + 4 + fillW, r.y + 22, ACCENT);
        } else if (option instanceof ColorOption c) {
            button(g, id, editingOption == option ? inputDisplay() : String.format("%08X", c.getColor()), new Rect(r.x, r.y, r.width - 58, r.height), () -> colorPicker.open(option.getName(), c.getColor(), value -> { c.setColor(value); c.setUseGlobal(false); changed(detail); }), true, mx, my, false);
            button(g, id + ":global", c.isUseGlobal() ? "Global" : "Own", new Rect(r.x + r.width - 54, r.y, 54, r.height), () -> { c.setUseGlobal(!c.isUseGlobal()); changed(detail); }, true, mx, my, c.isUseGlobal());
        } else if (option instanceof TextOption t) {
            button(g, id, editingOption == option ? inputDisplay() : t.getValue(), r, () -> startEdit(option), true, mx, my, editingOption == option);
        }
    }
    private void button(LadsGraphics g, String id, String label, Rect r, Runnable action, boolean enabled, int mx, int my, boolean selected) {
        boolean content = contentControl(id);
        if (content && (r.y + r.height <= viewport.y || r.y >= viewport.y + viewport.height)) return;
        boolean hover = r.contains(mx, my) && (!content || viewport.contains(mx, my));
        float progress = animate(id, enabled && (hover || focusId.equals(id)));
        if (focusId.equals(id)) round(g, r.x - 1, r.y - 1, r.width + 2, r.height + 2, ACCENT);
        int color = !enabled ? PANEL : selected ? mix(LadsPalette.PRIMARY, LadsPalette.PRIMARY_HOVER, progress) : mix(CARD, LadsPalette.HOVER, progress);
        round(g, r.x, r.y, r.width, r.height, color);
        g.drawCenteredText(fit(g, label, r.width - 10), r.x + r.width / 2, r.y + (r.height - g.fontHeight()) / 2 + 1, enabled ? TEXT : LadsPalette.DISABLED);
        controls.add(new Control(id, label, r, action, enabled));
    }
    /** Card sub-button: dark glass that reads on green, red and neutral cards; the Settings bar puts a gear before its label. */
    private void chip(LadsGraphics g, String id, String label, Rect r, Runnable action, int mx, int my, int color, boolean gear) {
        boolean focused = focusId.equals(id);
        float progress = animate(id, r.contains(mx, my) && viewport.contains(mx, my) || focused);
        if (focused) round(g, r.x - 1, r.y - 1, r.width + 2, r.height + 2, TEXT);
        round(g, r.x, r.y, r.width, r.height, mix(0x59000000, 0x30FFFFFF, progress));
        int textY = r.y + (r.height - g.fontHeight()) / 2 + 1, center = r.x + r.width / 2 + (gear ? 6 : 0);
        if (gear) gear(g, center - g.textWidth(label) / 2 - 13, textY - 1, color);
        g.drawCenteredText(label, center, textY, color);
        controls.add(new Control(id, label, r, action, true));
    }
    /** 9x9 cog: ring with a 3x3 hole, four teeth and four corner nubs. */
    private static void gear(LadsGraphics g, int x, int y, int color) {
        g.fill(x + 3, y, x + 6, y + 2, color); g.fill(x + 3, y + 7, x + 6, y + 9, color);
        g.fill(x, y + 3, x + 3, y + 6, color); g.fill(x + 6, y + 3, x + 9, y + 6, color);
        g.fill(x + 2, y + 2, x + 7, y + 3, color); g.fill(x + 2, y + 6, x + 7, y + 7, color);
        for (int i = 0; i < 4; i++) g.fill(x + 1 + i % 2 * 6, y + 1 + i / 2 * 6, x + 2 + i % 2 * 6, y + 2 + i / 2 * 6, color);
    }
    private float animate(String id, boolean active) {
        float target = active ? 1 : 0;
        float previous = hoverStates.getOrDefault(id, 0f);
        float value = previous + (target - previous) * frameBlend;
        if (Math.abs(value - target) < .001f) value = target;
        hoverStates.put(id, value);
        return value;
    }
    private boolean contentControl(String id) {
        return id.startsWith("option:") || id.startsWith("detail:") || id.startsWith("favorite:")
            || id.startsWith("card:") || id.equals("reset") || id.startsWith("mods:") || id.startsWith("kb:");
    }
    private void scrollbar(LadsGraphics g) {
        if (maxScroll == 0) return;
        int thumb = Math.max(12, viewport.height * viewport.height / (viewport.height + maxScroll));
        int y = viewport.y + (viewport.height - thumb) * scrollOffset / maxScroll;
        g.fill(viewport.x + viewport.width - 3, viewport.y, viewport.x + viewport.width, viewport.y + viewport.height, PANEL);
        g.fill(viewport.x + viewport.width - 3, y, viewport.x + viewport.width, y + thumb, MUTED);
    }
    public boolean mouseClicked(double x, double y, int button) {
        tip.dismiss();
        if (actions.click(x,y,button)) return true;
        if (colorPicker.click(x, y, button)) return true;
        if (fpsDialogOpen) {
            int dw = Math.min(370, width - 24);
            int dh = Math.min(235, height - 24);
            int dx = (width - dw) / 2;
            int dy = (height - dh) / 2;
            Rect dialogRect = new Rect(dx, dy, dw, dh);
            if (!dialogRect.contains(x, y)) {
                closeHudFpsDialog();
                return true;
            }
            if (new Rect(dx + dw - 52, dy + 8, 42, 20).contains(x, y)) {
                closeHudFpsDialog();
                return true;
            }
            Rect toggleRect = new Rect(dx + 12, dy + 40, dw - 24, 22);
            if (toggleRect.contains(x, y)) {
                boolean next = !HudSettings.getInstance().isHudFpsCapEnabled();
                HudSettings.getInstance().setHudFpsCapEnabled(next);
                ConfigManager.save();
                dirty = true;
                return true;
            }
            if (fpsSliderTrack != null && new Rect(fpsSliderTrack.x - 4, fpsSliderTrack.y - 4, fpsSliderTrack.width + 8, fpsSliderTrack.height + 8).contains(x, y)) {
                draggingFpsSlider = true;
                updateFpsSlider(x);
                return true;
            }
            int[] levels = HudSettings.HUD_FPS_LEVELS;
            int btnW = (dw - 24 - (levels.length - 1) * 3) / levels.length;
            int py = dy + 100;
            for (int i = 0; i < levels.length; i++) {
                Rect btnRect = new Rect(dx + 12 + i * (btnW + 3), py, btnW, 18);
                if (btnRect.contains(x, y)) {
                    HudSettings.getInstance().setHudFpsLimit(levels[i]);
                    HudSettings.getInstance().setHudFpsCapEnabled(true);
                    fpsBuffer = levels[i] == 0 ? "0" : String.valueOf(levels[i]);
                    ConfigManager.save();
                    dirty = true;
                    return true;
                }
            }
            Rect textRect = new Rect(dx + 12, dy + 144, 76, 22);
            if (textRect.contains(x, y)) {
                startEditFps();
                return true;
            }
            Rect applyRect = new Rect(dx + 94, dy + 144, 48, 22);
            if (applyRect.contains(x, y)) {
                commitFpsEdit();
                return true;
            }
            if (editingFps) {
                commitFpsEdit();
            }
            return true;
        }
        refreshCapabilities();
        if (button != 0 && button != 1) return false;
        for (Control c : List.copyOf(controls)) {
            if (!c.rect.contains(x, y) || !c.enabled || contentControl(c.id) && !viewport.contains(x, y)) continue;
            if (!commitEdit()) return true;
            focusId = c.id;
            if (detail != null && c.id.startsWith("option:") && !c.id.endsWith(":global")) {
                Option o = activeOption(c.id.substring(7));
                if (o instanceof SliderOption || o instanceof DoubleOption) { dragging = o; dragTrack = c.rect; updateDrag(x); return true; }
                // "< value >": the left half (or a right-click) steps back, the right half steps forward.
                if (o instanceof DropdownOption d) {
                    if (button == 1 || x < c.rect.x + c.rect.width / 2.0) d.cycleBack(); else d.cycle();
                    changed(detail); return true;
                }
            }
            if (button == 0) c.action.run();
            return true;
        }
        commitEdit(); return true;
    }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (fpsDialogOpen && draggingFpsSlider) { updateFpsSlider(x); return true; }
        if (colorPicker.move(x, y)) return true;
        refreshCapabilities();
        if (dragging == null) return false;
        updateDrag(x);
        return true;
    }
    public boolean mouseReleased(double x, double y, int button) {
        if (fpsDialogOpen && draggingFpsSlider) { updateFpsSlider(x); draggingFpsSlider = false; return true; }
        if (colorPicker.release()) return true;
        refreshCapabilities();
        if (dragging == null) return false;
        updateDrag(x);
        dragging = null;
        persist();
        return true;
    }
    private void updateDrag(double x) {
        double t = Math.max(0, Math.min(1, (x - dragTrack.x - 4) / Math.max(1, dragTrack.width - 8)));
        if (dragging instanceof SliderOption s) s.setValue(s.getMin() + (s.getMax() - s.getMin()) * t);
        else if (dragging instanceof DoubleOption d) d.set(d.getMin() + (d.getMax() - d.getMin()) * t);
        dirty = true; detail.touch(); persist();
    }
    public boolean mouseScrolled(double x, double y, double amount) {
        if (actions.wheel(amount)) return true;
        if (colorPicker.isOpen()) return true;
        if (!viewport.contains(x, y)) return false;
        tip.dismiss(); scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(amount * 28))); return true;
    }
    public boolean keyPressed(int key, int modifiers) {
        if (actions.key(key,modifiers)) return true;
        if (colorPicker.key(key)) return true;
        if (fpsDialogOpen) {
            if (key == 256) { closeHudFpsDialog(); return true; }
            if (editingFps) {
                if (key == 257) { commitFpsEdit(); return true; }
                if (key == 259) {
                    if (!fpsBuffer.isEmpty()) fpsBuffer = fpsBuffer.substring(0, fpsBuffer.length() - 1);
                    return true;
                }
            }
            if (key == 262 || key == 263) {
                int dir = key == 262 ? 1 : -1;
                int current = HudSettings.getInstance().getHudFpsLimit();
                int next = HudSettings.nextFpsLevel(current, dir);
                HudSettings.getInstance().setHudFpsLimit(next);
                HudSettings.getInstance().setHudFpsCapEnabled(true);
                fpsBuffer = next == 0 ? "0" : String.valueOf(next);
                ConfigManager.save();
                dirty = true;
                return true;
            }
            return true;
        }
        refreshCapabilities();
        boolean ctrl = (modifiers & 2) != 0;
        if (ctrl && key == 70) { if (!finish()) return true; if (!modsView) detail = null; startSearch(); return true; }
        if (key == 256) {
            if (editingSearch || editingOption != null) { editingSearch = false; editingOption = null; notice = ""; }
            else if (modsPlan != null) modsPlan = null;
            else if (modsView) closeMods();
            else if (detail != null) back(); else close();
            return true;
        }
        if (editingSearch || editingOption != null) {
            if (ctrl && key == 86) {
                String clipboard = clipboardReader.get();
                if (clipboard != null) clipboard.codePoints().limit(1024).forEach(this::charTyped);
                return true;
            }
            if (ctrl && key == 65) { selectAll = true; return true; }
            if (key == 257) { commitEdit(); return true; }
            if (key == 263) { if (cursor > 0) cursor = editBuffer.offsetByCodePoints(cursor, -1); selectAll = false; return true; }
            if (key == 262) { if (cursor < editBuffer.length()) cursor = editBuffer.offsetByCodePoints(cursor, 1); selectAll = false; return true; }
            if (key == 268) { cursor = 0; selectAll = false; return true; }
            if (key == 269) { cursor = editBuffer.length(); selectAll = false; return true; }
            if (key == 259 || key == 261) {
                if (selectAll) { editBuffer = ""; cursor = 0; selectAll = false; }
                else if (key == 259 && cursor > 0) { int previous = editBuffer.offsetByCodePoints(cursor, -1); editBuffer = editBuffer.substring(0, previous) + editBuffer.substring(cursor); cursor = previous; }
                else if (key == 261 && cursor < editBuffer.length()) editBuffer = editBuffer.substring(0, cursor) + editBuffer.substring(editBuffer.offsetByCodePoints(cursor, 1));
                if (editingSearch) applySearch();
                return true;
            }
            if (key != 258) return true;
        }
        if (key == 258) {
            if (!commitEdit()) return true;
            List<Control> all = controls.stream().filter(c -> c.enabled && (!contentControl(c.id) || c.rect.y >= viewport.y && c.rect.y + c.rect.height <= viewport.y + viewport.height)).toList();
            if (all.isEmpty()) return true;
            int current = -1;
            for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(focusId)) current = i;
            int next = Math.floorMod(current + ((modifiers & 1) != 0 ? -1 : 1), all.size());
            focusId = all.get(next).id; onNarrate.accept(all.get(next).label); return true;
        }
        if ((key == 262 || key == 263) && detail != null && focusId.startsWith("option:")) {
            Option o = activeOption(focusId.substring(7)); int direction = key == 262 ? 1 : -1;
            if (o instanceof SliderOption s) s.setValue(s.getValue() + direction * Math.max(s.getStep(), .1));
            else if (o instanceof DoubleOption d) d.set(d.get() + direction * .1);
            else if (o instanceof DropdownOption d) d.setIndex(d.getIndex() + direction);
            else return false;
            changed(detail); return true;
        }
        if (key == 257 || key == 32) for (Control c : List.copyOf(controls))
            if (c.id.equals(focusId) && c.enabled) { c.action.run(); return true; }
        if (key == 266 || key == 267) { scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (key == 267 ? 1 : -1) * viewport.height)); return true; }
        return false;
    }
    public boolean charTyped(int codePoint) {
        if (actions.type(codePoint)) return true;
        if (colorPicker.type(codePoint)) return true;
        if (fpsDialogOpen) {
            if (editingFps && codePoint >= '0' && codePoint <= '9' && fpsBuffer.length() < 4) {
                fpsBuffer += (char)codePoint;
                return true;
            }
            return true;
        }
        if (!editingSearch && editingOption == null || Character.isISOControl(codePoint) || !Character.isValidCodePoint(codePoint)) return false;
        String text = new String(Character.toChars(codePoint));
        if (selectAll) { editBuffer = ""; cursor = 0; selectAll = false; }
        if (editBuffer.length() + text.length() <= (editingSearch ? 64 : editingOption instanceof ColorOption ? 8 : 160)) {
            editBuffer = editBuffer.substring(0, cursor) + text + editBuffer.substring(cursor); cursor += text.length();
            if (editingSearch) applySearch();
        }
        return true;
    }
    private String inputDisplay() { return editBuffer.substring(0, cursor) + "|" + editBuffer.substring(cursor); }
    private void startSearch() { editingSearch = true; editingOption = null; editBuffer = modsView ? modsSearch : searchQuery; cursor = editBuffer.length(); selectAll = false; focusId = modsView ? "mods-search" : "search"; }
    private void applySearch() { if (modsView) { modsSearch = editBuffer; scrollOffset = 0; displayedScroll = 0; renderScroll = 0; } else setSearchQuery(editBuffer); }
    private void startEdit(Option o) { editingSearch = false; editingOption = o; editBuffer = o instanceof TextOption t ? t.getValue() : String.format("%08X", ((ColorOption)o).getColor()); cursor = editBuffer.length(); selectAll = true; }
    private boolean commitEdit() {
        if (editingOption instanceof ColorOption c) {
            try { if (editBuffer.length() != 8) throw new NumberFormatException(); c.setColor(Integer.parseUnsignedInt(editBuffer, 16)); c.setUseGlobal(false); }
            catch (NumberFormatException e) { notice = "Use 8 hex digits: AARRGGBB. Esc cancels."; return false; }
        } else if (editingOption instanceof TextOption t) t.setValue(editBuffer);
        if (editingOption != null) changed(detail);
        editingOption = null; editingSearch = false; selectAll = false; notice = ""; return true;
    }
    private void category(String cat) { if (!finish()) return; currentCategory = cat; detail = null; modsView = detailFromMods = false; modsPlan = null; invalidate(); }
    private void invalidate() { filterDirty = true; scrollOffset = 0; displayedScroll = 0; renderScroll = 0; focusId = ""; }
    private void changed(Module m) {
        if (m != null) m.touch(); dirty = true; filterDirty = true; persist();
    }
    private void persist() { if (dirty) { ConfigManager.save(); dirty = false; } }
    private List<Option> activeOptions() {
        if (detail == null || detail.getName().equals("DiscordRPC")) return List.of();
        return detail.getOptions().stream().filter(o -> !(o instanceof PlayerActionOption))
            .filter(o -> !(detail instanceof KillBannerModule banner && banner.pickerOption(o))).toList();
    }
    private Option activeOption(String name) { return activeOptions().stream().filter(o -> o.getName().equals(name)).findFirst().orElse(null); }
    private boolean finish() { if (!commitEdit()) return false; persist(); return true; }
    public void openGlobalColors(){colorPicker.openGlobal();}
    public void openDisplayActions(){if(detail!=null)actions.open(detail.getOptions().stream().filter(o->o instanceof PlayerActionOption).map(o->(PlayerActionOption)o).toList(),()->changed(detail));}
    public void openModule(String name) { Module m=ModuleManager.getInstance().getModule(name); if(m!=null)openDetails(m); }
    private void openDetails(Module m) { if (!ModuleSupport.isBuiltIn(m.getName())) return; detail = m; detailFromMods = false; m.setLastOpenedTime(System.currentTimeMillis()); scrollOffset = 0; displayedScroll = 0; renderScroll = 0; focusId = "back"; notice = ""; }
    private void back() {
        if (!finish()) return;
        detail = null; scrollOffset = 0; displayedScroll = 0; renderScroll = 0; focusId = "";
        if (detailFromMods) { detailFromMods = false; modsView = true; reloadMods(); }
    }
    private void leave(Runnable action) { if (!finish()) return; action.run(); }
    public void close() { refreshCapabilities(); leave(onClose); }
    public List<Module> getFilteredModules() {
        refreshCapabilities();
        if (!filterDirty) return filtered;
        String query = searchQuery.toLowerCase(Locale.ROOT);
        filtered = ModuleManager.getInstance().getModules().stream()
            .filter(m -> ModuleSupport.isBuiltIn(m.getName()))
            .filter(m -> currentCategory.equals("All") || currentCategory.equals("HUD") && m.getCategory() == Module.Category.HUD
                || currentCategory.equals("Server") && m.getCategory() == Module.Category.SERVER
                || currentCategory.equals("Performance") && PERFORMANCE.contains(m.getName())
                || currentCategory.equals("Gameplay") && m.getCategory() != Module.Category.HUD && m.getCategory() != Module.Category.SERVER && !PERFORMANCE.contains(m.getName()))
            .filter(m -> !enabledOnly || m.isEnabled())
            .filter(m -> !favoritesOnly || m.isFavorite())
            .filter(m -> (m.getName() + " " + m.getDescription()).toLowerCase(Locale.ROOT).contains(query))
            .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        ownershipRevision = ModuleSupport.revision(); filterDirty = false; return filtered;
    }
}
