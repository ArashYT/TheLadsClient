package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;

public class HudManager {
    private static HudManager instance;
    private final List<HudElement> elements = new ArrayList<>();
    private ScoreboardHudElement scoreboardElement;
    /** QA only (HudLayoutQa): runs once after the next Lads HUD frame, with its graphics in Lads HUD units. */
    public static volatile java.util.function.Consumer<LadsGraphics> afterRender;
    /** Where each HUD was last drawn, in the Lads HUD's units (QA and tests). */
    private final java.util.Map<String, HudGroupLayout.Rect> lastPlaced = new java.util.HashMap<>();
    /** The last capped Lads HUD build, replayed on the frames between builds (see HudFrameCap). */
    private final List<java.util.function.Consumer<LadsGraphics>> cachedHud = new ArrayList<>();
    private int hudFrameCount = 0;
    private int measuredHudFps = 60;
    private long lastFpsMeasureTime = 0;
    /** HudCover: the HUDs the last draw left out because the tab list or the debug text covers them; all of them under F3's "Hide Lads HUD". */
    private final java.util.Set<String> covered = new HashSet<>();
    private boolean allCovered;
    /** A HUD was covered or uncovered in the last draw: what follows it (isCovered) drew before it knew, so the next frame builds again. */
    private boolean coverMoved;
    /** The game and one vanilla GUI unit in Lads HUD units, as last drawn (HudCover's boxes are in GUI units). */
    private com.thelads.core.client.bridge.LadsGameBridge game;
    private double unit = 1;

    public int getMeasuredHudFps() {
        return measuredHudFps > 0 ? measuredHudFps : HudSettings.getInstance().getHudFpsLimit();
    }

    public void recordHudFrame() {
        hudFrameCount++;
        long now = System.currentTimeMillis();
        if (now - lastFpsMeasureTime >= 1000) {
            measuredHudFps = hudFrameCount;
            hudFrameCount = 0;
            lastFpsMeasureTime = now;
        }
    }

    private HudManager() {
        add(new FPSHudElement(), "FPS");
        add(new BossBarHudElement(), "BossBar");
        add(new CoordinatesHudElement(), "Coordinates");
        add(new BiomeHudElement(), "Biome");
        add(new PingHudElement(), "PingHUD");
        add(new ArmorHudElement(), "ArmorHUD");
        add(new MemoryHudElement(), "Memory");
        add(new DirectionHudElement(), "Direction");
        add(new SpeedHudElement(), "Speed");
        add(new DayHudElement(), "Day");
        add(new TimeHudElement(), "Time");
        for (String name : List.of("Clock", "Stopwatch", "ItemCounter", "ReachDisplay", "ServerAddress", "PortalCoordinates"))
            add(new ToolsHudElement(), name);
        add(new HealthHudElement(), "Health");
        add(new HungerHudElement(), "Hunger");
        add(new XpHudElement(), "XP");
        add(new KeystrokesHudElement(), "Keystrokes");
        add(new CpsHudElement(), "CPS");
        add(new TexturePackHudElement(), "TexturePacks");
        add(new PotionHudElement(), "Potion Effects");
        add(new PaperdollHudElement(), "Paperdoll");
        add(new XaeroMinimapHudElement(), "Minimap");
        add(new VoiceChatHudElement(), "Voice Chat");
        add(new VoiceGroupHudElement(), "Voice Chat Group");

        scoreboardElement = new ScoreboardHudElement();
        add(scoreboardElement, "Scoreboard");
        add(new ToggleSprintHudElement(), com.thelads.core.modules.ToggleSprintModule.NAME);
        add(new ActionBarHudElement(), ActionBar.MODULE);
        add(new SessionStatsHudElement(), "Session Stats");
        add(new BedWarsHeightHudElement(), "Bed Wars Height Limit");
    }

    private void add(HudElement element, String moduleName) {
        element.setModuleName(moduleName);
        element.useOrganizedDefaults();
        elements.add(element);
    }

    public static synchronized HudManager getInstance() {
        if (instance == null) {
            instance = new HudManager();
        }
        return instance;
    }

    public void render(LadsGraphics g) {
        if (g == null || (g.getGame() != null && g.getGame().isHudHidden())) return;
        game = g.getGame();
        // F3 hides the Lads HUD modules (Better F3's "Hide Lads HUD", on by default).
        if (hidesAll()) {
            allCovered = true;
            return;
        }
        // The Lads HUD's own GUI scale (HudScale): laid out in its units, drawn under one pose scale.
        LadsGraphics hud = HudScale.units(g);
        unit = hud.hudUnit();
        if (hud == g) renderScaled(g);
        else {
            g.pushPose();
            try {
                float scale = (float) (1 / hud.hudUnit());
                g.scale(scale, scale);
                renderScaled(hud);
            } finally {
                g.popPose();
            }
        }
        if (HudFrameCap.exempt != Boolean.TRUE) ServerRulesNotice.render(g); // once per frame, outside the HUD caches
        var qa = afterRender;
        if (qa != null) { afterRender = null; qa.accept(hud); }
    }

    private void renderScaled(LadsGraphics g) {
        if (HudFrameCap.exempt == Boolean.TRUE) { // a version's HUD cache (HudCache1201): the HUDs that ignore the cap, every frame
            renderElements(g, Boolean.TRUE);
            return;
        }
        if (!HudFrameCap.wholeHud && g.getGame() != null && g.getGame().isIngame() && HudFrameCap.enabled()) {
            // Capped: rebuild at the cap rate and draw the last build on every frame, so the HUD never blinks out.
            long now = System.nanoTime();
            if (HudFrameCap.due(now, g.getScaledWidth(), g.getScaledHeight())) {
                cachedHud.clear();
                recordHudFrame();
                renderElements(new RecordingGraphics(g, cachedHud), Boolean.FALSE);
                HudFrameCap.built(now);
            }
            for (var op : cachedHud) op.accept(g);
            renderElements(g, Boolean.TRUE);
            return;
        }
        if (!HudFrameCap.wholeHud) HudFrameCap.reset();
        cachedHud.clear();
        recordHudFrame();
        renderElements(g, HudFrameCap.exempt);
    }

    private boolean hidesAll() {
        return game != null && com.thelads.core.modules.BetterF3Module.hidesLadsHud(game.isDebugScreenOpen());
    }

    /** Whether a HUD drawn at this box (Lads HUD units) is under the tab list or the debug text now (HudCover). */
    private boolean covered(HudGroupLayout.Rect at) {
        return HudCover.covered(at.x() / unit, at.y() / unit, at.right() / unit, at.bottom() / unit, game != null && game.isDebugScreenOpen());
    }

    /**
     * Whether the tab list or the debug text now covers or uncovers a HUD where it was last drawn (HudFrameCap.due): a capped HUD is
     * then built again at once, so a HUD goes and comes back on the frame the tab list or F3 does, not one cached build later.
     */
    public boolean coverChanged() {
        if (game == null) return false;
        if (coverMoved) {
            coverMoved = false;
            return true;
        }
        boolean all = hidesAll();
        if (all || allCovered) return all != allCovered;
        if (covered.isEmpty() && HudCover.isEmpty() && !game.isDebugScreenOpen()) return false;
        for (var element : elements) {
            var at = lastPlaced.get(element.getModuleName());
            if (at != null && element.isEnabled() && element.isAvailable() && covered(at) != covered.contains(element.getModuleName())) return true;
        }
        return false;
    }

    /** Whether the last draw left this HUD out under the tab list or the debug text: what another mod draws for it (Xaero's map) follows. */
    public boolean isCovered(String name) {
        return allCovered || covered.contains(name);
    }

    /** QA: the HUDs the last draw left out under the tab list or the debug text. */
    public java.util.Set<String> coveredHuds() {
        return new java.util.TreeSet<>(covered);
    }

    /** Draws a HUD where the layout put it, unless the tab list or the debug text covers it there. */
    private void draw(LadsGraphics g, HudElement element, HudGroupLayout.Rect at) {
        element.placed(g, at);
        lastPlaced.put(element.getModuleName(), at);
        String name = element.getModuleName();
        if (covered(at)) {
            if (name != null) coverMoved |= covered.add(name);
            return;
        }
        if (name != null) coverMoved |= covered.remove(name);
        element.renderAt(g, at.x(), at.y(), false);
    }

    private boolean isExempt(HudElement element) {
        String name = element.getModuleName();
        if (name == null) return false;
        if (HudSettings.getInstance().isModuleExempt(name)) return true;
        var mod = com.thelads.core.config.ModuleManager.getInstance().getModule(name);
        if (mod instanceof com.thelads.core.modules.HudModule hudMod && hudMod.isIgnoreFpsCap()) return true;
        if (mod != null && mod.getOption("Ignore HUD FPS Cap") instanceof com.thelads.core.config.BoolOption opt && opt.get()) return true;
        return false;
    }

    /**
     * A version's own capped build of the Lads HUD (1.8.9's framebuffer cache, HudCache189): drawn into g, with what it draws reported
     * to HudFrameCap so animations raise the build rate as on the recorded path.
     */
    public void renderMarked(LadsGraphics g, long now) {
        List<java.util.function.Consumer<LadsGraphics>> ops = new ArrayList<>();
        RecordingGraphics recording = new RecordingGraphics(g, ops);
        render(recording);
        markedBounds = recording.bounds();
        for (var op : ops) op.accept(g);
        HudFrameCap.built(now);
    }

    private List<float[]> markedBounds;

    /** Disjoint boxes {minX, minY, maxX, maxY} (g's units) around what the last renderMarked drew, null when unknown: the parts of
     *  the screen a cache must composite. */
    public List<float[]> markedBounds() {
        return markedBounds;
    }

    private void renderElements(LadsGraphics g, Boolean exemptFilter) {
        int screenW = g.getScaledWidth();
        int screenH = g.getScaledHeight();
        HudSettings settings = HudSettings.getInstance();
        allCovered = false;

        // Most players neither group nor move their HUDs. Keep the same measuring/clamping/draw order
        // without building maps, sets and stream pipelines for every rendered frame.
        boolean legacy = false;
        if (settings.getGroups().isEmpty() && settings.getAnchors().isEmpty()) {
            for (var element : elements) legacy |= element.isAvailable() && HudAnchors.legacy(element);
            if (!legacy) {
                for (var element : elements) {
                    element.restoreSavedPosition();
                    if (!element.isEnabled() || !element.isAvailable()) continue;
                    if (exemptFilter != null && isExempt(element) != exemptFilter) continue;
                    var bounds = element.measureBounds(g, false);
                    var delta = HudGroupLayout.clampDelta(bounds, 0, 0, screenW, screenH);
                    draw(g, element, HudGroupLayout.translate(bounds, delta));
                }
                return;
            }
        }
        var hotbar = HudAnchors.hotbar(g);
        var neededNames = new HashSet<String>();
        for (var element : elements) {
            // Loaded/profile-switched positions must also reach currently disabled HUDs.
            element.restoreSavedPosition();
            legacy |= element.isAvailable() && HudAnchors.legacy(element);
            if (!element.isEnabled() || !element.isAvailable()) continue;
            // The capped build and the every-frame pass of exempt HUDs each measure only their own HUDs (and what those need).
            if (exemptFilter != null && isExempt(element) != exemptFilter) continue;
            neededNames.add(element.getModuleName());
            var group = settings.getGroupMembers(element.getModuleName());
            if (group != null) neededNames.addAll(group);
            // What it is docked to is laid out too (a switched-off holder still keeps its room).
            for (String to = holder(element.getModuleName()); to != null && neededNames.add(to); to = holder(to)) {}
        }
        if (legacy) adopt(g, screenW, screenH, hotbar);
        var measured = new LinkedHashMap<HudElement, HudGroupLayout.Rect>();
        for (HudElement element : elements) {
            if (!element.isAvailable() || !neededNames.contains(element.getModuleName())) continue;
            // Hidden members keep the same group geometry in gameplay and the editor. Preview
            // measurement reserves optional empty content; renderAt(false) never draws samples.
            boolean grouped = settings.getGroupMembers(element.getModuleName()) != null;
            measured.put(element, element.measureBounds(g, grouped));
        }
        HudAnchors.place(measured, screenW, screenH, hotbar);
        if (!settings.getGroups().isEmpty()) HudGroupLayout.matchDockedWidths(measured);
        HudAnchors.clamp(measured, screenW, screenH);
        // Keep original draw order even when nonadjacent elements belong to one rigid group.
        for (var entry : measured.entrySet()) {
            var element = entry.getKey();
            if (!element.isEnabled()) continue;
            if (exemptFilter != null && isExempt(element) != exemptFilter) continue;
            draw(g, element, entry.getValue());
        }
    }

    /** The HUD a saved anchor docks this one to, or null (the screen, the hotbar). */
    private static String holder(String name) {
        var anchor = HudSettings.getInstance().getAnchor(name);
        return anchor == null || anchor.to() == null || anchor.to().equals(HudSettings.HOTBAR) ? null : anchor.to();
    }

    /**
     * Anchors positions saved without one where they show now (HudAnchors.adopt), once. Docking is judged at the editor's
     * sizes (sample content), as the layout was arranged there.
     */
    private void adopt(LadsGraphics g, int screenW, int screenH, HudGroupLayout.Rect hotbar) {
        var shown = new LinkedHashMap<HudElement, HudGroupLayout.Rect>();
        for (var element : elements) if (element.isAvailable() && (element.isEnabled() || HudAnchors.legacy(element))) shown.put(element, element.measureBounds(g, true));
        HudAnchors.place(shown, screenW, screenH, hotbar);
        HudAnchors.clamp(shown, screenW, screenH);
        HudAnchors.adopt(shown, HudElement::isEnabled, g);
    }

    /** Where a HUD was drawn last (Lads HUD units), or null; a HUD switched off since keeps its old entry. */
    public HudGroupLayout.Rect lastBounds(String name) {
        return lastPlaced.get(name);
    }

    public List<HudElement> getElements() {
        return elements;
    }

    public ScoreboardHudElement getScoreboardElement() {
        return scoreboardElement;
    }
}
