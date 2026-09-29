package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.*;
import com.thelads.core.config.Module;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

    public void setOnOpenHudEditor(Runnable action) { onOpenHudEditor = action; }
    public void setReducedMotion(boolean value) { reducedMotion = value; }
    public void setOnClose(Runnable action) { onClose = action; }
    public void setOnOpenResourcePacks(Runnable action) { onOpenResourcePacks = action; }
    public void setOnOpenVideoSettings(Runnable action) { onOpenVideoSettings = action; }
    public void setOnNarrate(Consumer<String> action) { onNarrate = action; }
    public void refreshCapabilities() {
        if (ownershipRevision != ModuleSupport.revision()) {
            ownershipRevision = ModuleSupport.revision(); filterDirty = true; controls.clear();
        }
        if (detail != null && !ModuleSupport.isBuiltIn(detail.getName())) {
            detail = null; editingOption = null; dragging = null; editingSearch = false;
            editBuffer = ""; cursor = 0; notice = ""; invalidate();
        }
    }
    public void refreshCatalog() { filterDirty = true; refreshCapabilities(); }
    public void setClipboardReader(Supplier<String> reader) { clipboardReader = reader; }
    public void setSearchQuery(String value) { searchQuery = value == null ? "" : value; filterDirty = true; scrollOffset = 0; }
    public String getSearchQuery() { return searchQuery; }
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
        if (hoverStates.size() > 1024) hoverStates.clear();
        width = g.getScaledWidth(); height = g.getScaledHeight(); controls.clear();
        g.fill(0, 0, width, height, BG);
        int pad = width < 450 ? 10 : 20, side = width >= 530 && height >= 340 ? 118 : 0;
        g.fill(0, 0, width, 2, ACCENT);
        g.drawText("THE LADS", pad, 15, ACCENT);
        if (width >= 400) g.drawText(detail == null ? "MODS / MAKE IT YOURS" : "MODULE SETTINGS", pad + 68, 15, MUTED);
        button(g, "close", "Done", new Rect(width - pad - 52, 8, 52, 23), this::close, true, mouseX, mouseY, false);
        int x = side == 0 ? pad : side + 16, w = width - x - pad;
        if (side > 0) {
            g.fill(0, 40, side, height, PANEL);
            for (int i = 0; i < CATEGORIES.length; i++) {
                String category = CATEGORIES[i];
                button(g, "category:" + category, category, new Rect(8, 53 + i * 29, side - 16, 24), () -> category(category), true, mouseX, mouseY, category.equals(currentCategory));
            }
            button(g, "hud", "Edit HUD", new Rect(8, height - 93, side - 16, 23), () -> leave(onOpenHudEditor), true, mouseX, mouseY, false);
            button(g, "packs", "Resource packs", new Rect(8, height - 65, side - 16, 23), () -> leave(onOpenResourcePacks), true, mouseX, mouseY, false);
            button(g, "video", "Video settings", new Rect(8, height - 37, side - 16, 23), () -> leave(onOpenVideoSettings), true, mouseX, mouseY, false);
        }
        if (detail == null) renderCatalog(g, x, w, side == 0, mouseX, mouseY);
        else renderDetails(g, x, w, mouseX, mouseY);
        g.drawText(fit(g, notice.isEmpty() ? "Ctrl+F search / Tab navigate / Esc back" : notice, w), x, height - 15, MUTED);
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
            int cx = x + (i % cols) * (cardW + gap), cy = top + (i / cols) * (cardH + gap) - scrollOffset;
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
            String state = m.isEnabled() ? "ON" : "OFF";
            button(g, "toggle:" + m.getName(), state, new Rect(cx + cardW - 53, cy + cardH - 23, 45, 18), () -> {
                if (ModuleSupport.isBuiltIn(m.getName())) { m.toggle(); changed(m); }
            }, true, mx, my, status.configurable() && m.isEnabled());
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
        button(g, "toggle:detail", detail.isEnabled() ? "ON" : "OFF", new Rect(x + w - 52, stateY, 52, 22),
            () -> { detail.toggle(); changed(detail); }, true, mx, my, detail.isEnabled());
        int top = stateY + 30;
        viewport = new Rect(x, top, w, Math.max(20, height - top - 28));
        int rowH = 43;
        List<Option> options = activeOptions();
        maxScroll = Math.max(0, (options.size() + 1) * rowH - viewport.height);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        g.enableScissor(x, top, x + w, top + viewport.height);
        for (int i = 0; i < options.size(); i++) {
            int y = top + i * rowH - scrollOffset;
            if (y + rowH <= top || y >= top + viewport.height) continue;
            optionRow(g, options.get(i), x, y, w - 8, mx, my);
        }
        int resetY = top + options.size() * rowH - scrollOffset;
        if (resetY < top + viewport.height && resetY + 24 > top)
            button(g, "reset", "Reset options", new Rect(x, resetY + 4, Math.min(140, w - 8), 24),
                () -> { detail.getOptions().forEach(Option::reset); changed(detail); notice = "Options reset"; }, true, mx, my, false);
        g.disableScissor(); scrollbar(g);
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
            button(g, id, editingOption == option ? inputDisplay() : String.format("%08X", c.getColor()), new Rect(r.x, r.y, r.width - 29, r.height), () -> startEdit(option), true, mx, my, editingOption == option);
            button(g, id + ":global", c.isUseGlobal() ? "G" : "C", new Rect(r.x + r.width - 25, r.y, 25, r.height), () -> { c.setUseGlobal(!c.isUseGlobal()); changed(detail); }, true, mx, my, c.isUseGlobal());
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
            || id.startsWith("toggle:") && !id.equals("toggle:detail") || id.equals("reset");
    }
    private void scrollbar(LadsGraphics g) {
        if (maxScroll == 0) return;
        int thumb = Math.max(12, viewport.height * viewport.height / (viewport.height + maxScroll));
        int y = viewport.y + (viewport.height - thumb) * scrollOffset / maxScroll;
        g.fill(viewport.x + viewport.width - 3, viewport.y, viewport.x + viewport.width, viewport.y + viewport.height, PANEL);
        g.fill(viewport.x + viewport.width - 3, y, viewport.x + viewport.width, y + thumb, MUTED);
    }
    public boolean mouseClicked(double x, double y, int button) {
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
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) { refreshCapabilities(); if (dragging == null) return false; updateDrag(x); return true; }
    public boolean mouseReleased(double x, double y, int button) { refreshCapabilities(); if (dragging == null) return false; updateDrag(x); dragging = null; persist(); return true; }
    private void updateDrag(double x) {
        double t = Math.max(0, Math.min(1, (x - dragTrack.x - 4) / Math.max(1, dragTrack.width - 8)));
        if (dragging instanceof SliderOption s) s.setValue(s.getMin() + (s.getMax() - s.getMin()) * t);
        else if (dragging instanceof DoubleOption d) d.set(d.getMin() + (d.getMax() - d.getMin()) * t);
        dirty = true; detail.touch();
    }
    public boolean mouseScrolled(double x, double y, double amount) {
        if (!viewport.contains(x, y)) return false;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(amount * 28))); return true;
    }
    public boolean keyPressed(int key, int modifiers) {
        refreshCapabilities();
        boolean ctrl = (modifiers & 2) != 0;
        if (ctrl && key == 70) { if (!finish()) return true; detail = null; startSearch(); return true; }
        if (key == 256) {
            if (editingSearch || editingOption != null) { editingSearch = false; editingOption = null; notice = ""; }
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
                if (editingSearch) setSearchQuery(editBuffer);
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
        if (!editingSearch && editingOption == null || Character.isISOControl(codePoint) || !Character.isValidCodePoint(codePoint)) return false;
        String text = new String(Character.toChars(codePoint));
        if (selectAll) { editBuffer = ""; cursor = 0; selectAll = false; }
        if (editBuffer.length() + text.length() <= (editingSearch ? 64 : editingOption instanceof ColorOption ? 8 : 160)) {
            editBuffer = editBuffer.substring(0, cursor) + text + editBuffer.substring(cursor); cursor += text.length();
            if (editingSearch) setSearchQuery(editBuffer);
        }
        return true;
    }
    private String inputDisplay() { return editBuffer.substring(0, cursor) + "|" + editBuffer.substring(cursor); }
    private void startSearch() { editingSearch = true; editingOption = null; editBuffer = searchQuery; cursor = editBuffer.length(); selectAll = false; focusId = "search"; }
    private void startEdit(Option o) { editingSearch = false; editingOption = o; editBuffer = o instanceof TextOption t ? t.getValue() : String.format("%08X", ((ColorOption)o).getColor()); cursor = editBuffer.length(); selectAll = true; }
    private boolean commitEdit() {
        if (editingOption instanceof ColorOption c) {
            try { if (editBuffer.length() != 8) throw new NumberFormatException(); c.setColor(Integer.parseUnsignedInt(editBuffer, 16)); c.setUseGlobal(false); }
            catch (NumberFormatException e) { notice = "Use 8 hex digits: AARRGGBB. Esc cancels."; return false; }
        } else if (editingOption instanceof TextOption t) t.setValue(editBuffer);
        if (editingOption != null) changed(detail);
        editingOption = null; editingSearch = false; selectAll = false; notice = ""; return true;
    }
    private void category(String cat) { if (!finish()) return; currentCategory = cat; detail = null; invalidate(); }
    private void invalidate() { filterDirty = true; scrollOffset = 0; focusId = ""; }
    private void changed(Module m) {
        if (m != null) m.touch(); dirty = true; filterDirty = true; persist();
    }
    private void persist() { if (dirty) { ConfigManager.save(); dirty = false; } }
    private List<Option> activeOptions() { return detail == null ? List.of() : detail.getOptions(); }
    private Option activeOption(String name) { return activeOptions().stream().filter(o -> o.getName().equals(name)).findFirst().orElse(null); }
    private boolean finish() { if (!commitEdit()) return false; persist(); return true; }
    private void openDetails(Module m) { if (!ModuleSupport.isBuiltIn(m.getName())) return; detail = m; m.setLastOpenedTime(System.currentTimeMillis()); scrollOffset = 0; focusId = "back"; notice = ""; }
    private void back() { if (!finish()) return; detail = null; scrollOffset = 0; focusId = ""; }
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
