package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.*;
import com.thelads.core.config.Module;
import com.thelads.core.mods.ModDependencyPlanner;
import com.thelads.core.mods.ModInventoryModel;
import com.thelads.core.mods.ModStateStore;
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
    private static final Set<String> PERFORMANCE = Set.of("Performance", "DynamicFPS", "ImmediatelyFast", "Exordium", "RenderScale", "ScalableLux", "Clumps", "FarBlockEntities", "EntityCulling", "Lithium", "FerriteCore");
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
    public boolean isEditingText() { return editingSearch || editingOption != null || actions.isOpen() || colorPicker.isOpen(); }
    public String getCurrentCategory() { return currentCategory; }
    public String getCurrentTab() { return "MODS"; }
    public List<Rect> getControlBounds() { return controls.stream().map(Control::rect).toList(); }
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
        width = g.getScaledWidth(); height = g.getScaledHeight(); controls.clear();
        g.fill(0, 0, width, height, BG);
        int pad = width < 450 ? 10 : 20, side = width >= 530 && height >= 340 ? 118 : 0;
        g.fill(0, 0, width, 2, ACCENT);
        g.drawText("THE LADS", pad, 15, ACCENT);
        if (width >= 400) g.drawText(modsView ? "INSTALLED MODS" : detail == null ? "MODS / MAKE IT YOURS" : "MODULE SETTINGS", pad + 68, 15, MUTED);
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
        top += dense ? 17 : 22;
        viewport = new Rect(x, top, w, Math.max(20, height - top - 28));
        int cols = w >= 450 ? 3 : w >= 305 ? 2 : 1, gap = 8;
        int cardW = (w - (cols - 1) * gap - 6) / cols, cardH = dense ? 46 : 78;
        maxScroll = Math.max(0, ((modules.size() + cols - 1) / cols) * (cardH + gap) - gap - viewport.height);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        g.enableScissor(x, top, x + w, top + viewport.height);
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            int cx = x + (i % cols) * (cardW + gap), cy = top + (i / cols) * (cardH + gap) - renderScroll;
            if (cy + cardH <= top || cy >= top + viewport.height) continue;
            var status = ModuleSupport.get(m.getName());
            boolean cardHovered = new Rect(cx, cy, cardW, cardH).contains(mx, my) && viewport.contains(mx, my);
            float cardHover = animate("card:" + m.getName(), cardHovered);
            round(g, cx, cy + 2, cardW, cardH, 0x68060003);
            round(g, cx, cy, cardW, cardH, mix(LadsPalette.BORDER, LadsPalette.PRIMARY_HOVER, cardHover));
            round(g, cx + 1, cy + 1, cardW - 2, cardH - 2, mix(CARD, LadsPalette.HOVER, cardHover));
            g.fill(cx + 8, cy + 10, cx + 10, cy + 21, status.configurable() && m.isEnabled() ? ACCENT : MUTED);
            g.drawText(fit(g, m.getName(), cardW - 48), cx + 16, cy + 11, TEXT);
            if (!dense) MenuGraphics.wrap(g, m.getDescription(), cx + 10, cy + 29, cardW - 20, 2, MUTED);
            button(g, "favorite:" + m.getName(), m.isFavorite() ? "*" : "+", new Rect(cx + cardW - 25, cy + 5, 20, 20), () -> { m.setFavorite(!m.isFavorite()); changed(m); }, true, mx, my, m.isFavorite());
            button(g, "detail:" + m.getName(), "Settings", new Rect(cx + 8, cy + cardH - 23, cardW - 68, 18), () -> openDetails(m), true, mx, my, false);
            String state = m.getName().equals("DiscordRPC") ? "Soon" : m.isEnabled() ? "ON" : "OFF";
            button(g, "toggle:" + m.getName(), state, new Rect(cx + cardW - 53, cy + cardH - 23, 45, 18), () -> {
                if (ModuleSupport.isBuiltIn(m.getName())) { m.toggle(); changed(m); }
            }, !m.getName().equals("DiscordRPC"), mx, my, status.configurable() && m.isEnabled());
        }
        if (modules.isEmpty()) {
            g.drawText("No matching modules", x + 12, top + 18, TEXT);
            g.drawText("Try another search or filter.", x + 12, top + 34, MUTED);
        }
        g.disableScissor(); scrollbar(g);
    }
    private void renderDetails(LadsGraphics g, int x, int w, int mx, int my) {
        button(g, "back", "< Modules", new Rect(x, 43, 86, 22), this::back, true, mx, my, false);
        g.drawText(fit(g, detail.getName(), w - 102), x + 100, 51, TEXT);
        int descriptionH = height < 230 ? 0 : MenuGraphics.wrap(g, detail.getDescription(), x, 76, w, 2, MUTED);
        int stateY = height < 230 ? 68 : 81 + descriptionH;
        g.drawText("LADS MODULE", x, stateY + 6, ACCENT);
        button(g, "toggle:detail", detail.getName().equals("DiscordRPC") ? "Soon" : detail.isEnabled() ? "ON" : "OFF", new Rect(x + w - 52, stateY, 52, 22),
            () -> { detail.toggle(); changed(detail); }, !detail.getName().equals("DiscordRPC"), mx, my, detail.isEnabled());
        int top = stateY + 30;
        if(detail.getOptions().stream().anyMatch(o -> o instanceof PlayerActionOption)) {
            button(g,"display-actions","Display actions...",new Rect(x,top,w,25),
                () -> actions.open(detail.getOptions().stream().filter(o -> o instanceof PlayerActionOption).map(o -> (PlayerActionOption)o).toList(), () -> changed(detail)),true,mx,my,false);
            top += 32;
        }
        viewport = new Rect(x, top, w, Math.max(20, height - top - 28));
        int rowH = 43;
        List<Option> options = activeOptions();
        maxScroll = Math.max(0, (options.size() + 1) * rowH - viewport.height);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        g.enableScissor(x, top, x + w, top + viewport.height);
        for (int i = 0; i < options.size(); i++) {
            int y = top + i * rowH - renderScroll;
            if (y + rowH <= top || y >= top + viewport.height) continue;
            optionRow(g, options.get(i), x, y, w - 8, mx, my);
        }
        int resetY = top + options.size() * rowH - renderScroll;
        if (resetY < top + viewport.height && resetY + 24 > top)
            button(g, "reset", "Reset options", new Rect(x, resetY + 4, Math.min(140, w - 8), 24),
                () -> { detail.getOptions().forEach(Option::reset); changed(detail); notice = "Options reset"; }, true, mx, my, false);
        g.disableScissor(); scrollbar(g);
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
            || id.startsWith("toggle:") && !id.equals("toggle:detail") || id.equals("reset") || id.startsWith("mods:");
    }
    private void scrollbar(LadsGraphics g) {
        if (maxScroll == 0) return;
        int thumb = Math.max(12, viewport.height * viewport.height / (viewport.height + maxScroll));
        int y = viewport.y + (viewport.height - thumb) * scrollOffset / maxScroll;
        g.fill(viewport.x + viewport.width - 3, viewport.y, viewport.x + viewport.width, viewport.y + viewport.height, PANEL);
        g.fill(viewport.x + viewport.width - 3, y, viewport.x + viewport.width, y + thumb, MUTED);
    }
    public boolean mouseClicked(double x, double y, int button) {
        if (actions.click(x,y,button)) return true;
        if (colorPicker.click(x, y, button)) return true;
        refreshCapabilities();
        if (button != 0 && button != 1) return false;
        for (Control c : List.copyOf(controls)) {
            if (!c.rect.contains(x, y) || !c.enabled || contentControl(c.id) && !viewport.contains(x, y)) continue;
            if (!commitEdit()) return true;
            focusId = c.id;
            if (detail != null && c.id.startsWith("option:") && !c.id.endsWith(":global")) {
                Option o = activeOption(c.id.substring(7));
                if (o instanceof SliderOption || o instanceof DoubleOption) { dragging = o; dragTrack = c.rect; updateDrag(x); return true; }
                if (o instanceof DropdownOption d && button == 1) { d.cycleBack(); changed(detail); return true; }
            }
            if (button == 0) c.action.run();
            return true;
        }
        commitEdit(); return true;
    }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) { if (colorPicker.move(x, y)) return true; refreshCapabilities(); if (dragging == null) return false; updateDrag(x); return true; }
    public boolean mouseReleased(double x, double y, int button) { if (colorPicker.release()) return true; refreshCapabilities(); if (dragging == null) return false; updateDrag(x); dragging = null; persist(); return true; }
    private void updateDrag(double x) {
        double t = Math.max(0, Math.min(1, (x - dragTrack.x - 4) / Math.max(1, dragTrack.width - 8)));
        if (dragging instanceof SliderOption s) s.setValue(s.getMin() + (s.getMax() - s.getMin()) * t);
        else if (dragging instanceof DoubleOption d) d.set(d.getMin() + (d.getMax() - d.getMin()) * t);
        dirty = true; detail.touch();
    }
    public boolean mouseScrolled(double x, double y, double amount) {
        if (actions.wheel(amount)) return true;
        if (colorPicker.isOpen()) return true;
        if (!viewport.contains(x, y)) return false;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(amount * 28))); return true;
    }
    public boolean keyPressed(int key, int modifiers) {
        if (actions.key(key,modifiers)) return true;
        if (colorPicker.key(key)) return true;
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
    private List<Option> activeOptions() { return detail == null || detail.getName().equals("DiscordRPC") ? List.of() : detail.getOptions().stream().filter(o -> !(o instanceof PlayerActionOption)).toList(); }
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
