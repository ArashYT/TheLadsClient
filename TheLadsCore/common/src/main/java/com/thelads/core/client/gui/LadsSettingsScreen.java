package com.thelads.core.client.gui;

import com.thelads.core.client.CrosshairBackground;
import com.thelads.core.client.CrosshairProfiles;
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
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.sound.CustomSounds;
import com.thelads.core.modules.BetterResolutionModule;
import com.thelads.core.modules.ComboSoundsModule;
import com.thelads.core.modules.CrosshairModule;
import com.thelads.core.modules.KillBannerModule;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.client.ladsapi.LadsApiUi;
import com.thelads.core.client.ladsapi.ModuleState;
import com.thelads.core.client.ladsapi.OptionRule;
import com.thelads.core.client.ladsapi.ServerPolicy;
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
    private String currentCategory = ModuleGroups.HOME, searchQuery = "", focusId = "", notice = "";
    private boolean enabledOnly, favoritesOnly, filterDirty = true, dirty;
    private boolean fpsDialogOpen, editingFps, draggingFpsSlider;
    private Rect fpsSliderTrack;
    private final Map<String, Rect> fpsExceptionRects = new LinkedHashMap<>();
    private String fpsBuffer = "";
    private long ownershipRevision = -1;
    private int scrollOffset, maxScroll, width = 640, height = 360;
    private boolean draggingScrollbar;
    private int scrollbarDragAnchor;
    private Rect viewport = new Rect(0, 0, 640, 360);
    private final List<Control> controls = new ArrayList<>();
    private final Map<String, Float> hoverStates = new HashMap<>();
    private long previousFrameNanos;
    private float frameBlend = 1;
    private boolean reducedMotion;
    private final ColorPicker colorPicker = new ColorPicker();
    private final ActionDropdown actions = new ActionDropdown();
    private final ParticleRatesDialog particleRates = new ParticleRatesDialog();
    private final ConfirmDialog confirm = new ConfirmDialog();
    private final ClientImportDialog clientImport = new ClientImportDialog();
    private static volatile boolean openNetworkHubRequested;
    private final LadsNetworkHubDialog networkHub = new LadsNetworkHubDialog();
    {
        networkHub.setOnAfterJoin(this::close);
        if (consumeOpenNetworkHubRequest()) networkHub.open();
    }
    public static void requestOpenNetworkHub() { openNetworkHubRequested = true; }
    private static boolean consumeOpenNetworkHubRequest() {
        if (!openNetworkHubRequested) return false;
        openNetworkHubRequested = false;
        return true;
    }
    public void openNetworkHub() { networkHub.open(); }
    public LadsNetworkHubDialog networkHub() { return networkHub; }
    /** The sound library and trimmer every uploaded-sound slot opens (Choose, Add). */
    private final SoundLibraryDialog library = new SoundLibraryDialog(confirm, () -> changed(ModuleManager.getInstance().getModule(ComboSoundsModule.NAME)));
    private double displayedScroll;
    private int renderScroll;
    private int catalogScrollOffset;
    private double catalogDisplayedScroll;
    private int catalogRenderScroll;
    private List<Module> filtered = List.of();
    private Module detail;
    private Option editingOption, dragging;
    private Rect dragTrack, gameView;
    private boolean editingSearch, selectAll;
    private String editBuffer = "";
    private int cursor;
    private Runnable onOpenHudEditor = () -> {}, onClose = () -> {}, onOpenResourcePacks = () -> {}, onOpenVideoSettings = () -> {};
    private Consumer<String> onNarrate = ignored -> {};
    private Supplier<String> clipboardReader = () -> "";
    private Consumer<String> clipboardWriter = text -> {};
    /** Credit for the Flaticon icons (THIRD_PARTY_NOTICES.txt). */
    private static final String ICON_CREDIT = "Icons by Flaticon (flaticon.com)";
    private static final List<String> CATEGORIES = ModuleGroups.CATEGORIES;
    /** One line of the catalog page: a heading, a hint, a fold-out group, a row (one module, or a bundle's), or a setting search found. */
    private interface Line {}
    private record Heading(String text) implements Line {}
    private record Hint(String text) implements Line {}
    private record Fold(String key, String title, boolean open, int on, int total) implements Line {}
    private record Row(String title, List<Module> members) implements Line {}
    private record Hit(Module module, Option option) implements Line {}
    private static final int MAX_OPTION_HITS = 60, RECENT_ROWS = 6;
    private List<Line> optionHits = List.of();
    /** The setting a search result opens: its page scrolls to it on the next frame. */
    private Option revealOption;
    private Row tipRow;
    // Installed mods view: its own state, search and "mods:" row ids, so the native catalog above stays native-only.
    private record ModLine(ModInventoryModel.Row row, int depth) {}
    private boolean modsView, detailFromMods;
    private String modsSearch = "", modsStamp = "", modsCounts = "";
    private String kbSearch = "";
    private boolean editingKbSearch;
    private ModInventoryModel.Filter modsFilter = ModInventoryModel.Filter.ALL;
    private ModInventoryModel modsModel;
    private ModDependencyPlanner.Plan modsPlan;
    private final Set<String> modsToggled = new HashSet<>();
    private long modsCheckedNanos;
    private Consumer<String> onOpenModSettings;
    // Module description tooltip: shown once the pointer rests on one catalog card for 750 ms.
    private final HoverDelay tip = new HoverDelay(750_000_000L);
    private Module tipModule;
    private Option tipOption;
    private OptionRule tipOptionRule;

    /** Menu category of a Lads module, shared with the launcher catalog. */
    public static String categoryOf(Module m) {
        String grouped = ModuleGroups.categoryOf(m.getName());
        if (grouped != null) return grouped;
        return m.getCategory() == Module.Category.HUD ? ModuleGroups.HUD : m.getCategory() == Module.Category.SERVER ? ModuleGroups.SERVER
            : m.getCategory() == Module.Category.HYPIXEL ? ModuleGroups.HYPIXEL : ModuleGroups.CLIENT;
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
    public void setClipboardReader(Supplier<String> reader) { clipboardReader = reader != null ? reader : () -> ""; }
    public Supplier<String> getClipboardReader() { return clipboardReader; }
    public void setClipboardWriter(Consumer<String> writer) { clipboardWriter = writer != null ? writer : text -> {}; }
    public Consumer<String> getClipboardWriter() { return clipboardWriter; }
    public void setSearchQuery(String value) { searchQuery = value == null ? "" : value; filterDirty = true; scrollOffset = 0; displayedScroll = 0; renderScroll = 0; catalogScrollOffset = 0; catalogDisplayedScroll = 0; catalogRenderScroll = 0; }
    public String getSearchQuery() { return searchQuery; }
    public boolean isEditingText() { return editingSearch || editingOption != null || editingFps || actions.isOpen() || particleRates.isOpen() || clientImport.isOpen() || networkHub.isOpen() || colorPicker.isOpen() || fpsDialogOpen || library.isOpen(); }
    public String getCurrentCategory() { return currentCategory; }
    public String getCurrentTab() { return "MODS"; }
    public List<Rect> getControlBounds() { return controls.stream().map(Control::rect).toList(); }
    /** Bounds of a control drawn by the last render (e.g. "search", "option:Size"), or null; QA drives real input at them. */
    public Rect controlBounds(String id) { return controls.stream().filter(c -> c.id.equals(id)).map(Control::rect).findFirst().orElse(null); }
    /** QA: the control was drawn whole inside the page's scrolling area by the last render (a click at its middle reaches it). */
    public boolean revealed(String id) {
        Rect r = controlBounds(id);
        return r != null && (!contentControl(id) || r.y >= viewport.y && r.y + r.height <= viewport.y + viewport.height);
    }
    /** Ids of the controls drawn by the last render (QA scripts click them by id). */
    public List<String> controlIds() { return controls.stream().map(c -> c.id).toList(); }
    public int getScrollOffset() { return scrollOffset; }
    public int getMaxScroll() { return maxScroll; }
    public boolean isDraggingScrollbar() { return draggingScrollbar; }
    public String getFocusId() { return focusId; }
    /** Screen bounds of the scrollbar thumb drawn by the last render, or null when not scrollable. */
    public Rect scrollbarThumbBounds() {
        if (maxScroll == 0) return null;
        int trackW = 4;
        int trackX = viewport.x + viewport.width - trackW;
        int thumb = Math.max(14, (int)((long)viewport.height * viewport.height / (viewport.height + maxScroll)));
        int trackAvail = viewport.height - thumb;
        int y = viewport.y + (trackAvail > 0 ? (int)((long)trackAvail * scrollOffset / maxScroll) : 0);
        return new Rect(trackX, y, trackW, thumb);
    }
    /** Screen bounds of the scrollbar track drawn by the last render, or null when not scrollable. */
    public Rect scrollbarTrackBounds() {
        if (maxScroll == 0) return null;
        int trackW = 4;
        int trackX = viewport.x + viewport.width - trackW;
        return new Rect(trackX, viewport.y, trackW, viewport.height);
    }
    /**
     * True when the module has a real live or visual preview (HUD elements, animated kill banners,
     * custom crosshairs, live world Fullbright). Modules with placeholder cards (e.g. Hypixel Mods,
     * performance tweaks, zoom, animations) return false and lay their settings out in two columns.
     */
    private static HudElement findHudElement(String name) {
        if (name == null) return null;
        List<HudElement> elements = HudManager.getInstance().getElements();
        for (int i = 0, n = elements.size(); i < n; i++) {
            HudElement e = elements.get(i);
            if (e.getModuleName() != null && e.getModuleName().equalsIgnoreCase(name)) return e;
        }
        return null;
    }
    public static boolean hasPreview(Module m) {
        if (m == null) return false;
        String name = m.getName();
        if (findHudElement(name) != null) {
            return true;
        }
        if (m instanceof KillBannerModule || m instanceof CrosshairModule) return true;
        if ("Fullbright".equalsIgnoreCase(name)) return true;
        if ("Nametags".equalsIgnoreCase(name) || "ToggleNametags".equalsIgnoreCase(name)) return true;
        if ("Hitboxes".equalsIgnoreCase(name)) return true;
        if ("Chat".equalsIgnoreCase(name) || "ChatFilter".equalsIgnoreCase(name)) return true;
        if ("BetterF3".equalsIgnoreCase(name)) return true;
        if ("DiscordRPC".equalsIgnoreCase(name) || name.toLowerCase(Locale.ROOT).contains("discord")) return true;
        return false;
    }
    public boolean currentDetailHasPreview() { return hasPreview(detail); }
    /** The slider track of a particle type's row in Particle Core's open rates editor, or null: QA and tests click at it. */
    public Rect particleRatesTrack(String id) {
        int[] t = particleRates.track(id);
        return t == null ? null : new Rect(t[0], t[1], t[2], t[3]);
    }
    /** Where the last frame drew the live game view (GUI pixels), or null (QA measures the world there). */
    public Rect gameViewBounds() { return gameView; }
    /** True while the open page draws the live game view in its preview (Fullbright): 1.8.9 copies the framebuffer before the menu paints. */
    public boolean showsGameView() {
        return detail != null && ("Fullbright".equalsIgnoreCase(detail.getName())
            || detail instanceof CrosshairModule crosshair && crosshair.background.getIndex() == CrosshairBackground.LIVE);
    }
    /** The reset confirmation (QA reads its bounds and clicks them). */
    public ConfirmDialog confirmDialog() { return confirm; }
    /** The import dialog (QA reads its click targets and clicks them). */
    ClientImportDialog clientImportDialog() { return clientImport; }
    /** The sound library (QA reads its bounds and clicks them). */
    public SoundLibraryDialog soundLibrary() { return library; }
    public List<String> visibleModuleNames() { return getFilteredModules().stream().map(Module::getName).toList(); }

    public void render(LadsGraphics g, int pointerX, int pointerY) {
        refreshCapabilities();
        if (consumeOpenNetworkHubRequest()) openNetworkHub();
        // A popup is modal: the screen under it neither highlights nor reacts to the pointer.
        boolean modal = colorPicker.isOpen() || actions.isOpen() || particleRates.isOpen() || clientImport.isOpen() || networkHub.isOpen() || fpsDialogOpen || confirm.isOpen() || library.isOpen();
        int mouseX = modal ? -100000 : pointerX, mouseY = modal ? -100000 : pointerY;
        long now = System.nanoTime();
        double elapsed = previousFrameNanos == 0 ? 0 : Math.min(.1, (now - previousFrameNanos) / 1e9);
        previousFrameNanos = now;
        frameBlend = reducedMotion ? 1 : (float)(1 - Math.exp(-elapsed * 18));
        displayedScroll += (scrollOffset - displayedScroll) * frameBlend;
        if (Math.abs(scrollOffset - displayedScroll) < .2) displayedScroll = scrollOffset;
        renderScroll = (int)Math.round(displayedScroll);
        if (hoverStates.size() > 1024) hoverStates.clear();
        width = g.getScaledWidth(); height = g.getScaledHeight(); controls.clear(); tipModule = null; tipRow = null; tipOption = null; tipOptionRule = null; gameView = null;
        g.fill(0, 0, width, height, BG);
        // The side bar's rows shrink to 18 px to fit twelve categories and four shortcuts in 356 GUI px.
        int pad = width < 450 ? 10 : 20, side = width >= 530 && height >= 356 ? 118 : 0;
        g.fill(0, 0, width, 2, ACCENT);
        String fpsBtnLabel = "HUD FPS: " + (HudSettings.getInstance().isHudFpsCapEnabled() ? HudSettings.formatFpsLimit(HudSettings.getInstance().getHudFpsLimit()) : "Off");
        int fpsBtnW = Math.max(78, g.textWidth(fpsBtnLabel) + 14);
        // Backgrounds and Text Shadow sit left of Colors; short labels only when the window is too narrow for the full ones.
        String bgLabel = ColorPicker.backgroundsLabel(), shLabel = ColorPicker.textShadowLabel();
        int bgW = g.textWidth(bgLabel) + 14, shW = g.textWidth(shLabel) + 14;
        if (width - pad - 116 - shW - 6 - bgW - 6 - fpsBtnW < pad + 60) {
            bgLabel = "BG"; shLabel = "Shadow"; bgW = g.textWidth(bgLabel) + 14; shW = g.textWidth(shLabel) + 14;
        }
        // Still too narrow (down to 320 GUI px): HUD FPS drops "(Recommended)", so no button leaves the screen.
        if (width - pad - 116 - shW - 6 - bgW - 6 - fpsBtnW < pad + 60) {
            fpsBtnLabel = fpsBtnLabel.replace(" (Recommended)", "");
            fpsBtnW = g.textWidth(fpsBtnLabel) + 14;
        }
        int shX = width - pad - 116 - shW, bgX = shX - 6 - bgW, fpsX = bgX - 6 - fpsBtnW;
        // "THE LADS" gives way to the buttons when they reach it.
        if (fpsX >= pad + g.textWidth("THE LADS") + 6) g.drawText("THE LADS", pad, 15, ACCENT);
        // The title line stops where the HUD FPS button begins ("60 (Recommended)" makes that button wide).
        if (width >= 400) g.drawText(fit(g, modsView ? "INSTALLED MODS" : detail == null ? "MODS / MAKE IT YOURS" : "MODULE SETTINGS", fpsX - 6 - (pad + 68)), pad + 68, 15, MUTED);
        button(g, "global-hud-fps", fpsBtnLabel, new Rect(fpsX, 8, fpsBtnW, 23), this::openHudFpsDialog, true, mouseX, mouseY, fpsDialogOpen);
        button(g, "global-backgrounds", bgLabel, new Rect(bgX, 8, bgW, 23), ColorPicker::toggleBackgrounds, true, mouseX, mouseY, HudSettings.getInstance().isBackgrounds());
        button(g, "global-text-shadow", shLabel, new Rect(shX, 8, shW, 23), ColorPicker::cycleTextShadow, true, mouseX, mouseY, HudSettings.getInstance().isTextShadow());
        button(g, "global-colors", "Colors", new Rect(width - pad - 110, 8, 52, 23), colorPicker::openGlobal, true, mouseX, mouseY, false);
        button(g, "close", "Done", new Rect(width - pad - 52, 8, 52, 23), this::close, true, mouseX, mouseY, false);
        int x = side == 0 ? pad : side + 16, w = width - x - pad;
        if (side > 0) {
            g.fill(0, 40, side, height, PANEL);
            int step = Math.max(18, Math.min(29, (height - 60) / (CATEGORIES.size() + 4))), rowH = step - (step >= 24 ? 5 : 1);
            for (int i = 0; i < CATEGORIES.size(); i++) {
                String category = CATEGORIES.get(i);
                button(g, "category:" + category, category, new Rect(8, 46 + i * step, side - 16, rowH), () -> category(category), true, mouseX, mouseY, !modsView && category.equals(currentCategory));
            }
            button(g, "installed-mods", "Installed mods", new Rect(8, height - 6 - 4 * step, side - 16, rowH), this::openMods, true, mouseX, mouseY, modsView);
            button(g, "hud", "Edit HUD", new Rect(8, height - 6 - 3 * step, side - 16, rowH), () -> leave(onOpenHudEditor), true, mouseX, mouseY, false);
            button(g, "packs", "Resource packs", new Rect(8, height - 6 - 2 * step, side - 16, rowH), () -> leave(onOpenResourcePacks), true, mouseX, mouseY, false);
            button(g, "video", "Video settings", new Rect(8, height - 6 - step, side - 16, rowH), () -> leave(onOpenVideoSettings), true, mouseX, mouseY, false);
        }
        if (modsView) renderMods(g, x, w, mouseX, mouseY);
        else if (detail == null) renderCatalog(g, x, w, side == 0, mouseX, mouseY);
        else renderDetails(g, x, w, mouseX, mouseY);
        g.drawText(fit(g, notice.isEmpty() ? "Ctrl+F search / Tab navigate / Esc back" : notice, w), x, height - 15, MUTED);
        // The icons' credit (Flaticon licence), right of the hint while no notice is showing and there is room for both.
        if (notice.isEmpty() && g.textWidth(ICON_CREDIT) + 190 <= w) g.drawText(ICON_CREDIT, x + w - g.textWidth(ICON_CREDIT), height - 15, MUTED);
        // Each popup is its own layer in front of everything before it (see LadsGraphics.overlay).
        g.overlay(() -> colorPicker.render(g, pointerX, pointerY));
        g.overlay(() -> actions.render(g, pointerX, pointerY));
        g.overlay(() -> particleRates.render(g, pointerX, pointerY));
        g.overlay(() -> clientImport.render(g, pointerX, pointerY));
        g.overlay(() -> networkHub.render(g, pointerX, pointerY));
        g.overlay(() -> renderHudFpsDialog(g, pointerX, pointerY));
        g.overlay(() -> library.render(g, pointerX, pointerY));
        g.overlay(() -> confirm.render(g, pointerX, pointerY));
        String tipKey = modal ? null : tipOption != null ? ("option:" + (detail != null ? detail.getName() : "") + ":" + tipOption.getName()) : tipModule != null ? tipModule.getName() : tipRow != null ? tipRow.title() : null;
        if (tip.update(tipKey, now)) {
            if (tipOption != null && tipOptionRule != null) {
                String serverName = ServerPolicy.active() != null ? ServerPolicy.active().serverName() : "";
                String text = LadsApiUi.formatOptionTooltip(serverName, tipOptionRule, tipOption);
                g.overlay(() -> tooltip(g, text, pointerX, pointerY));
            } else if (tipModule != null) {
                ServerPolicy policy = ServerPolicy.active();
                ModuleState serverState = policy != null ? policy.module(tipModule.getName()) : ModuleState.INHERIT;
                String serverName = (policy != null && !policy.serverName().isEmpty()) ? policy.serverName() : "server";
                boolean refuses = policy != null && policy.refusesChanges();

                String desc = tipModule.getDescription();
                if (serverState == ModuleState.BLOCK) {
                    desc += " [Blocked by " + serverName + "]";
                } else if (serverState == ModuleState.ON && !refuses) {
                    desc += " [Turned on by " + serverName + "]";
                } else if (tipModule.isBlockedByServer()) {
                    desc += com.thelads.core.client.HypixelSafetyManager.blockOf(tipModule.getName()) == com.thelads.core.client.HypixelSafetyManager.Block.UNKNOWN
                        ? " [Off: this server's rules are unknown - open Settings to bypass at your own risk]" : " [Off: this server's rules ban it - Server Rules]";
                } else if (com.thelads.core.client.HypixelSafetyManager.isBypassed(tipModule.getName())
                        && com.thelads.core.client.HypixelSafetyManager.blockOf(tipModule.getName()) != com.thelads.core.client.HypixelSafetyManager.Block.NONE) {
                    desc += " [Bypassed on this server - at your own risk]";
                } else if (com.thelads.core.client.hypixel.HypixelMods.HYPIXEL_MODS.equals(tipModule.getName())
                        && !com.thelads.core.client.HypixelSafetyManager.isHypixel()) {
                    desc += " [Inactive - Only activates on Hypixel]";
                }
                String text = desc;
                g.overlay(() -> tooltip(g, text, pointerX, pointerY));
            } else if (tipRow != null) {
                String text = tipRow.members().stream().map(Module::getName).collect(java.util.stream.Collectors.joining(", ")) + " in one row. Settings has a tab for each.";
                g.overlay(() -> tooltip(g, text, pointerX, pointerY));
            }
        }
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
            // Two rows of six, with short labels.
            int perRow = CATEGORIES.size() / 2, cell = (w - (perRow - 1) * 4) / perRow, cellH = dense ? 16 : 18;
            for (int i = 0; i < CATEGORIES.size(); i++) {
                String cat = CATEGORIES.get(i);
                button(g, "category:" + cat, ModuleGroups.shortLabel(cat), new Rect(x + (i % perRow) * (cell + 4), top + (i / perRow) * (cellH + 2), cell, cellH), () -> category(cat), true, mx, my, cat.equals(currentCategory));
            }
            top += 2 * (cellH + 2) + (dense ? 2 : 4);
        }
        int filterW = w >= 360 ? 78 : 54;
        Rect search = new Rect(x, top, w - filterW * 2 - 12, 24);
        button(g, "search", editingSearch ? inputDisplay() : searchQuery.isEmpty() ? "Search modules and settings..." : searchQuery, search, this::startSearch, true, mx, my, editingSearch);
        button(g, "enabled", "Enabled", new Rect(search.x + search.width + 6, top, filterW, 24), () -> { enabledOnly = !enabledOnly; invalidate(); }, true, mx, my, enabledOnly);
        button(g, "favorites", "Favorites", new Rect(x + w - filterW, top, filterW, 24), () -> { favoritesOnly = !favoritesOnly; invalidate(); }, true, mx, my, favoritesOnly);
        top += dense ? 27 : 33;
        List<Module> modules = getFilteredModules();
        // Reachable in every layout: the side bar's buttons are gone in the compact one, and at the narrowest the short label takes the count's place.
        int importX = x + g.textWidth(modules.size() + " MODULES") + 8;
        int actionX = importX;
        if (importX + 110 < x + w - (compact ? 223 : 164)) {
            g.drawText(modules.size() + " MODULES", x, top, MUTED);
            button(g, "client-import", "Import from client", new Rect(importX, top - 4, 110, 18), clientImport::open, true, mx, my, false);
            actionX += 116;
        } else {
            button(g, "client-import", "Import", new Rect(x, top - 4, 48, 18), clientImport::open, true, mx, my, false);
            actionX = x + 54;
        }
        int rightLimit = x + w - (compact ? 223 : (currentCategory.equals("HUD") && !modsView ? (w >= 400 ? 278 : (w >= 280 ? 164 : 4)) : 4));
        if (com.thelads.core.client.LadsNetwork.titleButton() && actionX + 86 <= rightLimit) {
            button(g, "lads-network", "Lads Network", new Rect(actionX, top - 4, 86, 18), this::openNetworkHub, true, mx, my, networkHub.isOpen());
        }
        if (compact) {
            button(g, "installed-mods", "Installed mods", new Rect(x + w - 219, top - 4, 84, 18), this::openMods, true, mx, my, false);
            button(g, "hud", "HUD", new Rect(x + w - 131, top - 4, 39, 18), () -> leave(onOpenHudEditor), true, mx, my, false);
            button(g, "packs", "Packs", new Rect(x + w - 87, top - 4, 39, 18), () -> leave(onOpenResourcePacks), true, mx, my, false);
            button(g, "video", "Video", new Rect(x + w - 43, top - 4, 43, 18), () -> leave(onOpenVideoSettings), true, mx, my, false);
        }
        if (currentCategory.equals("HUD") && !modsView && !compact && w >= 280) { // compact: the header already has HUD FPS, and this row has the shortcuts
            String fpsStatus = "HUD FPS: " + (HudSettings.getInstance().isHudFpsCapEnabled() ? HudSettings.formatFpsLimit(HudSettings.getInstance().getHudFpsLimit()) : "Off");
            button(g, "cat-hud-fps", fpsStatus, new Rect(x + w - 160, top - 4, 158, 18), this::openHudFpsDialog, true, mx, my, HudSettings.getInstance().isHudFpsCapEnabled());
            if (w >= 400) { // the Lads HUD's own GUI scale (HudScale): Auto follows vanilla's; also in the HUD editor
                int chosen = HudSettings.getInstance().getHudScale(), vanilla = g.guiScale();
                String scale = "HUD scale: " + (chosen == 0 ? "Auto" : String.valueOf(com.thelads.core.client.hud.HudScale.of(g)));
                button(g, "cat-hud-scale", scale, new Rect(x + w - 274, top - 4, 110, 18), () -> {
                    int next = HudSettings.getInstance().getHudScale() + 1;
                    HudSettings.getInstance().setHudScale(next > com.thelads.core.client.hud.HudScale.max(width * vanilla, height * vanilla) ? 0 : next);
                    ConfigManager.save();
                }, vanilla > 0, mx, my, chosen != 0);
            }
        }
        top += dense ? 17 : 22;
        viewport = new Rect(x, top, w, Math.max(20, height - top - 28));
        List<Line> lines = catalogLines(modules);
        int cols = w >= 640 ? 3 : w >= 300 ? 2 : 1, gap = 4;
        // Rows: state pip, name, star and gear; the description is the hover tooltip and the row itself toggles.
        int rowH = dense ? 20 : 22, foldH = dense ? 18 : 20, rowW = (w - (cols - 1) * gap - 6) / cols;
        int y = top - renderScroll, col = 0;
        g.enableScissor(x - 2, top - 2, x + w, top + viewport.height + 2); // 2 px for the hover halo
        for (Line line : lines) {
            if (line instanceof Row row) {
                if (y + rowH > top && y < top + viewport.height) {
                    if (row.members().size() > 1) bundleRow(g, row, x + col * (rowW + gap), y, rowW, rowH, mx, my);
                    else moduleRow(g, row.members().get(0), x + col * (rowW + gap), y, rowW, rowH, mx, my);
                }
                if (++col == cols) { col = 0; y += rowH + gap; }
                continue;
            }
            if (col > 0) { col = 0; y += rowH + gap; }
            int lineH = line instanceof Fold ? foldH : line instanceof Hit ? rowH : 13;
            if (y + lineH > top && y < top + viewport.height) {
                if (line instanceof Fold fold) foldRow(g, fold, x, y, w - 6, lineH, mx, my);
                else if (line instanceof Hit hit) hitRow(g, hit, x, y, w - 6, lineH, mx, my);
                else g.drawText(fit(g, line instanceof Heading heading ? heading.text() : ((Hint)line).text(), w - 6), x, y + 3, MUTED);
            }
            y += lineH + gap;
        }
        if (col > 0) y += rowH + gap;
        maxScroll = Math.max(0, y + renderScroll - gap - top - viewport.height);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        if (lines.isEmpty()) {
            g.drawText("No matching modules", x + 12, top + 18, TEXT);
            g.drawText("Try another search or filter.", x + 12, top + 34, MUTED);
        }
        g.disableScissor(); scrollbar(g);
    }
    /** What the page lists: filtering shows flat rows and the settings it found; otherwise Home, or the category's fold-out groups. */
    private List<Line> catalogLines(List<Module> modules) {
        List<Line> lines = new ArrayList<>();
        if (!searchQuery.isEmpty() || enabledOnly || favoritesOnly) {
            lines.addAll(rows(modules, true));
            if (!optionHits.isEmpty()) { lines.add(new Heading("SETTINGS")); lines.addAll(optionHits); }
        } else if (currentCategory.equals(ModuleGroups.HOME)) {
            homeLines(lines, modules);
        } else if (currentCategory.equals(ModuleGroups.ALL)) {
            for (String category : CATEGORIES) fold(lines, "all/" + category, category, modules.stream().filter(m -> categoryOf(m).equals(category)).toList(), false);
        } else {
            List<ModuleGroups.Group> groups = ModuleGroups.groupsIn(currentCategory);
            List<Module> other = modules.stream().filter(m -> !groups.contains(ModuleGroups.groupOf(m.getName()))).toList();
            long used = groups.stream().filter(group -> modules.stream().anyMatch(m -> group.members().contains(m.getName()))).count() + (other.isEmpty() ? 0 : 1);
            if (used <= 1) lines.addAll(rows(modules, true));
            else {
                // A category short enough to read at a glance starts open.
                boolean open = modules.size() <= 14;
                for (ModuleGroups.Group group : groups) fold(lines, group.key(), group.title(), modules.stream().filter(m -> group.members().contains(m.getName())).toList(), open);
                fold(lines, currentCategory + "/" + ModuleGroups.OTHER, ModuleGroups.OTHER, other, open);
            }
        }
        return lines;
    }
    private void homeLines(List<Line> lines, List<Module> modules) {
        List<Module> favorites = modules.stream().filter(Module::isFavorite).toList();
        List<Module> recent = modules.stream().filter(m -> !m.isFavorite() && m.getLastOpenedTime() > 0)
            .sorted(Comparator.comparingLong(Module::getLastOpenedTime).reversed()).limit(RECENT_ROWS).toList();
        if (!favorites.isEmpty()) { lines.add(new Heading("FAVORITES")); lines.addAll(rows(favorites, true)); }
        if (!recent.isEmpty()) { lines.add(new Heading("RECENTLY USED")); lines.addAll(rows(recent, false)); }
        if (favorites.isEmpty() && recent.isEmpty()) {
            lines.add(new Hint("Star a module to keep it here. The ones you use most show up by themselves."));
            lines.add(new Heading("POPULAR"));
            lines.addAll(rows(ModuleGroups.POPULAR.stream().flatMap(name -> modules.stream().filter(m -> m.getName().equals(name))).toList(), false));
        }
        fold(lines, "home/on", "Turned on", modules.stream().filter(m -> ModuleSupport.isToggleable(m.getName()) && m.isEnabled()).toList(), false);
    }
    private void fold(List<Line> lines, String key, String title, List<Module> members, boolean byDefault) {
        if (members.isEmpty()) return;
        List<Module> switches = members.stream().filter(m -> ModuleSupport.isToggleable(m.getName())).toList();
        boolean open = ModuleGroups.isOpen(key, byDefault);
        lines.add(new Fold(key, title, open, (int)switches.stream().filter(Module::isEnabled).count(), switches.size()));
        if (open) lines.addAll(rows(members, true));
    }
    /** A row per module; a bundle's modules share one when everything this game version has of the bundle is listed. */
    private static List<Row> rows(List<Module> modules, boolean sorted) {
        List<Row> rows = new ArrayList<>();
        Set<ModuleGroups.Bundle> done = new HashSet<>();
        for (Module m : modules) {
            ModuleGroups.Bundle bundle = ModuleGroups.bundleOf(m.getName());
            List<Module> members = bundle == null ? List.of() : bundleMembers(bundle);
            if (members.size() > 1 && modules.containsAll(members)) {
                if (done.add(bundle)) rows.add(new Row(bundle.title(), members));
            } else rows.add(new Row(ModuleGroups.label(m.getName()), List.of(m)));
        }
        if (sorted) rows.sort(Comparator.comparing(Row::title, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }
    private static List<Module> bundleMembers(ModuleGroups.Bundle bundle) {
        return bundle.members().stream().map(ModuleManager.getInstance()::getModule)
            .filter(m -> m != null && ModuleSupport.isBuiltIn(m.getName()) && !ModuleManager.getInstance().isHiddenModule(m.getName())).toList();
    }
    /** The player can switch it here: no server rule holds it on or off. */
    private static boolean switchable(Module m) {
        ServerPolicy policy = ServerPolicy.active();
        ModuleState state = policy != null ? policy.module(m.getName()) : ModuleState.INHERIT;
        return state != ModuleState.BLOCK && !(state == ModuleState.ON && !policy.refusesChanges()) && !m.isBlockedByServer() && ModuleSupport.isToggleable(m.getName());
    }
    private void foldRow(LadsGraphics g, Fold fold, int x, int y, int w, int h, int mx, int my) {
        String id = "group:" + fold.key();
        boolean focused = focusId.equals(id);
        float hover = animate(id, new Rect(x, y, w, h).contains(mx, my) && viewport.contains(mx, my) || focused);
        if (focused) round(g, x - 1, y - 1, w + 2, h + 2, TEXT);
        round(g, x, y, w, h, mix(PANEL, LadsPalette.HOVER, hover));
        int textY = y + (h - g.fontHeight()) / 2 + 1;
        com.thelads.core.client.title.TitleScreenTheme.icon(g, fold.open() ? "angle-small-down" : "angle-right", x + 5, y + (h - 9) / 2, 9, MUTED);
        String count = fold.total() == 0 ? "" : fold.on() == fold.total() ? fold.on() + " on" : fold.on() + " of " + fold.total() + " on";
        g.drawText(count, x + w - 8 - g.textWidth(count), textY, fold.on() > 0 ? TEXT : MUTED);
        g.drawText(fit(g, fold.title(), w - 34 - g.textWidth(count)), x + 18, textY, TEXT);
        controls.add(new Control(id, fold.title() + (fold.open() ? ", open" : ", closed"), new Rect(x, y, w, h), () -> { ModuleGroups.toggle(fold.key()); dirty = true; }, true));
    }
    private void hitRow(LadsGraphics g, Hit hit, int x, int y, int w, int h, int mx, int my) {
        String id = "result:" + hit.module().getName() + "/" + hit.option().getName();
        String where = ModuleGroups.label(hit.module().getName());
        boolean focused = focusId.equals(id);
        float hover = animate(id, new Rect(x, y, w, h).contains(mx, my) && viewport.contains(mx, my) || focused);
        if (focused) round(g, x - 1, y - 1, w + 2, h + 2, TEXT);
        round(g, x, y, w, h, mix(CARD, LadsPalette.HOVER, hover));
        int textY = y + (h - g.fontHeight()) / 2 + 1, whereW = Math.min(g.textWidth(where), w / 2);
        g.drawText(fit(g, where, whereW), x + w - 8 - whereW, textY, MUTED);
        g.drawText(fit(g, hit.option().getName(), w - 24 - whereW), x + 8, textY, TEXT);
        controls.add(new Control(id, hit.option().getName() + " in " + where, new Rect(x, y, w, h), () -> { revealOption = hit.option(); openDetails(hit.module()); }, true));
    }
    /** The row's card: its state colour, a glow when hovered and an outline when focused. Returns the fill for what sits on it. */
    private int rowCard(LadsGraphics g, String id, int cx, int cy, int rowW, int rowH, boolean on, boolean live, boolean blocked, int mx, int my) {
        boolean hovered = new Rect(cx, cy, rowW, rowH).contains(mx, my) && viewport.contains(mx, my);
        float hover = animate(id, hovered);
        int base = blocked ? LadsPalette.CARD_OFF : !live ? CARD : on ? LadsPalette.CARD_ON : LadsPalette.CARD_OFF;
        int glow = blocked || !live ? LadsPalette.DISABLED : on ? LadsPalette.CARD_ON_GLOW : LadsPalette.CARD_OFF_GLOW, fill = mix(base, glow, .14f * hover);
        if (live && hover > 0) round(g, cx - 1, cy - 1, rowW + 2, rowH + 2, (int)(0x90 * hover) << 24 | glow & 0xFFFFFF);
        if (focusId.equals(id)) round(g, cx - 1, cy - 1, rowW + 2, rowH + 2, TEXT);
        round(g, cx, cy, rowW, rowH, mix(base, glow, .35f + .65f * hover));
        round(g, cx + 1, cy + 1, rowW - 2, rowH - 2, fill);
        return fill;
    }
    private void rowPip(LadsGraphics g, int cx, int cy, int rowH, boolean on, int fill) { // filled when on, so the state is not colour alone
        round(g, cx + 7, cy + (rowH - 7) / 2, 7, 7, TEXT);
        if (!on) round(g, cx + 8, cy + (rowH - 5) / 2, 5, 5, fill);
    }
    /** The star and gear at a row's right end. Returns where the name must stop. */
    private int rowChips(LadsGraphics g, String key, String label, int cx, int cy, int rowW, int rowH, boolean favorite, Runnable star, Runnable settings, int mx, int my) {
        int chipW = rowH - 2, chipH = rowH - 4, gearX = cx + rowW - chipW - 2, starX = gearX - chipW - 2;
        iconChip(g, "favorite:" + key, "Favorite " + label, "star", new Rect(starX, cy + 2, chipW, chipH), star, mx, my, favorite ? com.thelads.core.client.ServerListEntryUi.PIN_STAR_COLOR : MUTED);
        iconChip(g, "detail:" + key, "Settings for " + label, "settings", new Rect(gearX, cy + 2, chipW, chipH), settings, mx, my, TEXT);
        return starX - 4;
    }
    private void bundleRow(LadsGraphics g, Row row, int cx, int cy, int rowW, int rowH, int mx, int my) {
        String id = "card:" + row.title();
        List<Module> members = row.members(), switches = members.stream().filter(LadsSettingsScreen::switchable).toList();
        long on = members.stream().filter(Module::isEnabled).count();
        if (new Rect(cx, cy, rowW, rowH).contains(mx, my) && viewport.contains(mx, my)) tipRow = row;
        int fill = rowCard(g, id, cx, cy, rowW, rowH, on > 0, !switches.isEmpty(), switches.isEmpty(), mx, my);
        rowPip(g, cx, cy, rowH, on > 0, fill);
        boolean favorite = members.stream().allMatch(Module::isFavorite);
        int nameEnd = rowChips(g, row.title(), row.title(), cx, cy, rowW, rowH, favorite,
            () -> { for (Module m : members) { m.setFavorite(!favorite); changed(m); } }, () -> openDetails(members.get(0)), mx, my);
        String count = on + "/" + members.size();
        int textY = cy + (rowH - g.fontHeight()) / 2 + 1;
        g.drawText(count, nameEnd - g.textWidth(count), textY, MUTED);
        g.drawText(fit(g, row.title(), nameEnd - g.textWidth(count) - 4 - (cx + 20)), cx + 20, textY, TEXT);
        // Added after the star and gear so those win the click.
        controls.add(new Control(id, row.title() + ", " + on + " of " + members.size() + " on", new Rect(cx, cy, rowW, rowH), () -> {
            if (switches.isEmpty()) { openDetails(members.get(0)); return; }
            boolean anyOn = switches.stream().anyMatch(Module::isEnabled);
            for (Module m : switches) if (m.isEnabled() == anyOn) { m.toggle(); changed(m); }
            onNarrate.accept(row.title() + (anyOn ? ", Off" : ", On"));
        }, true));
    }
    private void moduleRow(LadsGraphics g, Module m, int cx, int cy, int cardW, int cardH, int mx, int my) {
        String id = "card:" + m.getName();
        ServerPolicy policy = ServerPolicy.active();
        ModuleState serverState = policy != null ? policy.module(m.getName()) : ModuleState.INHERIT;
        String serverName = (policy != null && !policy.serverName().isEmpty()) ? policy.serverName() : "server";
        boolean refuses = policy != null && policy.refusesChanges();

        boolean serverBlocked = serverState == ModuleState.BLOCK;
        boolean serverForcedOn = serverState == ModuleState.ON && !refuses;
        boolean blocked = serverBlocked || m.isBlockedByServer();
        boolean toggleable = !serverBlocked && !serverForcedOn && !blocked && ModuleSupport.isToggleable(m.getName());
        boolean on = serverForcedOn || (toggleable && m.isEnabled());
        if (new Rect(cx, cy, cardW, cardH).contains(mx, my) && viewport.contains(mx, my)) tipModule = m;
        int fill = rowCard(g, id, cx, cy, cardW, cardH, on, toggleable || serverForcedOn, blocked, mx, my);
        boolean settingsOnly = ModuleSupport.isSettingsOnly(m.getName());
        boolean unknownRules = blocked && !serverBlocked && com.thelads.core.client.HypixelSafetyManager.blockOf(m.getName()) == com.thelads.core.client.HypixelSafetyManager.Block.UNKNOWN;
        String soon = serverBlocked ? ("Blocked by " + serverName)
            : serverForcedOn ? ("Turned on by " + serverName)
            : blocked ? (unknownRules ? "Unknown Rules" : "Server Block")
            : (toggleable || settingsOnly ? "" : "Soon");
        if (toggleable) rowPip(g, cx, cy, cardH, on, fill);
        String title = ModuleGroups.label(m.getName());
        int nameEnd = rowChips(g, m.getName(), title, cx, cy, cardW, cardH, m.isFavorite(), () -> { m.setFavorite(!m.isFavorite()); changed(m); }, () -> openDetails(m), mx, my);
        int textY = cy + (cardH - g.fontHeight()) / 2 + 1, soonW = Math.min(g.textWidth(soon), (nameEnd - cx) / 2);
        if (!toggleable) g.drawText(fit(g, soon, soonW), nameEnd - soonW, textY, blocked ? ACCENT : serverForcedOn ? 0xFF5DF08A : MUTED);
        g.drawText(fit(g, title, nameEnd - (cx + 20) - (toggleable ? 0 : soonW + 4)), cx + 20, textY, toggleable || settingsOnly || serverForcedOn ? TEXT : MUTED);
        // Added after the star and gear so those win the click.
        String accessibilityLabel = m.getName() + (serverBlocked ? (", Blocked by " + serverName) : serverForcedOn ? (", Turned on by " + serverName) : blocked ? (unknownRules ? ", Off, unknown server rules" : ", Blocked on this server") : (on ? ", On" : toggleable ? ", Off" : settingsOnly ? ", Settings" : ", Soon"));
        controls.add(new Control(id, accessibilityLabel, new Rect(cx, cy, cardW, cardH), () -> {
            if (serverBlocked) {
                notice = m.getName() + " is blocked by " + serverName + ".";
                onNarrate.accept(notice);
                openDetails(m);
            } else if (serverForcedOn) {
                notice = m.getName() + " is turned on by " + serverName + ".";
                onNarrate.accept(notice);
                openDetails(m);
            } else if (m.isBlockedByServer()) { // its details hold the notice and the Bypass button
                notice = com.thelads.core.client.HypixelSafetyManager.getBlockedNotice(m.getName());
                onNarrate.accept(notice);
                openDetails(m);
            }
            else if (ModuleSupport.isToggleable(m.getName())) { m.toggle(); changed(m); onNarrate.accept(m.getName() + (m.isEnabled() ? ", On" : ", Off")); }
            else if (ModuleSupport.isSettingsOnly(m.getName())) openDetails(m);
        }, true));
    }
    /** The big red at-your-own-risk warning; confirming keeps the module on for this server only. */
    private void openBypassWarning(Module m) {
        String host = com.thelads.core.client.HypixelSafetyManager.currentHost();
        boolean unknown = com.thelads.core.client.HypixelSafetyManager.blockOf(m.getName()) == com.thelads.core.client.HypixelSafetyManager.Block.UNKNOWN;
        String why = unknown
            ? "We don't know the rules of " + host + ", so " + m.getName() + " is off here to keep you safe."
            : host + " bans " + m.getName() + " in its rules.";
        confirm.openDanger("USE AT YOUR OWN RISK", why + " Turning it on can get your account BANNED by the server's anticheat or staff,"
            + " and The Lads Client cannot undo a ban. It stays on for " + host + " only, until you undo the bypass.",
            "Enable anyway", () -> {
                com.thelads.core.client.HypixelSafetyManager.bypass(m.getName());
                if (!m.isEnabled()) m.setEnabled(true);
                changed(m);
                notice = m.getName() + " bypassed on " + host + " (at your own risk)";
                onNarrate.accept(notice);
            });
        onNarrate.accept("Warning: use at your own risk. " + why);
    }

    private void renderDetails(LadsGraphics g, int x, int w, int mx, int my) {
        boolean wide = w >= 360;
        boolean preview = hasPreview(detail);
        int leftW = wide ? Math.min(270, (w - 14) / 2) : w;
        int previewX = x + leftW + 12;
        int previewW = w - leftW - 12;
        boolean showPreview = wide && preview && previewW >= 80;

        int headerW = showPreview ? leftW : (w - 8);
        button(g, "back", "Modules", new Rect(x, 43, 86, 22), this::back, true, mx, my, false);
        ModuleGroups.Bundle bundle = ModuleGroups.bundleOf(detail.getName());
        List<Module> tabs = bundle == null ? List.of() : bundleMembers(bundle);
        g.drawText(fit(g, tabs.size() > 1 ? bundle.title() : ModuleGroups.label(detail.getName()), headerW - 102), x + 96, 51, TEXT);
        int descriptionH = height < 230 ? 0 : MenuGraphics.wrap(g, detail.getDescription(), x, 76, headerW, 2, MUTED);
        int stateY = height < 230 ? 68 : 81 + descriptionH;
        if (tabs.size() > 1) {
            int tabW = Math.min(110, (headerW - (tabs.size() - 1) * 4) / tabs.size());
            for (int i = 0; i < tabs.size(); i++) {
                Module tab = tabs.get(i);
                button(g, "tab:" + tab.getName(), tab.getName(), new Rect(x + i * (tabW + 4), stateY, tabW, 20), () -> { if (finish()) openDetails(tab); }, true, mx, my, tab == detail);
            }
            stateY += 26;
        }
        boolean hypixelInactive = com.thelads.core.client.hypixel.HypixelMods.HYPIXEL_MODS.equals(detail.getName())
            && !com.thelads.core.client.HypixelSafetyManager.isHypixel();
        g.drawText(hypixelInactive ? "LADS MODULE (INACTIVE OFF HYPIXEL)" : ModuleSupport.isSettingsOnly(detail.getName()) ? "SETTINGS" : "LADS MODULE", x, stateY + 6, ACCENT);
        ServerPolicy policy = ServerPolicy.active();
        ModuleState serverState = policy != null ? policy.module(detail.getName()) : ModuleState.INHERIT;
        String serverName = (policy != null && !policy.serverName().isEmpty()) ? policy.serverName() : "server";
        boolean refuses = policy != null && policy.refusesChanges();
        boolean serverBlocked = serverState == ModuleState.BLOCK;
        boolean serverForcedOn = serverState == ModuleState.ON && !refuses;

        boolean bypassed = !serverBlocked && !detail.isBlockedByServer() && com.thelads.core.client.HypixelSafetyManager.isBypassed(detail.getName())
            && com.thelads.core.client.HypixelSafetyManager.blockOf(detail.getName()) != com.thelads.core.client.HypixelSafetyManager.Block.NONE;
        if (serverBlocked) {
            button(g, "toggle:detail", "OFF", new Rect(x + headerW - 52, stateY, 52, 22), () -> {}, false, mx, my, false);
            g.drawText(fit(g, "Blocked by " + serverName, headerW), x, stateY + 28, ACCENT);
        } else if (serverForcedOn) {
            button(g, "toggle:detail", "ON", new Rect(x + headerW - 52, stateY, 52, 22), () -> {}, false, mx, my, true);
            g.drawText(fit(g, "Turned on by " + serverName, headerW), x, stateY + 28, 0xFF5DF08A);
        } else if (detail.isBlockedByServer()) {
            Module blockedModule = detail;
            button(g, "toggle:detail", "Bypass...", new Rect(x + headerW - 80, stateY, 80, 22), () -> openBypassWarning(blockedModule), true, mx, my, false);
            g.drawText(fit(g, com.thelads.core.client.HypixelSafetyManager.blockOf(detail.getName()) == com.thelads.core.client.HypixelSafetyManager.Block.UNKNOWN
                ? "Off: unknown server rules (anticheat ban risk)" : "Off: this server's rules ban it", headerW), x, stateY + 28, ACCENT);
        } else if (!ModuleSupport.isSettingsOnly(detail.getName()))
            button(g, "toggle:detail", detail.isEnabled() ? "ON" : "OFF", new Rect(x + headerW - 52, stateY, 52, 22),
                () -> { detail.toggle(); changed(detail); }, true, mx, my, detail.isEnabled());
        if (bypassed) {
            Module bypassedModule = detail;
            g.drawText(fit(g, "BYPASSED on this server - at your own risk", headerW - 90), x, stateY + 28, 0xFFFF3B3B);
            button(g, "bypass:undo", "Undo bypass", new Rect(x + headerW - 84, stateY + 24, 84, 18), () -> {
                com.thelads.core.client.HypixelSafetyManager.removeBypass(bypassedModule.getName());
                notice = bypassedModule.getName() + " follows this server's rules again";
                onNarrate.accept(notice);
            }, true, mx, my, false);
        }
        int top = (detail.isBlockedByServer() || bypassed || serverBlocked || serverForcedOn) ? stateY + 46 : stateY + 30;
        if(detail.getOptions().stream().anyMatch(o -> o instanceof PlayerActionOption)) {
            button(g,"display-actions","Display actions...",new Rect(x,top,showPreview ? leftW : Math.min(270, w - 8),25),
                () -> actions.open(detail.getOptions().stream().filter(o -> o instanceof PlayerActionOption).map(o -> (PlayerActionOption)o).toList(), () -> changed(detail)),true,mx,my,false);
            top += 32;
        }

        int contentW = showPreview ? leftW : w;
        viewport = new Rect(x, top, contentW, Math.max(20, height - top - 28));
        List<Option> options = activeOptions();
        g.enableScissor(x, top, x + contentW, top + viewport.height);

        int cols = showPreview ? 1 : (wide ? 2 : 1);
        int gap = 8;
        int colW = cols == 2 ? (w - gap - 8) / 2 : contentW - 8;

        // The Kill Banner picker scrolls with the options, above them (only when Kill Banner has preview).
        int picker = (showPreview && detail instanceof KillBannerModule banner)
            ? killBannerPicker(g, banner, x, top - renderScroll, colW, mx, my) : 0;

        int rowCount = (options.size() + cols - 1) / cols;
        int rows = 0;
        for (int r = 0; r < rowCount; r++) {
            int i0 = r * cols;
            int rh = rowHeight(options.get(i0));
            if (cols == 2 && i0 + 1 < options.size()) rh = Math.max(rh, rowHeight(options.get(i0 + 1)));
            rows += rh;
        }

        maxScroll = Math.max(0, picker + rows + 32 - viewport.height);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        if (revealOption != null) {
            int above = picker;
            for (int r = 0; r < rowCount && !options.subList(r * cols, Math.min(options.size(), (r + 1) * cols)).contains(revealOption); r++) {
                int rh = rowHeight(options.get(r * cols));
                if (cols == 2 && r * cols + 1 < options.size()) rh = Math.max(rh, rowHeight(options.get(r * cols + 1)));
                above += rh;
            }
            scrollOffset = Math.min(above, maxScroll); displayedScroll = scrollOffset; renderScroll = scrollOffset;
            focusId = "option:" + revealOption.getName();
            revealOption = null;
        }
        int resetY = top + picker - renderScroll;

        for (int r = 0; r < rowCount; r++) {
            int i0 = r * cols;
            Option opt0 = options.get(i0);
            Option opt1 = cols == 2 && i0 + 1 < options.size() ? options.get(i0 + 1) : null;
            int rh = rowHeight(opt0);
            if (opt1 != null) rh = Math.max(rh, rowHeight(opt1));

            if (resetY + rh > top && resetY < top + viewport.height) {
                optionRow(g, opt0, x, resetY, colW, mx, my);
                if (opt1 != null) {
                    optionRow(g, opt1, x + colW + gap, resetY, colW, mx, my);
                }
            }
            resetY += rh;
        }

        if (resetY < top + viewport.height && resetY + 24 > top)
            button(g, "reset", "Reset options", new Rect(x, resetY + 4, Math.min(130, colW), 24),
                () -> {
                    Module module = detail;
                    confirm.open("Reset every " + module.getName() + " option to its default?", () -> { module.getOptions().forEach(Option::reset); changed(module); notice = "Options reset"; });
                }, true, mx, my, false);
        g.disableScissor(); scrollbar(g);

        if (showPreview) {
            renderModulePreview(g, detail, previewX, 43, previewW, height - 68, mx, my);
        }
    }
    private static final String[] RANDOM_HINTS = {"Every kill shows the banner above.", "Each kill: another variant of the skin.",
        "Each kill: a random skin and variant.", "Each kill: one of the banners ticked below.",
        "A random skin per life or streak.", "A random chosen banner per life or streak."};
    /** Kill Banner settings as pictures: skin tiles with their one-kill art, their variants, Custom, Randomize and triggers. Returns its height. */
    /** The Kill Banner picker's open sections; the skins by default, the customising closed (kept for the session). */
    private static final java.util.Set<String> kbOpen = new java.util.HashSet<>(java.util.List.of("skins"));
    /** QA: the Kill Banner picker with only these sections open ("skins", "custombanner", "customsound", "randomize", "kinds"). */
    public static void openKillBannerSections(String... keys) { kbOpen.clear(); kbOpen.addAll(java.util.List.of(keys)); }

    /** Kill Banner settings as pictures: skin tiles with their one-kill art, Custom, Randomize and triggers, each section a
     * dropdown. The chosen skin's variants are under the preview on the right. Returns its height. */
    private int killBannerPicker(LadsGraphics g, KillBannerModule banner, int x, int y, int w, int mx, int my) {
        int start = y, gap = 4;
        int style = banner.bannerStyle.getIndex();
        KillBannerModule.Pick custom = banner.chosen();
        boolean styleLocked = isServerLocked(banner.bannerStyle);
        boolean visualLocked = isServerLocked(banner.customVisual);
        boolean soundLocked = isServerLocked(banner.customSound);
        boolean randomLocked = isServerLocked(banner.randomize);

        // Four tiles a row
        int perRow = 4;
        int tileW = (w - (perRow - 1) * gap) / perRow;
        int tileH = Math.max(40, tileW * 3 / 4 + 12);

        String q = kbSearch.trim().toLowerCase(Locale.ROOT);
        List<KillBannerStyle> visible = new ArrayList<>();
        for (KillBannerStyle s : KillBannerStyle.shown()) {
            if (q.isEmpty() || s.displayName.toLowerCase(Locale.ROOT).contains(q) || s.id.toLowerCase(Locale.ROOT).contains(q)) {
                visible.add(s);
            }
        }

        y = kbSection(g, "skins", "SKINS (" + KillBannerStyle.shown().size() + ")", x, y, w, mx, my);
        if (kbOpen.contains("skins")) {
            // Search bar for filtering the 91 skins
            String searchLabel = editingKbSearch ? inputDisplay() : kbSearch.isEmpty() ? "Search " + KillBannerStyle.shown().size() + " skins..." : kbSearch;
            button(g, "kb:search", searchLabel, new Rect(x, y, w, 20), this::startKbSearch, true, mx, my, editingKbSearch);
            y += 24;
            // Custom Tile + Skin Tiles
            boolean showCustom = q.isEmpty() || "custom".contains(q);
            int tileIdx = 0;
            if (showCustom) {
                int col = tileIdx % perRow, row = tileIdx / perRow;
                Rect r = new Rect(x + col * (tileW + gap), y + row * (tileH + gap), tileW, tileH);
                String cSkin = custom.style() == null ? "default" : custom.style().id;
                tile(g, "kb:skin:custom", "Custom", r, cSkin, custom.variant(), style == KillBannerModule.CUSTOM, mx, my, () -> {
                    if (!styleLocked) {
                        banner.bannerStyle.setIndex(KillBannerModule.CUSTOM);
                        kbOpen.add("custombanner"); // the choices Custom needs open with it
                        kbOpen.add("customsound");
                        changed(detail);
                    }
                }, !styleLocked);
                tileIdx++;
            }
            for (KillBannerStyle skin : visible) {
                int col = tileIdx % perRow, row = tileIdx / perRow;
                Rect r = new Rect(x + col * (tileW + gap), y + row * (tileH + gap), tileW, tileH);
                int sIndex = KillBannerModule.styleIndexOf(skin);
                boolean isSelected = style == sIndex;
                int v = banner.getVariant(skin);
                tile(g, "kb:skin:" + skin.id, skin.displayName, r, skin.id, v, isSelected, mx, my, () -> {
                    if (!styleLocked) {
                        banner.bannerStyle.setIndex(sIndex);
                        changed(detail);
                        g.getGame().previewKillBannerSound(skin.id, (float) banner.volume.getValue());
                    }
                }, !styleLocked);
                tileIdx++;
            }
            y += ((tileIdx + perRow - 1) / perRow) * (tileH + gap) + 8;
        }

        if (style == KillBannerModule.CUSTOM) {
            y = kbSection(g, "custombanner", "CUSTOM BANNER", x, y, w, mx, my);
            if (kbOpen.contains("custombanner")) {
                int vIdx = 0;
                for (KillBannerStyle skin : visible) {
                    int col = vIdx % perRow, row = vIdx / perRow;
                    Rect r = new Rect(x + col * (tileW + gap), y + row * (tileH + gap), tileW, tileH);
                    int kIdx = skin.ordinal();
                    boolean selected = banner.customVisual.getIndex() == kIdx;
                    int v = banner.getVariant(skin);
                    tile(g, "kb:visual:" + skin.id, skin.displayName, r, skin.id, v, selected, mx, my, () -> {
                        if (!visualLocked) {
                            banner.customVisual.setIndex(kIdx);
                            changed(detail);
                        }
                    }, !visualLocked);
                    vIdx++;
                }
                y += ((vIdx + perRow - 1) / perRow) * (tileH + gap) + 8;
            }
            y = kbSection(g, "customsound", "CUSTOM SOUND (CLICK TO HEAR)", x, y, w, mx, my);
            if (kbOpen.contains("customsound")) {
                // Your own files, one per kill of a streak; picking a skin's sound below turns them off.
                button(g, "kb:sound:own", "Your sounds (upload MP3 or WAV)", new Rect(x, y, w, 20),
                    () -> { banner.ownSounds.set(true); changed(detail); }, true, mx, my, banner.ownSounds.get());
                y += 24;
                if (banner.ownSounds.get()) {
                    for (SoundOption slot : banner.killSounds) { soundRow(g, slot, x, y, w, mx, my); y += 43; }
                    y += 4;
                }
                int sIdx = 0;
                for (KillBannerStyle skin : visible) {
                    int col = sIdx % perRow, row = sIdx / perRow;
                    Rect r = new Rect(x + col * (tileW + gap), y + row * (tileH + gap), tileW, tileH);
                    int kIdx = skin.ordinal();
                    boolean selected = banner.customSound.getIndex() == kIdx && !banner.ownSounds.get();
                    int v = banner.getVariant(skin);
                    tile(g, "kb:sound:" + skin.id, skin.displayName, r, skin.id, v, selected, mx, my, () -> {
                        if (!soundLocked) {
                            banner.customSound.setIndex(kIdx);
                            banner.ownSounds.set(false);
                            changed(detail);
                            g.getGame().previewKillBannerSound(skin.id, (float) banner.volume.getValue());
                        }
                    }, !soundLocked);
                    sIdx++;
                }
                y += ((sIdx + perRow - 1) / perRow) * (tileH + gap) + 8;
            }
        }

        y = kbSection(g, "randomize", "RANDOMIZE", x, y, w, mx, my);
        if (kbOpen.contains("randomize")) {
            String[] modes = {"Off", "Variant", "Skin + variant", "Chosen", "Per life", "Per life (Chosen)"};
            int rPerRow = w >= 300 ? 3 : 2, cell = (w - (rPerRow - 1) * gap) / rPerRow;
            for (int i = 0; i < modes.length; i++) {
                int index = i;
                button(g, "kb:random:" + i, modes[i], new Rect(x + i % rPerRow * (cell + gap), y + i / rPerRow * 24, cell, 20),
                    () -> { if (!randomLocked) { banner.randomize.setIndex(index); changed(detail); } }, !randomLocked, mx, my, banner.randomize.getIndex() == i);
            }
            y += ((modes.length + rPerRow - 1) / rPerRow) * 24 + 2;
            g.drawText(fit(g, RANDOM_HINTS[Math.min(RANDOM_HINTS.length - 1, banner.randomize.getIndex())], w), x, y, MUTED);
            y += 14;
            if (banner.randomize.getIndex() == KillBannerModule.RANDOM_CHOSEN || banner.randomize.getIndex() == KillBannerModule.RANDOM_PER_LIFE_CHOSEN) {
                var pool = banner.pool();
                for (KillBannerStyle skin : visible) {
                    String skinName = skin.displayName;
                    for (int v = 0; v < skin.variantNames.length; v++) {
                        int variant = v;
                        Rect r = new Rect(x + v * (tileW + gap), y, tileW, tileH);
                        boolean on = pool.contains(skin.id + ":" + v);
                        tile(g, "kb:pool:" + skin.id + ":" + v, v == 0 ? skinName : skin.variantNames[v], r, skin.id, v, on, mx, my,
                            () -> { banner.togglePool(skin, variant); changed(detail); }, true);
                        if (r.y + r.height > viewport.y && r.y < viewport.y + viewport.height) checkmark(g, r.x + r.width - 13, r.y + 3, on);
                    }
                    y += tileH + gap;
                }
                y += 4;
            }
        }

        y = kbSection(g, "kinds", "SHOW A BANNER FOR", x, y, w, mx, my);
        if (kbOpen.contains("kinds")) {
            BoolOption[] kinds = {banner.players, banner.mobs, banner.bosses};
            int checkW = (w - 2 * gap) / 3;
            for (int i = 0; i < kinds.length; i++) {
                BoolOption kind = kinds[i];
                Rect r = new Rect(x + i * (checkW + gap), y, checkW, 22);
                button(g, "kb:kind:" + kind.getName(), "    " + kind.getName(), r, () -> { kind.toggle(); changed(detail); }, true, mx, my, false);
                if (r.y + r.height > viewport.y && r.y < viewport.y + viewport.height) checkmark(g, r.x + 7, r.y + 6, kind.get());
            }
            y += 22;
        }
        return y + 10 - start;
    }

    /** A picker section's header, a dropdown: click to open or close it. Returns the y below it. */
    private int kbSection(LadsGraphics g, String key, String label, int x, int y, int w, int mx, int my) {
        boolean open = kbOpen.contains(key);
        Rect r = new Rect(x, y, w, 18);
        if (r.y + r.height > viewport.y && r.y < viewport.y + viewport.height) {
            String id = "kb:sec:" + key;
            boolean hover = r.contains(mx, my) && viewport.contains(mx, my);
            float progress = animate(id, hover || focusId.equals(id));
            round(g, r.x, r.y, r.width, r.height, mix(PANEL, LadsPalette.HOVER, progress));
            if (focusId.equals(id)) round(g, r.x, r.y, r.width, r.height, LadsPalette.PRIMARY_HOVER);
            g.drawText((open ? "[-] " : "[+] ") + label, r.x + 6, r.y + 5, ACCENT);
            controls.add(new Control(id, label, r, () -> { if (!kbOpen.remove(key)) kbOpen.add(key); }, true));
        }
        return y + 22;
    }

    /** The chosen skin's variants as tiles under the preview (not in the scrolling list). Returns the y below them. */
    private int variantTiles(LadsGraphics g, KillBannerModule banner, KillBannerStyle skin, int x, int y, int w, int mx, int my) {
        int gap = 4, perRow = Math.min(4, skin.variantNames.length);
        int tileW = (w - (perRow - 1) * gap) / perRow, tileH = Math.max(36, Math.min(60, tileW * 3 / 4 + 12));
        g.drawText(fit(g, skin.displayName + " VARIANT", w), x, y, ACCENT);
        y += 13;
        int curVar = banner.getVariant(skin);
        for (int v = 0; v < skin.variantNames.length; v++) {
            int variant = v;
            tile(g, "kbv:variant:" + skin.id + ":" + v, skin.variantNames[v], new Rect(x + v % perRow * (tileW + gap), y + v / perRow * (tileH + gap), tileW, tileH), skin.id, v,
                curVar == v, mx, my, () -> { banner.setVariant(skin, variant); changed(detail); }, false);
        }
        return y + ((skin.variantNames.length + perRow - 1) / perRow) * (tileH + gap);
    }
    private static int section(LadsGraphics g, String label, int x, int y) {
        g.drawText(label, x, y, ACCENT);
        return y + 13;
    }
    private void tile(LadsGraphics g, String id, String label, Rect r, String skin, int variant, boolean selected, int mx, int my, Runnable action, boolean scrolled) {
        if (scrolled && (r.y + r.height <= viewport.y || r.y >= viewport.y + viewport.height)) return;
        boolean hover = r.contains(mx, my) && (!scrolled || viewport.contains(mx, my));
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

        HudElement hudEl = findHudElement(m.getName());

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
                // The chosen skin's variants sit under the banner (Custom: its banner skin's), so no scrolling to reach them.
                KillBannerStyle varSkin = banner.bannerStyle.getIndex() == KillBannerModule.CUSTOM
                    ? KillBannerModule.visualOrSoundSkin(banner.customVisual.getIndex()) : pick.style();
                int varH = 0;
                if (varSkin != null && varSkin.variantNames.length > 1) {
                    int perRow = Math.min(4, varSkin.variantNames.length), tileW = (boxW - 8 - (perRow - 1) * 4) / perRow;
                    varH = 13 + ((varSkin.variantNames.length + perRow - 1) / perRow) * (Math.max(36, Math.min(60, tileW * 3 / 4 + 12)) + 4) + 4;
                }
                int bannerH = Math.max(40, boxH - varH);
                // The banner plays, kills 1 to 5 in turn; its spray stays inside the box.
                g.enableScissor(boxX + 2, boxY + 2, boxX + boxW - 2, boxY + bannerH - 2);
                boolean drawn = g.drawKillBannerPreview(pick.style() == null ? "base" : pick.style().id, pick.variant(),
                    boxX + 6, boxY + 6, boxW - 12, bannerH - 30, System.nanoTime() / 1e9);
                g.disableScissor();
                if (!drawn) g.drawCenteredText("KILL BANNER", centerX, boxY + bannerH / 2 - 4, ACCENT);
                String caption = pick.style() == null ? "Base" : pick.style().displayName + " · " + pick.style().variantNames[pick.variant()];
                if (pick.soundStyle() != pick.style()) caption += " · " + (pick.ownSounds() ? "Your" : pick.soundStyle() == null ? "Chime" : pick.soundStyle().displayName) + " sound";
                if (banner.randomize.getIndex() != KillBannerModule.RANDOM_OFF) caption += " · Randomized";
                g.drawCenteredText(fit(g, caption, boxW - 8), centerX, boxY + bannerH - 18, TEXT);
                if (varH > 0) {
                    g.fill(boxX + 6, boxY + bannerH + 1, boxX + boxW - 6, boxY + bannerH + 2, LadsPalette.BORDER);
                    variantTiles(g, banner, varSkin, boxX + 4, boxY + bannerH + 6, boxW - 8, mx, my);
                }
            } else if (m instanceof CrosshairModule) {
                // The crosshair as the game draws it with these options, over the chosen background (a biome, gradient, colour, your
                // image or the live game: CrosshairBackground); the scissor keeps a large Scale inside the box. Versions that cannot
                // draw it keep a plain reticle.
                CrosshairModule crosshair = (CrosshairModule) m;
                int bx = boxX + 3, by = boxY + 3, bw = boxW - 6, bh = boxH - 6;
                g.enableScissor(bx, by, bx + bw, by + bh);
                CrosshairBackground.draw(g, crosshair, bx, by, bw, bh);
                String caption = fit(g, CrosshairBackground.caption(crosshair, g.getGame().isIngame()), bw - 16);
                if (!caption.isEmpty()) {
                    int labelW = g.textWidth(caption) + 10;
                    round(g, centerX - labelW / 2, by + bh - 18, labelW, 14, 0xC0000000);
                    g.drawCenteredText(caption, centerX, by + bh - 14, TEXT, false);
                }
                if (!g.drawCrosshairPreview(centerX, centerY)) {
                    g.fill(centerX - 10, centerY - 1, centerX - 3, centerY + 1, 0xFFFFFFFF);
                    g.fill(centerX + 3, centerY - 1, centerX + 10, centerY + 1, 0xFFFFFFFF);
                    g.fill(centerX - 1, centerY - 10, centerX + 1, centerY - 3, 0xFFFFFFFF);
                    g.fill(centerX - 1, centerY + 3, centerX + 1, centerY + 10, 0xFFFFFFFF);
                }
                g.disableScissor();
            } else if ("Fullbright".equalsIgnoreCase(name)) {
                // The live world, so a change shows while it is made; versions that cannot copy it keep the card.
                int[] view = g.drawGameView16x9(boxX + 2, boxY + 2, boxW - 4, boxH - 4);
                if (view != null) {
                    gameView = new Rect(view[0], view[1], view[2], view[3]);
                    String live = m.isEnabled() && m.getOption("Brightness") instanceof SliderOption gamma ? "Fullbright " + gamma.display() : "Fullbright off";
                    int labelW = g.textWidth(live) + 10;
                    round(g, centerX - labelW / 2, boxY + boxH - 18, labelW, 14, 0xC0000000);
                    g.drawCenteredText(live, centerX, boxY + boxH - 14, TEXT, false);
                } else {
                    round(g, centerX - 40, centerY - 25, 80, 50, 0x30FFFFFF);
                    g.drawCenteredText("GAMMA BOOST", centerX, centerY - 10, ACCENT);
                    g.drawCenteredText(m.isEnabled() ? "Brighter than Brightness 100%" : "Standard Gamma", centerX, centerY + 6, TEXT);
                }
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
            } else if ("Nametags".equalsIgnoreCase(name) || "ToggleNametags".equalsIgnoreCase(name)) {
                round(g, centerX - 50, centerY - 14, 100, 26, 0x80000000);
                round(g, centerX - 50, centerY - 14, 100, 26, m.isEnabled() ? ACCENT : LadsPalette.BORDER);
                g.drawCenteredText("[LAD] Steve", centerX, centerY - 10, ACCENT);
                g.drawCenteredText("20 ❤ · 14m", centerX, centerY + 2, TEXT);
            } else if ("Hitboxes".equalsIgnoreCase(name)) {
                round(g, centerX - 30, centerY - 35, 60, 70, 0x20FFFFFF);
                g.fill(centerX - 30, centerY - 35, centerX + 30, centerY - 34, 0xFFFFFFFF);
                g.fill(centerX - 30, centerY + 34, centerX + 30, centerY + 35, 0xFFFFFFFF);
                g.fill(centerX - 30, centerY - 35, centerX - 29, centerY + 35, 0xFFFFFFFF);
                g.fill(centerX + 29, centerY - 35, centerX + 30, centerY + 35, 0xFFFFFFFF);
                // Eye line
                g.fill(centerX - 30, centerY - 12, centerX + 30, centerY - 11, 0xFFFF0000);
                g.drawCenteredText("Entity Hitbox", centerX, centerY + 10, MUTED);
            } else if (com.thelads.core.client.DamageTilt.MODULE.equalsIgnoreCase(name) || "CameraTilt".equalsIgnoreCase(name)) {
                round(g, centerX - 40, centerY - 20, 80, 40, CARD);
                g.drawCenteredText("TILT ANGLE", centerX, centerY - 10, ACCENT);
                g.drawCenteredText("Dynamic Screen Tilt", centerX, centerY + 4, TEXT);
            } else if ("DynamicLights".equalsIgnoreCase(name)) {
                round(g, centerX - 45, centerY - 25, 90, 50, CARD);
                round(g, centerX - 45, centerY - 25, 90, 50, m.isEnabled() ? 0xFFFFAA00 : LadsPalette.BORDER);
                g.drawCenteredText("DYNAMIC LIGHT", centerX, centerY - 10, 0xFFFFAA00);
                g.drawCenteredText(m.isEnabled() ? "Handheld Torch Lit" : "Disabled", centerX, centerY + 6, TEXT);
            } else if ("MotionBlur".equalsIgnoreCase(name)) {
                round(g, centerX - 45, centerY - 25, 90, 50, CARD);
                g.drawCenteredText("MOTION BLUR", centerX, centerY - 10, ACCENT);
                g.drawCenteredText("Frame Blending", centerX, centerY + 6, TEXT);
            } else if ("DiscordRPC".equalsIgnoreCase(name) || name.toLowerCase(Locale.ROOT).contains("discord")) {
                round(g, centerX - 60, centerY - 24, 120, 48, CARD);
                round(g, centerX - 60, centerY - 24, 120, 48, 0xFF5865F2);
                g.drawCenteredText("Playing The Lads", centerX, centerY - 14, 0xFF5865F2);
                g.drawCenteredText("In-Game · Server", centerX, centerY, TEXT);
                g.drawCenteredText("Rich Presence", centerX, centerY + 12, MUTED);
            } else if ("Chat".equalsIgnoreCase(name) || "ChatFilter".equalsIgnoreCase(name)) {
                round(g, centerX - 65, centerY - 20, 130, 40, 0x80000000);
                g.drawText("[Chat] <Player> Hello!", centerX - 60, centerY - 14, TEXT);
                g.drawText("[Lads] Welcome!", centerX - 60, centerY + 2, ACCENT);
            } else if ("BetterF3".equalsIgnoreCase(name)) {
                round(g, centerX - 62, centerY - 30, 124, 60, 0x90000000);
                g.drawText("240 fps / Unlimited fps", centerX - 56, centerY - 24, 0xFF55FF55);
                g.drawText("XYZ: ", centerX - 56, centerY - 12, 0xFFFF5555);
                g.drawText("12.5 64.0 -8.2", centerX - 56 + g.textWidth("XYZ: "), centerY - 12, 0xFF55FFFF);
                g.drawText("Biome: ", centerX - 56, centerY, 0xFF4FE3B5);
                g.drawText("minecraft:plains", centerX - 56 + g.textWidth("Biome: "), centerY, 0xFF55FF55);
                g.drawText("CPU: ", centerX - 56, centerY + 12, 0xFF55FFFF);
                g.drawText("8x Ryzen 7", centerX - 56 + g.textWidth("CPU: "), centerY + 12, 0xFFFFFF55);
            } else {
                round(g, centerX - 50, centerY - 32, 100, 64, CARD);
                round(g, centerX - 50, centerY - 32, 100, 64, m.isEnabled() ? ACCENT : LadsPalette.BORDER);
                g.drawModIcon(name.toLowerCase(Locale.ROOT), centerX - 14, centerY - 26, 28);
                g.drawCenteredText(fit(g, name, boxW - 12), centerX, centerY + 6, TEXT);
                g.drawCenteredText(m.getCategory().name(), centerX, centerY + 18, m.isEnabled() ? ACCENT : MUTED);
            }

            g.drawCenteredText(fit(g, "Live Settings Preview", boxW - 4), boxX + boxW / 2, y + h - 14, MUTED);
        }
    }
    public void openHudFpsDialog() {
        fpsDialogOpen = true;
        fpsBox = null;
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
        // A drag passes each level many times: the config is written when the level changes, not on every mouse move.
        boolean same = chosen == HudSettings.getInstance().getHudFpsLimit() && HudSettings.getInstance().isHudFpsCapEnabled();
        HudSettings.getInstance().setHudFpsLimit(chosen);
        if (!HudSettings.getInstance().isHudFpsCapEnabled()) HudSettings.getInstance().setHudFpsCapEnabled(true);
        fpsBuffer = chosen == 0 ? "0" : String.valueOf(chosen);
        if (!same) ConfigManager.save();
        dirty = true;
    }
    /** QA and tests: the HUD FPS popup's box, then every control in it, as the last frame laid them out (empty while it is closed or not drawn). */
    public List<Rect> hudFpsDialogRects() {
        if (!fpsDialogOpen || fpsBox == null) return List.of();
        List<Rect> rects = new ArrayList<>(List.of(fpsBox, fpsClose, fpsToggle, fpsInput, fpsApply));
        rects.addAll(Arrays.asList(fpsPresets));
        rects.addAll(fpsExceptionRects.values());
        return rects;
    }
    private static final String[] FPS_EXEMPT_SAMPLE = {"Keystrokes", "CPS", "FPS", "ArmorHUD", "Minimap", "Voice Chat"};
    // The HUD frame rate popup's rectangles as the last render laid them out; clicks use the same ones.
    private Rect fpsBox, fpsClose, fpsToggle, fpsInput, fpsApply;
    private Rect[] fpsPresets = new Rect[0];
    private int fpsTitleY, fpsSubY, fpsLabelX, fpsLabelY, fpsHintX, fpsHintY, fpsExcLabelY, fpsRateY, fpsRateHintY;
    /**
     * Lays the popup out for a box at dx, dy and returns the box height. {@code compact} tightens the rows (and drops the hint lines)
     * for a window too short for the roomy layout; the box is as tall as its content so nothing hangs out of it.
     */
    private int layoutHudFpsDialog(LadsGraphics g, int dx, int dy, int dw, boolean compact) {
        int pad = 12, rowH = compact ? 16 : 18, fieldH = compact ? 20 : 22;
        fpsClose = new Rect(dx + dw - 52, dy + (compact ? 6 : 8), 42, 20);
        fpsTitleY = dy + (compact ? 7 : 11);
        fpsSubY = dy + (compact ? 17 : 23);
        fpsToggle = new Rect(dx + pad, dy + (compact ? 28 : 40), dw - 2 * pad, compact ? 18 : 22);
        fpsSliderTrack = new Rect(dx + pad, dy + (compact ? 50 : 78), dw - 2 * pad, 14);
        int levels = HudSettings.HUD_FPS_LEVELS.length, btnW = (dw - 2 * pad - (levels - 1) * 3) / levels, presetY = dy + (compact ? 68 : 100);
        fpsPresets = new Rect[levels];
        for (int i = 0; i < levels; i++) fpsPresets[i] = new Rect(dx + pad + i * (btnW + 3), presetY, btnW, rowH);
        int inputY = dy + (compact ? 88 : 144);
        fpsLabelX = dx + pad;
        fpsLabelY = compact ? inputY + 6 : inputY - 14;
        int inputX = compact ? dx + pad + g.textWidth("Or type custom FPS:") + 6 : dx + pad;
        fpsInput = new Rect(inputX, inputY, 76, fieldH);
        fpsApply = new Rect(inputX + 82, inputY, 48, fieldH);
        fpsHintX = fpsApply.x + fpsApply.width + 8;
        fpsHintY = compact ? -1 : inputY + 6;
        fpsExcLabelY = dy + (compact ? 112 : 175);
        int chipH = compact ? 16 : 18, chipX = dx + pad, chipY = dy + (compact ? 123 : 189);
        fpsExceptionRects.clear();
        for (String mod : FPS_EXEMPT_SAMPLE) {
            int chipW = g.textWidth(mod) + 14;
            if (chipX + chipW > dx + dw - pad) {
                chipX = dx + pad;
                chipY += chipH + 4;
            }
            fpsExceptionRects.put(mod, new Rect(chipX, chipY, chipW, chipH));
            chipX += chipW + 4;
        }
        fpsRateY = chipY + chipH + (compact ? 7 : 18);
        fpsRateHintY = compact ? -1 : fpsRateY + 13;
        return (compact ? fpsRateY + 9 + 6 : fpsRateHintY + 9 + 7) - dy;
    }
    private void renderHudFpsDialog(LadsGraphics g, int mx, int my) {
        if (!fpsDialogOpen) return;
        int dw = Math.min(370, width - 24);
        int dh = layoutHudFpsDialog(g, 0, 0, dw, false);
        boolean compact = dh > height - 8;
        if (compact) dh = layoutHudFpsDialog(g, 0, 0, dw, true);
        dh = Math.min(dh, height - 8);
        int dx = (width - dw) / 2, dy = (height - dh) / 2;
        layoutHudFpsDialog(g, dx, dy, dw, compact);
        fpsBox = new Rect(dx, dy, dw, dh);

        g.fill(0, 0, width, height, 0xB0000000);
        round(g, dx - 2, dy + 3, dw + 4, dh + 4, 0x14000000);
        round(g, dx, dy + 1, dw, dh, 0x28000000);
        round(g, dx, dy, dw, dh, LadsPalette.BORDER);
        round(g, dx + 1, dy + 1, dw - 2, dh - 2, PANEL);
        g.fill(dx + 1, dy, dx + dw - 1, dy + 2, ACCENT);

        g.drawText("GLOBAL HUD FRAME RATE CAP", dx + 12, fpsTitleY, ACCENT);
        g.drawText(fit(g, "Controls refresh rate of all HUD modules & vanilla HUD", dw - 24 - fpsClose.width - 6), dx + 12, fpsSubY, MUTED);

        boolean closeHover = fpsClose.contains(mx, my);
        round(g, fpsClose.x, fpsClose.y, fpsClose.width, fpsClose.height, closeHover ? LadsPalette.HOVER : CARD);
        g.drawCenteredText("Done", fpsClose.x + fpsClose.width / 2, fpsClose.y + (fpsClose.height - g.fontHeight()) / 2 + 1, TEXT);

        boolean capEnabled = HudSettings.getInstance().isHudFpsCapEnabled();
        int currentFps = HudSettings.getInstance().getHudFpsLimit();
        boolean toggleHover = fpsToggle.contains(mx, my);
        round(g, fpsToggle.x, fpsToggle.y, fpsToggle.width, fpsToggle.height,
            capEnabled ? (toggleHover ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY) : (toggleHover ? LadsPalette.HOVER : CARD));
        String statusLabel = capEnabled ? "HUD FPS LIMIT: ENABLED (" + HudSettings.formatFpsLimit(currentFps) + ")" : "HUD FPS LIMIT: OFF (UNCAPPED)";
        g.drawCenteredText(statusLabel, fpsToggle.x + fpsToggle.width / 2, fpsToggle.y + (fpsToggle.height - g.fontHeight()) / 2 + 1, TEXT);

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

        for (int i = 0; i < levels.length; i++) {
            Rect btn = fpsPresets[i];
            boolean active = levels[i] == currentFps || (levels[i] == 0 && (currentFps <= 0 || currentFps > 240));
            boolean hover = btn.contains(mx, my);
            round(g, btn.x, btn.y, btn.width, btn.height, active ? LadsPalette.PRIMARY : (hover ? LadsPalette.HOVER : CARD));
            String lbl = levels[i] == 0 ? "Max" : levels[i] == 60 ? "60*" : String.valueOf(levels[i]);
            g.drawCenteredText(lbl, btn.x + btn.width / 2, btn.y + (btn.height - g.fontHeight()) / 2 + 1, active ? TEXT : MUTED);
        }

        g.drawText("Or type custom FPS:", fpsLabelX, fpsLabelY, MUTED);
        round(g, fpsInput.x, fpsInput.y, fpsInput.width, fpsInput.height, editingFps ? ACCENT : LadsPalette.BORDER);
        round(g, fpsInput.x + 1, fpsInput.y + 1, fpsInput.width - 2, fpsInput.height - 2, editingFps ? CARD : PANEL);
        String displayVal = editingFps ? (fpsBuffer + "|") : (currentFps == 0 ? "0" : String.valueOf(currentFps));
        g.drawCenteredText(displayVal, fpsInput.x + fpsInput.width / 2, fpsInput.y + (fpsInput.height - g.fontHeight()) / 2 + 1, TEXT);

        boolean applyHover = fpsApply.contains(mx, my);
        round(g, fpsApply.x, fpsApply.y, fpsApply.width, fpsApply.height, applyHover ? LadsPalette.HOVER : CARD);
        g.drawCenteredText("Apply", fpsApply.x + fpsApply.width / 2, fpsApply.y + (fpsApply.height - g.fontHeight()) / 2 + 1, TEXT);

        if (fpsHintY >= 0) g.drawText(fit(g, "60 FPS (Recommended) prevents tearing.", dx + dw - 12 - fpsHintX), fpsHintX, fpsHintY, MUTED);

        g.drawText("EXCEPTIONS (IGNORE FPS CAP)", dx + 12, fpsExcLabelY, ACCENT);
        for (var entry : fpsExceptionRects.entrySet()) {
            Rect cr = entry.getValue();
            boolean ex = HudSettings.getInstance().isModuleExempt(entry.getKey());
            round(g, cr.x, cr.y, cr.width, cr.height, ex ? LadsPalette.PRIMARY : (cr.contains(mx, my) ? LadsPalette.HOVER : CARD));
            g.drawCenteredText(entry.getKey(), cr.x + cr.width / 2, cr.y + (cr.height - g.fontHeight()) / 2 + 1, ex ? TEXT : MUTED);
        }

        g.drawText("Current HUD Rate: " + HudManager.getInstance().getMeasuredHudFps() + " FPS", dx + 12, fpsRateY, ACCENT);
        if (fpsRateHintY >= 0) g.drawText(fit(g, "Updates all HUD modules & vanilla HUD in real time.", dw - 24), dx + 12, fpsRateHintY, MUTED);
    }
    private void renderMods(LadsGraphics g, int x, int w, int mx, int my) {
        pollModState();
        int top = 42, resetW = 56;
        button(g, "mods-back", "Modules", new Rect(x, top, 86, 22), this::closeMods, true, mx, my, false);
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
        // Two columns, like the module cards, read left to right; one on a narrow panel. A row is as tall as its taller card.
        int gap = 4, cols = w >= 440 ? 2 : 1, cardW = (w - 8 - (cols - 1) * gap) / cols, total = 0;
        for (int i = 0; i < lines.size(); i += cols) total += modRowHeight(lines, i, cols) + gap;
        maxScroll = Math.max(0, total - gap - viewport.height);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        g.enableScissor(x, top, x + w, top + viewport.height);
        int y = top - renderScroll;
        for (int i = 0; i < lines.size(); i += cols) {
            int h = modRowHeight(lines, i, cols);
            if (y + h > top && y < top + viewport.height)
                for (int c = 0; c < cols && i + c < lines.size(); c++) modRow(g, lines.get(i + c), x + c * (cardW + gap), y, cardW, h, mx, my);
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
    private static int modRowHeight(List<ModLine> lines, int first, int cols) {
        int h = 0;
        for (int i = first; i < Math.min(lines.size(), first + cols); i++) h = Math.max(h, modRowHeight(lines.get(i).row()));
        return h;
    }
    private void modRow(LadsGraphics g, ModLine line, int x, int y, int w, int h, int mx, int my) {
        var row = line.row();
        int rx = x + line.depth() * 14, rw = w - line.depth() * 14, textX = rx + 38, right = rx + rw - 6;
        boolean narrow = rw < 300; // a two-column card: a smaller toggle and a gear for Settings leave the name room
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
            String state = row.requested() ? "ON" : "OFF";
            int toggleW = narrow ? 34 : 45;
            right -= toggleW;
            button(g, "mods:toggle:" + row.key(), state, new Rect(right, y + 6, toggleW, 18), () -> toggleModRow(row), row.canToggle(), mx, my, row.requested());
            right -= 6;
        }
        Runnable settings = modSettingsAction(row);
        if (settings != null) {
            int settingsW = narrow ? 18 : 58;
            right -= settingsW;
            // Narrow: the label does not fit and draws nothing; the gear stands for it (the control keeps the label).
            button(g, "mods:settings:" + row.key(), "Settings", new Rect(right, y + 6, settingsW, 18), settings, true, mx, my, false);
            if (narrow) gear(g, right + 5, y + 11, TEXT);
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
    /** An option row is 43 px; a sound list, and Combo Sounds' sets, add 24 px for each entry. */
    private int rowHeight(Option option) {
        if (option instanceof CrosshairModule.ProfilesOption) return 62;
        if (option instanceof SoundListOption list) return 43 + list.size() * 24;
        if (option instanceof ComboSoundsModule.SoundSets && detail instanceof ComboSoundsModule combo) return 43 + combo.soundSets().size() * 24;
        return 43;
    }
    private static String displayValue(SliderOption s) {
        return SliderOption.format(s.getValue()) + (s.display().endsWith("%") ? "%" : "");
    }
    private static void drawLockIcon(LadsGraphics g, int lx, int ly, int color) {
        g.fill(lx + 1, ly, lx + 5, ly + 1, color);
        g.fill(lx + 1, ly + 1, lx + 2, ly + 4, color);
        g.fill(lx + 4, ly + 1, lx + 5, ly + 4, color);
        g.fill(lx, ly + 3, lx + 6, ly + 8, color);
    }
    private boolean isServerLocked(Option option) {
        if (option == null) return false;
        String mod = detail != null ? detail.getName() : option.getModuleName();
        if (mod == null || mod.isEmpty()) return false;
        ServerPolicy policy = ServerPolicy.active();
        if (policy == null) return false;
        OptionRule rule = policy.option(mod, option.getName());
        return rule != null && ((rule.forcesValue() && !policy.refusesChanges()) || (rule.lock && !rule.forcesValue()));
    }
    private void optionRow(LadsGraphics g, Option option, int x, int y, int w, int mx, int my) {
        if (option instanceof CrosshairModule.ProfilesOption profiles) { profilesRow(g, profiles, x, y, w, mx, my); return; }
        if (option instanceof SoundOption sound) { soundRow(g, sound, x, y, w, mx, my); return; }
        if (option instanceof SoundListOption list) { soundListRow(g, list, x, y, w, mx, my); return; }
        if (option instanceof ComboSoundsModule.SoundSets && detail instanceof ComboSoundsModule combo) { setsRow(g, combo, x, y, w, mx, my); return; }
        round(g, x, y, w, 37, CARD);
        int controlW = Math.min(155, w / 2);

        ServerPolicy policy = ServerPolicy.active();
        OptionRule rule = (policy != null && detail != null) ? policy.option(detail.getName(), option.getName()) : null;
        boolean serverLocked = isServerLocked(option);

        int nameX = x + 9;
        if (rule != null) {
            drawLockIcon(g, nameX, y + 14, serverLocked ? ACCENT : MUTED);
            nameX += 10;
        }
        g.drawText(fit(g, option.getName(), w - controlW - (rule != null ? 30 : 20)), nameX, y + 14, TEXT);

        boolean rowHovered = new Rect(x, y, w, 37).contains(mx, my) && viewport.contains(mx, my);
        if (rowHovered && rule != null) {
            tipOption = option;
            tipOptionRule = rule;
        }

        Rect r = new Rect(x + w - controlW - 6, y + 7, controlW, 23);
        String id = "option:" + option.getName();
        if (option instanceof ActionOption action) {
            button(g, id, action.getLabel(), r, () -> {
                if (action.getConfirm() == null) leave(action::run); else confirm.open(action.getConfirm(), () -> leave(action::run));
            }, action.isAvailable(), mx, my, false);
        } else if (option instanceof BoolOption b) {
            button(g, id, b.get() ? "ON" : "OFF", r, () -> { if (!serverLocked) { b.toggle(); changed(detail); } }, !serverLocked, mx, my, b.get());
        } else if (option instanceof DropdownOption d) {
            button(g, id, "< " + d.getValue() + " >", r, () -> { if (!serverLocked) { d.cycle(); changed(detail); } }, !serverLocked, mx, my, false);
        } else if (option instanceof SliderOption || option instanceof DoubleOption) {
            double min = option instanceof SliderOption s ? s.getMin() : ((DoubleOption)option).getMin();
            double max = option instanceof SliderOption s ? s.getMax() : ((DoubleOption)option).getMax();
            double value = option instanceof SliderOption s ? s.getValue() : ((DoubleOption)option).get();
            // Better Resolution's Scale only counts with the Custom preset: shown greyed and not draggable.
            boolean free = !(detail instanceof BetterResolutionModule resolution && resolution.locked(option)) && !serverLocked;
            if (option instanceof SliderOption s && s.isEditable()) {
                // The slider, and beside it the value as a text field: click it, type a whole number, Enter.
                Rect track = new Rect(r.x, r.y, r.width - 50, r.height), field = new Rect(r.x + r.width - 46, r.y, 46, r.height);
                int first = controls.size();
                button(g, id, "", track, () -> {}, free, mx, my, false);
                if (controls.size() > first) controls.set(first, new Control(id, s.getName() + ", " + displayValue(s), track, () -> {}, free));
                int fillW = (int)((track.width - 8) * (value - min) / Math.max(.001, max - min));
                g.fill(track.x + 4, track.y + 10, track.x + track.width - 4, track.y + 13, LadsPalette.BORDER);
                g.fill(track.x + 4, track.y + 10, track.x + 4 + fillW, track.y + 13, ACCENT);
                button(g, id + ":field", editingOption == option ? inputWindow(g, field.width - 10) : displayValue(s), field, () -> { if (!serverLocked) startEdit(s); }, free, mx, my, editingOption == option);
            } else {
                button(g, id, (option instanceof SliderOption s ? s.display() : SliderOption.format(value)), r, () -> {}, free, mx, my, false);
                int fillW = (int)((controlW - 8) * (value - min) / Math.max(.001, max - min));
                g.fill(r.x + 4, r.y + 20, r.x + 4 + fillW, r.y + 22, ACCENT);
            }
        } else if (option instanceof ColorOption c) {
            button(g, id, editingOption == option ? inputDisplay() : String.format("%08X", c.getColor()), new Rect(r.x, r.y, r.width - 58, r.height), () -> { if (!serverLocked) colorPicker.open(option.getName(), c.getOwnColor(), val -> { c.setColor(val); c.setUseGlobal(false); changed(detail); }); }, !serverLocked, mx, my, false);
            button(g, id + ":global", c.isUseGlobal() ? "Global" : "Own", new Rect(r.x + r.width - 54, r.y, 54, r.height), () -> { if (!serverLocked) { c.setUseGlobal(!c.isUseGlobal()); changed(detail); } }, !serverLocked, mx, my, c.isUseGlobal());
        } else if (option instanceof TextOption t) {
            button(g, id, editingOption == option ? inputWindow(g, r.width - 10) : t.getValue(), r, () -> startEdit(option), true, mx, my, editingOption == option);
        } else if (option instanceof ParticleReductionOption p) {
            button(g, id, p.changed() == 0 ? "Edit..." : p.changed() + " changed...", r, () -> particleRates.open(p, () -> changed(detail)), true, mx, my, p.changed() > 0);
        }
    }
    /** An uploaded-sound slot (SoundOption): its name and file, then Choose (the sound library), Play and Clear. */
    private void soundRow(LadsGraphics g, SoundOption sound, int x, int y, int w, int mx, int my) {
        Module module = detail;
        round(g, x, y, w, 37, CARD);
        // Each button as wide as its label, so all three fit a narrow column (GUI scale 2 in a small window); the name takes the rest.
        int chooseW = g.textWidth("Choose") + 14, playW = g.textWidth("Play") + 14, clearW = g.textWidth("Clear") + 14;
        int controlW = chooseW + playW + clearW + 8;
        g.drawText(fit(g, sound.getName(), w - controlW - 20), x + 9, y + 8, TEXT);
        g.drawText(fit(g, sound.isSet() ? sound.get() : builtInSound(module, sound), w - controlW - 20), x + 9, y + 21, MUTED);
        String id = "option:" + sound.getName();
        int rx = x + w - controlW - 6, ry = y + 7;
        button(g, id + ":choose", "Choose", new Rect(rx, ry, chooseW, 23), () -> library.open(sound.getName(), sound.get(), soundVolume(module), file -> {
            sound.set(file);
            changed(module);
            notice = sound.getName() + ": " + file;
            previewSound(sound, module);
        }), true, mx, my, false);
        button(g, id + ":play", "Play", new Rect(rx + chooseW + 4, ry, playW, 23), () -> previewSound(sound, module),
            sound.isSet() || module instanceof ComboSoundsModule, mx, my, false);
        button(g, id + ":clear", "Clear", new Rect(rx + chooseW + playW + 8, ry, clearW, 23),
            () -> { sound.set(""); changed(module); }, sound.isSet(), mx, my, false);
    }
    private static String builtInSound(Module module, SoundOption sound) {
        if (!(module instanceof ComboSoundsModule combo)) return module instanceof com.thelads.core.modules.OofModule ? "Built-in classic oof" : "Plain chime";
        return sound == combo.longShotSound ? "Built-in level-up chime" : sound == combo.headshotSound ? "Built-in helmet clink" : "Built-in pling";
    }
    private static double soundVolume(Module module) {
        return module instanceof ComboSoundsModule combo ? combo.volume.getValue() : module instanceof KillBannerModule banner ? banner.volume.getValue()
            : module instanceof com.thelads.core.modules.OofModule oof ? oof.volume.getValue() : 1;
    }
    private static void previewSound(SoundOption sound, Module module) {
        if (module instanceof ComboSoundsModule combo) combo.preview(sound);
        else if (sound.isSet()) CustomSounds.play(sound.get(), soundVolume(module));
    }
    /** A list of sounds (After Combo 5): Add (the sound library), then each file with up, down, Play and remove. */
    private void soundListRow(LadsGraphics g, SoundListOption list, int x, int y, int w, int mx, int my) {
        Module module = detail;
        List<String> files = list.get();
        round(g, x, y, w, 37 + files.size() * 24, CARD);
        int addW = g.textWidth("Add...") + 14;
        g.drawText(fit(g, list.getName(), w - addW - 20), x + 9, y + 8, TEXT);
        String summary = files.isEmpty() ? module instanceof ComboSoundsModule ? "Empty: Combo 5 again" : "Empty" : files.size() + (files.size() == 1 ? " sound" : " sounds");
        g.drawText(fit(g, summary, w - addW - 20), x + 9, y + 21, MUTED);
        String id = "option:" + list.getName();
        button(g, id + ":add", "Add...", new Rect(x + w - addW - 6, y + 7, addW, 23), () -> library.open(list.getName(), "", soundVolume(module), file -> {
            list.add(file);
            changed(module);
            notice = list.getName() + ": added " + file;
        }), true, mx, my, false);
        int upW = g.textWidth("^") + 10, downW = g.textWidth("v") + 10, playW = g.textWidth("Play") + 10, removeW = g.textWidth("x") + 10;
        for (int i = 0; i < files.size(); i++) {
            int index = i, ry = y + 37 + i * 24;
            String file = files.get(i);
            round(g, x + 6, ry - 1, w - 9, 22, PANEL); // each entry a darker strip, so its buttons show
            Rect remove = new Rect(x + w - 6 - removeW, ry, removeW, 20), play = new Rect(remove.x - 3 - playW, ry, playW, 20);
            Rect down = new Rect(play.x - 3 - downW, ry, downW, 20), up = new Rect(down.x - 3 - upW, ry, upW, 20);
            g.drawText(fit(g, (i + 1) + ". " + file, up.x - x - 20), x + 15, ry + 6, TEXT);
            button(g, id + ":up:" + i, "^", up, () -> { list.move(index, -1); changed(module); }, i > 0, mx, my, false);
            button(g, id + ":down:" + i, "v", down, () -> { list.move(index, 1); changed(module); }, i < files.size() - 1, mx, my, false);
            button(g, id + ":play:" + i, "Play", play, () -> CustomSounds.play(file, soundVolume(module)), true, mx, my, false);
            button(g, id + ":remove:" + i, "x", remove, () -> { list.remove(index); changed(module); }, true, mx, my, false);
        }
    }
    /** The set being renamed (a text field whose value renames it) and its index among the player's sets. */
    private TextOption renaming;
    private int renamingIndex = -1;
    /** Combo Sounds' Sound Sets: Save current, then each set (click: Combo 1-5 at once; the one in use is lit), Rename and Delete for yours. */
    private void setsRow(LadsGraphics g, ComboSoundsModule combo, int x, int y, int w, int mx, int my) {
        List<ComboSoundsModule.SoundSet> sets = combo.soundSets();
        round(g, x, y, w, 37 + sets.size() * 24, CARD);
        int saveW = g.textWidth("Save current") + 14;
        String id = "option:" + combo.sets.getName();
        g.drawText(fit(g, combo.sets.getName(), w - saveW - 20), x + 9, y + 8, TEXT);
        g.drawText(fit(g, "Click one to fill Combo 1-5", w - saveW - 20), x + 9, y + 21, MUTED);
        button(g, id + ":save", "Save current", new Rect(x + w - saveW - 6, y + 7, saveW, 23), () -> {
            int index = combo.saveSet();
            changed(combo);
            renameSet(combo, index);
        }, true, mx, my, false);
        int renameW = g.textWidth("Rename") + 10, deleteW = g.textWidth("Delete") + 10;
        for (int i = 0; i < sets.size(); i++) {
            ComboSoundsModule.SoundSet set = sets.get(i);
            int own = i - 1, ry = y + 37 + i * 24, right = x + w - 6; // own: the index among the player's sets (Default pling: -1)
            round(g, x + 6, ry - 1, w - 9, 22, PANEL); // each set a darker strip, so its buttons show
            Rect delete = new Rect(right - deleteW, ry, deleteW, 20), rename = new Rect(delete.x - 3 - renameW, ry, renameW, 20);
            Rect name = new Rect(x + 9, ry, (own < 0 ? right : rename.x - 3) - x - 9, 20);
            boolean editing = renaming != null && editingOption == renaming && renamingIndex == own;
            button(g, id + ":use:" + i, editing ? inputWindow(g, name.width - 10) : set.name(), name, editing ? () -> {} : () -> {
                combo.use(set);
                changed(combo);
                notice = "Sound set: " + set.name();
            }, true, mx, my, editing || combo.using(set));
            if (own < 0) continue;
            button(g, id + ":rename:" + i, "Rename", rename, () -> renameSet(combo, own), true, mx, my, false);
            button(g, id + ":delete:" + i, "Delete", delete, () -> confirm.open("Delete this sound set?",
                set.name() + " goes; its sounds stay in your library.", () -> { combo.deleteSet(own); changed(combo); }), true, mx, my, false);
        }
    }
    private void renameSet(ComboSoundsModule combo, int index) {
        renamingIndex = index;
        renaming = new TextOption("Set name", combo.sets.get().get(index).name()) {
            @Override public void setValue(String value) {
                super.setValue(value);
                combo.renameSet(index, value);
            }
        };
        startEdit(renaming);
    }
    private TextOption savingProfileOption;
    private void profilesRow(LadsGraphics g, CrosshairModule.ProfilesOption profilesOpt, int x, int y, int w, int mx, int my) {
        round(g, x, y, w, 56, CARD);
        String id = "option:" + profilesOpt.getName();
        List<String> list = CrosshairProfiles.list();
        String current = profilesOpt.getSelected();
        if ((current.isEmpty() || !CrosshairProfiles.has(current)) && !list.isEmpty()) {
            current = list.get(0);
        }
        int dropW = Math.min(150, w / 2);

        // Top line: title "Profiles" and dropdown of saved profiles
        g.drawText(fit(g, "Profiles", w - dropW - 20), x + 9, y + 15, TEXT);
        String dropLabel = list.isEmpty() ? "< None >" : "< " + current + " >";
        Rect dropRect = new Rect(x + w - dropW - 6, y + 6, dropW, 22);
        button(g, id + ":dropdown", dropLabel, dropRect, () -> cycleProfile(false), !list.isEmpty(), mx, my, false);

        int delW = g.textWidth("Delete") + 12;
        boolean editingSave = editingOption != null && editingOption == savingProfileOption;
        int saveW = editingSave ? Math.max(g.textWidth("Save as...") + 12, dropW - delW - 4) : g.textWidth("Save as...") + 12;
        Rect delRect = new Rect(x + w - delW - 6, y + 31, delW, 20);
        Rect saveRect = new Rect(delRect.x - 4 - saveW, y + 31, saveW, 20);

        // Bottom line: description and Save as... / Delete buttons
        int descAvail = saveRect.x - (x + 9) - 4;
        g.drawText(fit(g, "Saved profiles", descAvail), x + 9, y + 39, MUTED);

        button(g, id + ":save", editingSave ? inputWindow(g, saveRect.width - 10) : "Save as...", saveRect, () -> {
            startSaveProfile(profilesOpt);
        }, true, mx, my, editingSave);

        final String delTarget = current;
        button(g, id + ":delete", "Delete", delRect, () -> {
            if (delTarget.isEmpty()) return;
            confirm.open("Delete crosshair profile?", "Delete profile '" + delTarget + "'?", () -> {
                CrosshairProfiles.delete(delTarget);
                List<String> rem = CrosshairProfiles.list();
                profilesOpt.setSelected(rem.isEmpty() ? "" : rem.get(0));
                if (!rem.isEmpty()) {
                    CrosshairProfiles.apply(rem.get(0));
                }
                changed(detail);
            });
        }, !list.isEmpty() && !current.isEmpty(), mx, my, false);
    }

    private void cycleProfile(boolean back) {
        List<String> list = CrosshairProfiles.list();
        if (list.isEmpty()) return;
        CrosshairModule.ProfilesOption opt = detail instanceof CrosshairModule cm ? cm.profiles : null;
        if (opt == null) return;
        String current = opt.getSelected();
        if ((current.isEmpty() || !CrosshairProfiles.has(current)) && !list.isEmpty()) {
            current = list.get(0);
        }
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(current)) {
                idx = i;
                break;
            }
        }
        int nextIdx = back ? (idx - 1 + list.size()) % list.size() : (idx + 1) % list.size();
        String nextName = list.get(nextIdx);
        opt.setSelected(nextName);
        CrosshairProfiles.apply(nextName);
        notice = "Profile: " + nextName;
        changed(detail);
    }

    private void startSaveProfile(CrosshairModule.ProfilesOption profilesOpt) {
        String defaultName = CrosshairProfiles.nextDefaultName();
        savingProfileOption = new TextOption("Profile name", defaultName) {
            @Override
            public void setValue(String value) {
                super.setValue(value);
                String trimmed = CrosshairProfiles.validateName(value);
                if (trimmed != null && CrosshairProfiles.save(trimmed)) {
                    profilesOpt.setSelected(trimmed);
                    CrosshairProfiles.apply(trimmed);
                    notice = "Saved profile: " + trimmed;
                    changed(detail);
                } else {
                    notice = "Name must be 1-32 characters.";
                }
            }
        };
        startEdit(savingProfileOption);
    }
    private void button(LadsGraphics g, String id, String label, Rect r, Runnable action, boolean enabled, int mx, int my, boolean selected) {
        boolean content = contentControl(id);
        if (content && (r.y + r.height <= viewport.y || r.y >= viewport.y + viewport.height)) return;
        boolean hover = r.contains(mx, my) && (!content || viewport.contains(mx, my));
        float progress = animate(id, enabled && (hover || focusId.equals(id)));
        if (focusId.equals(id)) round(g, r.x - 1, r.y - 1, r.width + 2, r.height + 2, ACCENT);
        int color = !enabled ? PANEL : selected ? mix(LadsPalette.PRIMARY, LadsPalette.PRIMARY_HOVER, progress) : mix(CARD, LadsPalette.HOVER, progress);
        round(g, r.x, r.y, r.width, r.height, color);
        iconLabel(g, fit(g, label, r.width - 10), id.equals("hud") ? "edit" : id.startsWith("category:") ? ModuleGroups.icon(id.substring(9)) : null, r.x + r.width / 2, r.y + (r.height - g.fontHeight()) / 2 + 1, r.width, enabled ? TEXT : LadsPalette.DISABLED);
        controls.add(new Control(id, label, r, action, enabled));
    }
    /** Row sub-button: an icon on dark glass that reads on green, red and neutral rows; the label is for narration. */
    private void iconChip(LadsGraphics g, String id, String label, String icon, Rect r, Runnable action, int mx, int my, int color) {
        boolean focused = focusId.equals(id);
        float progress = animate(id, r.contains(mx, my) && viewport.contains(mx, my) || focused);
        if (focused) round(g, r.x - 1, r.y - 1, r.width + 2, r.height + 2, TEXT);
        round(g, r.x, r.y, r.width, r.height, mix(0x59000000, 0x30FFFFFF, progress));
        com.thelads.core.client.title.TitleScreenTheme.icon(g, icon, r.x + (r.width - 9) / 2, r.y + (r.height - 9) / 2, 9, color);
        controls.add(new Control(id, label, r, action, true));
    }
    /** The 9x9 cog. */
    private static void gear(LadsGraphics g, int x, int y, int color) {
        com.thelads.core.client.title.TitleScreenTheme.icon(g, "settings", x, y, 9, color);
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
            || id.startsWith("card:") || id.startsWith("group:") || id.startsWith("result:") || id.equals("reset") || id.startsWith("mods:") || id.startsWith("kb:");
    }
    private void scrollbar(LadsGraphics g) {
        if (maxScroll == 0) return;
        int trackW = 4;
        int trackX = viewport.x + viewport.width - trackW;
        int thumb = Math.max(14, (int)((long)viewport.height * viewport.height / (viewport.height + maxScroll)));
        int trackAvail = viewport.height - thumb;
        int y = viewport.y + (trackAvail > 0 ? (int)((long)trackAvail * scrollOffset / maxScroll) : 0);
        g.fill(trackX, viewport.y, trackX + trackW, viewport.y + viewport.height, PANEL);
        g.fill(trackX, y, trackX + trackW, y + thumb, draggingScrollbar ? ACCENT : MUTED);
    }
    public boolean mouseClicked(double x, double y, int button) {
        tip.dismiss();
        if (confirm.isOpen()) return confirm.click(x, y, button);
        if (library.click(x, y, button)) return true;
        if (actions.click(x,y,button)) return true;
        if (particleRates.click(x, y, button)) return true;
        if (clientImport.click(x, y, button)) return true;
        if (networkHub.click(x, y, button)) return true;
        if (colorPicker.click(x, y, button)) return true;
        if (fpsDialogOpen) {
            if (fpsBox == null) return true; // opened, not drawn yet
            if (!fpsBox.contains(x, y) || fpsClose.contains(x, y)) {
                closeHudFpsDialog();
                return true;
            }
            if (fpsToggle.contains(x, y)) {
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
            for (int i = 0; i < levels.length; i++) {
                if (fpsPresets[i].contains(x, y)) {
                    HudSettings.getInstance().setHudFpsLimit(levels[i]);
                    HudSettings.getInstance().setHudFpsCapEnabled(true);
                    fpsBuffer = levels[i] == 0 ? "0" : String.valueOf(levels[i]);
                    ConfigManager.save();
                    dirty = true;
                    return true;
                }
            }
            if (fpsInput.contains(x, y)) {
                startEditFps();
                return true;
            }
            if (fpsApply.contains(x, y)) {
                commitFpsEdit();
                return true;
            }
            for (var entry : fpsExceptionRects.entrySet()) {
                if (entry.getValue().contains(x, y)) {
                    String mod = entry.getKey();
                    boolean next = !HudSettings.getInstance().isModuleExempt(mod);
                    HudSettings.getInstance().setModuleExempt(mod, next);
                    ConfigManager.save();
                    dirty = true;
                    return true;
                }
            }
            if (editingFps) {
                commitFpsEdit();
            }
            return true;
        }
        refreshCapabilities();
        if (button != 0 && button != 1) return false;
        if (button == 0 && maxScroll > 0) {
            int hitW = 8;
            int trackX = viewport.x + viewport.width - hitW;
            if (x >= trackX && x <= viewport.x + viewport.width && y >= viewport.y && y <= viewport.y + viewport.height) {
                commitEdit();
                int thumbH = Math.max(14, (int)((long)viewport.height * viewport.height / (viewport.height + maxScroll)));
                int trackAvail = viewport.height - thumbH;
                int thumbY = viewport.y + (trackAvail > 0 ? (int)((long)trackAvail * scrollOffset / maxScroll) : 0);
                if (y >= thumbY && y <= thumbY + thumbH) {
                    draggingScrollbar = true;
                    scrollbarDragAnchor = (int)y - thumbY;
                } else if (y < thumbY) {
                    scrollOffset = Math.max(0, scrollOffset - viewport.height);
                } else {
                    scrollOffset = Math.min(maxScroll, scrollOffset + viewport.height);
                }
                return true;
            }
        }
        for (Control c : List.copyOf(controls)) {
            if (!c.rect.contains(x, y) || !c.enabled || contentControl(c.id) && !viewport.contains(x, y)) continue;
            if (!commitEdit()) return true;
            focusId = c.id;
            if (detail != null && c.id.startsWith("option:") && !c.id.endsWith(":global") && !c.id.endsWith(":field")) {
                Option o = activeOption(c.id.substring(7));
                if (isServerLocked(o)) return true;
                if (o instanceof SliderOption || o instanceof DoubleOption) { dragging = o; dragTrack = c.rect; updateDrag(x); return true; }
                // "< value >": the left half (or a right-click) steps back, the right half steps forward.
                if (o instanceof DropdownOption d) {
                    if (button == 1 || x < c.rect.x + c.rect.width / 2.0) d.cycleBack(); else d.cycle();
                    changed(detail); return true;
                }
                if (c.id.equals("option:Profiles:dropdown")) {
                    cycleProfile(button == 1 || x < c.rect.x + c.rect.width / 2.0);
                    return true;
                }
            }
            if (button == 0) c.action.run();
            return true;
        }
        commitEdit(); return true;
    }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (confirm.isOpen()) return true;
        if (networkHub.isOpen()) return true;
        if (library.drag(x)) return true;
        if (particleRates.drag(x, y)) return true;
        if (fpsDialogOpen && draggingFpsSlider) { updateFpsSlider(x); return true; }
        if (colorPicker.move(x, y)) return true;
        if (draggingScrollbar) {
            int thumbH = Math.max(14, (int)((long)viewport.height * viewport.height / (viewport.height + maxScroll)));
            int trackAvail = viewport.height - thumbH;
            if (trackAvail > 0) {
                int newThumbY = (int)y - scrollbarDragAnchor;
                double frac = Math.max(0.0, Math.min(1.0, (double)(newThumbY - viewport.y) / trackAvail));
                scrollOffset = (int)Math.round(frac * maxScroll);
            }
            return true;
        }
        refreshCapabilities();
        if (dragging == null) return false;
        updateDrag(x);
        return true;
    }
    public boolean mouseReleased(double x, double y, int button) {
        if (confirm.isOpen()) return true;
        if (networkHub.isOpen()) return true;
        if (library.release()) return true;
        if (particleRates.release()) return true;
        if (fpsDialogOpen && draggingFpsSlider) { updateFpsSlider(x); draggingFpsSlider = false; return true; }
        if (colorPicker.release()) return true;
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        refreshCapabilities();
        if (dragging == null) return false;
        updateDrag(x);
        dragging = null;
        persist();
        return true;
    }
    private void updateDrag(double x) {
        if (isServerLocked(dragging)) return;
        double t = Math.max(0, Math.min(1, (x - dragTrack.x - 4) / Math.max(1, dragTrack.width - 8)));
        if (dragging instanceof SliderOption s) s.setValue(s.getMin() + (s.getMax() - s.getMin()) * t);
        else if (dragging instanceof DoubleOption d) d.set(d.getMin() + (d.getMax() - d.getMin()) * t);
        // Saved once, when the drag ends (mouseReleased) or the page closes (finish): not the whole config on every mouse move.
        dirty = true; if (detail != null) detail.touch();
    }
    public boolean mouseScrolled(double x, double y, double amount) {
        if (confirm.isOpen()) return true;
        if (library.wheel(x, amount)) return true;
        if (actions.wheel(amount)) return true;
        if (particleRates.wheel(amount)) return true;
        if (clientImport.wheel(amount)) return true;
        if (networkHub.isOpen()) return true;
        if (colorPicker.isOpen() || fpsDialogOpen) return true;
        if (!viewport.contains(x, y)) return false;
        tip.dismiss(); scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(amount * 28 * (reducedMotion ? 1.0 : SmoothScroll.FACTOR)))); return true;
    }
    public boolean keyPressed(int key, int modifiers) {
        if (confirm.isOpen()) return confirm.key(key);
        if (library.key(key, modifiers)) return true;
        if (actions.key(key,modifiers)) return true;
        if (particleRates.key(key, modifiers)) return true;
        if (clientImport.key(key)) return true;
        if (networkHub.key(key)) return true;
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
            if (editingSearch || editingOption != null) { editingSearch = editingKbSearch = false; editingOption = null; notice = ""; }
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
            if (key == 257 || key == 335) { commitEdit(); return true; }
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
            List<Control> all = controls.stream().filter(c -> c.enabled).toList();
            if (all.isEmpty()) return true;
            int current = -1;
            for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(focusId)) current = i;
            int next = Math.floorMod(current + ((modifiers & 1) != 0 ? -1 : 1), all.size());
            Control target = all.get(next);
            focusId = target.id;
            if (contentControl(target.id)) scrollIntoView(target.rect);
            onNarrate.accept(target.label);
            return true;
        }
        if ((key == 262 || key == 263) && detail != null && focusId.startsWith("option:")) {
            String optName = focusId.substring(7);
            if (optName.endsWith(":field")) optName = optName.substring(0, optName.length() - 6);
            Option o = activeOption(optName);
            if (isServerLocked(o)) return false;
            int direction = key == 262 ? 1 : -1;
            if (o instanceof SliderOption s) s.setValue(s.getOwnValue() + direction * Math.max(s.getStep(), .1));
            else if (o instanceof DoubleOption d) d.set(d.get() + direction * .1);
            else if (o instanceof DropdownOption d) d.setIndex(d.getOwnIndex() + direction);
            else return false;
            changed(detail); return true;
        }
        if (key == 257 || key == 32) for (Control c : List.copyOf(controls))
            if (c.id.equals(focusId) && c.enabled) { c.action.run(); return true; }
        if (key == 264 || key == 265) {
            if (maxScroll > 0) {
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (key == 264 ? 1 : -1) * 28));
                return true;
            }
        }
        if (key == 266 || key == 267) { scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (key == 267 ? 1 : -1) * viewport.height)); return true; }
        if (key == 268) { scrollOffset = 0; return true; }
        if (key == 269) { scrollOffset = maxScroll; return true; }
        return false;
    }
    public boolean charTyped(int codePoint) {
        if (confirm.isOpen()) return true;
        if (library.type(codePoint)) return true;
        if (actions.type(codePoint)) return true;
        if (particleRates.type(codePoint)) return true;
        if (clientImport.isOpen()) return true;
        if (networkHub.isOpen()) return true;
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
        if (editingOption instanceof SliderOption && !(codePoint >= '0' && codePoint <= '9' || codePoint == '-')) return true;
        if (selectAll) { editBuffer = ""; cursor = 0; selectAll = false; }
        if (editBuffer.length() + text.length() <= (editingSearch ? 64 : editingOption instanceof ColorOption ? 8 : editingOption instanceof SliderOption ? 9 : 512)) {
            editBuffer = editBuffer.substring(0, cursor) + text + editBuffer.substring(cursor); cursor += text.length();
            if (editingSearch) applySearch();
        }
        return true;
    }
    private String inputDisplay() { return editBuffer.substring(0, cursor) + "|" + editBuffer.substring(cursor); }
    /** The edited text around the cursor, so a long value (Nametags nicknames) stays readable while typing. */
    private String inputWindow(LadsGraphics g, int width) {
        String text = inputDisplay();
        int start = 0, end = text.length();
        while (end - start > 1 && g.textWidth(text.substring(start, end)) > width) { if (end > cursor + 1) end--; else start++; }
        return text.substring(start, end);
    }
    private void startSearch() { editingKbSearch = false; editingSearch = true; editingOption = null; editBuffer = modsView ? modsSearch : searchQuery; cursor = editBuffer.length(); selectAll = false; focusId = modsView ? "mods-search" : "search"; }
    private void startKbSearch() { editingKbSearch = true; editingSearch = true; editingOption = null; editBuffer = kbSearch; cursor = editBuffer.length(); selectAll = false; focusId = "kb:search"; }
    private void applySearch() {
        if (editingKbSearch) { kbSearch = editBuffer; scrollOffset = 0; displayedScroll = 0; renderScroll = 0; }
        else if (modsView) { modsSearch = editBuffer; scrollOffset = 0; displayedScroll = 0; renderScroll = 0; }
        else setSearchQuery(editBuffer);
    }
    private void startEdit(Option o) {
        if (isServerLocked(o)) return;
        editingKbSearch = false;
        editingSearch = false;
        editingOption = o;
        editBuffer = o instanceof TextOption t ? t.getValue()
                   : o instanceof SliderOption s ? SliderOption.format(s.getOwnValue())
                   : String.format("%08X", ((ColorOption)o).getOwnColor());
        cursor = editBuffer.length();
        selectAll = true;
    }
    private boolean commitEdit() {
        if (isServerLocked(editingOption)) {
            editingOption = null;
            editingSearch = false;
            selectAll = false;
            return true;
        }
        String rejected = "";
        if (editingKbSearch) { kbSearch = editBuffer; editingKbSearch = false; }
        if (editingOption instanceof ColorOption c) {
            try { if (editBuffer.length() != 8) throw new NumberFormatException(); c.setColor(Integer.parseUnsignedInt(editBuffer, 16)); c.setUseGlobal(false); }
            catch (NumberFormatException e) { notice = "Use 8 hex digits: AARRGGBB. Esc cancels."; return false; }
        } else if (editingOption instanceof TextOption t) t.setValue(editBuffer);
        else if (editingOption instanceof SliderOption s) {
            // A whole number in range (rounded to the step); anything else leaves the value as it was and says so.
            var typed = s.parse(editBuffer);
            if (typed.isPresent()) s.setValue(typed.getAsDouble());
            else rejected = "Not a whole number from " + SliderOption.format(s.getMin()) + " to " + SliderOption.format(s.getMax()) + ": kept " + s.display();
        }
        // A number field keeps its value for anything else, and says so.
        if (rejected.isEmpty() && editingOption instanceof com.thelads.core.config.IntTextOption n && !n.valid(editBuffer))
            rejected = n.getName() + ": a whole number from " + n.getMin() + " to " + n.getMax() + ", so it stays " + n.getValue() + ".";
        if (editingOption != null) changed(detail);
        editingOption = null; editingSearch = false; selectAll = false; notice = rejected; return true;
    }
    private void category(String cat) { if (!finish()) return; currentCategory = cat; searchQuery = ""; detail = null; modsView = detailFromMods = false; modsPlan = null; catalogScrollOffset = 0; catalogDisplayedScroll = 0; catalogRenderScroll = 0; invalidate(); }
    private void invalidate() { filterDirty = true; scrollOffset = 0; displayedScroll = 0; renderScroll = 0; focusId = ""; }
    private void changed(Module m) {
        if (m != null) { m.touch(); m.setLastOpenedTime(System.currentTimeMillis()); } dirty = true; filterDirty = true; persist();
    }
    /** Sound Volumes has a slider per kind of sound: searching "rain" finds it. */
    private static boolean soundMatch(Module m, String query) {
        return !query.isEmpty() && com.thelads.core.client.SoundVolumes.NAME.equals(m.getName())
            && m.getOptions().stream().anyMatch(o -> o.getName().toLowerCase(Locale.ROOT).contains(query));
    }
    private void persist() { if (dirty) { ConfigManager.save(); dirty = false; } }
    private List<Option> activeOptions() {
        if (detail == null) return List.of();
        String query = searchQuery.toLowerCase(Locale.ROOT); // Sound Volumes found through a sound's name: only the matching sliders
        boolean sounds = soundMatch(detail, query) && !detail.getName().toLowerCase(Locale.ROOT).contains(query);
        return optionsOf(detail).stream().filter(o -> !sounds || o.getName().toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    /** The options a module's page shows, whichever page is open (search looks through them too). */
    private static List<Option> optionsOf(Module module) {
        return module.getOptions().stream().filter(o -> !(o instanceof PlayerActionOption))
            .filter(o -> !(module instanceof KillBannerModule banner && banner.pickerOption(o)))
            .filter(o -> !(module instanceof OldAnimationsModule animations && animations.hidden(o)))
            .filter(o -> !(module instanceof ToggleSprintModule toggles && toggles.hidden(o)))
            .filter(o -> !(module instanceof com.thelads.core.modules.ChatModule chat && chat.hidden(o)))
            .filter(o -> !(module instanceof com.thelads.core.modules.DynamicLightsModule lights && lights.hidden(o)))
            .filter(o -> !(module instanceof ComboSoundsModule combo && combo.hidden(o)))
            .filter(o -> !(module instanceof CrosshairModule crosshair && crosshair.hidden(o)))
            .filter(o -> !(module instanceof com.thelads.core.modules.SurvivalTweaksModule tweaks && tweaks.hidden(o)))
            .filter(o -> !(module instanceof com.thelads.core.modules.SurvivalGuardsModule guards && guards.hidden(o)))
            .filter(o -> !(module instanceof com.thelads.core.modules.StackRefillModule refill && refill.hidden(o)))
            .filter(o -> !(module instanceof com.thelads.core.modules.BetterF3Module f3 && f3.hidden(o)))
            .filter(o -> !(module instanceof com.thelads.core.modules.KeystrokesModule keys && keys.hidden(o)))
            .filter(o -> !(module instanceof com.thelads.core.modules.CosmeticsModule cosm && cosm.hidden(o)))
            .sorted((a, b) -> {
                if ("Ignore HUD FPS Cap".equals(a.getName())) return 1;
                if ("Ignore HUD FPS Cap".equals(b.getName())) return -1;
                return 0;
            }).toList();
    }
    private Option activeOption(String name) { return activeOptions().stream().filter(o -> o.getName().equals(name)).findFirst().orElse(null); }
    private boolean finish() { if (!commitEdit()) return false; persist(); return true; }
    public void openGlobalColors(){colorPicker.openGlobal();}
    public void openDisplayActions(){if(detail!=null)actions.open(detail.getOptions().stream().filter(o->o instanceof PlayerActionOption).map(o->(PlayerActionOption)o).toList(),()->changed(detail));}
    public void openModule(String name) {
        // QA scripts reach the catalog's pages with "settings page:HUD", "settings open:HUD/PvP" and "settings search:chroma".
        if (name.startsWith("page:")) { category(name.substring(5)); return; }
        if (name.startsWith("search:")) { setSearchQuery(name.substring(7)); return; }
        if (name.startsWith("open:")) {
            category(name.substring(5, name.indexOf('/')));
            if (!ModuleGroups.isOpen(name.substring(5), false)) ModuleGroups.toggle(name.substring(5));
            return;
        }
        Module m = ModuleManager.getInstance().getModule(name);
        if (m == null) {
            for (Module mod : ModuleManager.getInstance().getModules()) {
                if (mod.getName().equalsIgnoreCase(name)) {
                    m = mod;
                    break;
                }
            }
        }
        if (m != null) openDetails(m);
    }
    public void scrollBy(int pixels) { scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + pixels)); }
    public void scrollIntoView(Rect r) {
        if (viewport == null || r == null) return;
        if (r.y < viewport.y) {
            scrollOffset = Math.max(0, scrollOffset - (viewport.y - r.y));
        } else if (r.y + r.height > viewport.y + viewport.height) {
            scrollOffset = Math.min(maxScroll, scrollOffset + (r.y + r.height - (viewport.y + viewport.height)));
        }
    }
    /** QA (kill banner captures): the Kill Banner picker's skin search, as typed. */
    public void searchKillBanners(String query) { kbSearch = query; }
    private void openDetails(Module m) {
        if (!ModuleSupport.isBuiltIn(m.getName())) return;
        if (detail == null) {
            catalogScrollOffset = scrollOffset;
            catalogDisplayedScroll = displayedScroll;
            catalogRenderScroll = renderScroll;
        }
        detail = m;
        detailFromMods = false;
        m.setLastOpenedTime(System.currentTimeMillis());
        scrollOffset = 0;
        displayedScroll = 0;
        renderScroll = 0;
        draggingScrollbar = false;
        focusId = "back";
        notice = "";
    }
    private void back() {
        if (!finish()) return;
        detail = null;
        scrollOffset = catalogScrollOffset;
        displayedScroll = catalogDisplayedScroll;
        renderScroll = catalogRenderScroll;
        draggingScrollbar = false;
        focusId = "";
        if (detailFromMods) { detailFromMods = false; modsView = true; reloadMods(); }
    }
    private void leave(Runnable action) { if (!finish()) return; action.run(); }
    public void close() { refreshCapabilities(); leave(onClose); }
    public List<Module> getFilteredModules() {
        refreshCapabilities();
        if (!filterDirty) return filtered;
        String query = searchQuery.toLowerCase(Locale.ROOT);
        filtered = ModuleManager.getInstance().getModules().stream()
            .filter(m -> !ModuleManager.getInstance().isHiddenModule(m.getName()))
            .filter(m -> ModuleSupport.isBuiltIn(m.getName()))
            // Search looks everywhere: a player who types a name means that module, whatever page they are on.
            .filter(m -> !query.isEmpty() || currentCategory.equals(ModuleGroups.HOME) || currentCategory.equals(ModuleGroups.ALL) || categoryOf(m).equals(currentCategory))
            .filter(m -> !enabledOnly || m.isEnabled())
            .filter(m -> !favoritesOnly || m.isFavorite())
            .filter(m -> (m.getName() + " " + m.getDescription()).toLowerCase(Locale.ROOT).contains(query) || soundMatch(m, query))
            .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        optionHits = query.length() < 2 ? List.of() : findOptions(query);
        ownershipRevision = ModuleSupport.revision(); filterDirty = false; return filtered;
    }
    /** The settings whose name holds the query, each opening its module's page there. Sound Volumes is left out: its row shows just the matching sliders. */
    private List<Line> findOptions(String query) {
        return ModuleManager.getInstance().getModules().stream()
            .filter(m -> !ModuleManager.getInstance().isHiddenModule(m.getName()) && ModuleSupport.isBuiltIn(m.getName()) && !com.thelads.core.client.SoundVolumes.NAME.equals(m.getName()))
            .filter(m -> (!enabledOnly || m.isEnabled()) && (!favoritesOnly || m.isFavorite()))
            .flatMap(m -> optionsOf(m).stream().filter(o -> o.getName().toLowerCase(Locale.ROOT).contains(query)).map(o -> new Hit(m, o)))
            .sorted(Comparator.comparing((Hit hit) -> hit.option().getName(), String.CASE_INSENSITIVE_ORDER).thenComparing(hit -> hit.module().getName(), String.CASE_INSENSITIVE_ORDER))
            .limit(MAX_OPTION_HITS).map(hit -> (Line)hit).toList();
    }
}
