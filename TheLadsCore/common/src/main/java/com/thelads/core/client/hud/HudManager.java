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
    /** The last capped Lads HUD build, replayed on the frames between builds (see HudFrameCap). */
    private final List<java.util.function.Consumer<LadsGraphics>> cachedHud = new ArrayList<>();
    private int hudFrameCount = 0;
    private int measuredHudFps = 60;
    private long lastFpsMeasureTime = 0;

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
        add(new ToggleSprintHudElement(), "ToggleSprint");
        add(new ToggleSneakHudElement(), "ToggleSneak");
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
        if (!HudFrameCap.wholeHud && g.getGame() != null && g.getGame().isIngame() && HudFrameCap.enabled()) {
            // Capped: rebuild at the cap rate and draw the last build on every frame, so the HUD never blinks out.
            if (HudFrameCap.due(System.nanoTime(), g.getScaledWidth(), g.getScaledHeight())) {
                cachedHud.clear();
                recordHudFrame();
                renderElements(new RecordingGraphics(g, cachedHud));
            }
            for (var op : cachedHud) op.accept(g);
            return;
        }
        if (!HudFrameCap.wholeHud) HudFrameCap.reset();
        cachedHud.clear();
        recordHudFrame();
        renderElements(g);
    }

    private void renderElements(LadsGraphics g) {
        int screenW = g.getScaledWidth();
        int screenH = g.getScaledHeight();

        // Most players do not group HUDs. Keep the same measuring/clamping/draw order
        // without building maps, sets and stream pipelines for every rendered frame.
        if (HudSettings.getInstance().getGroups().isEmpty()) {
            for (var element : elements) {
                element.restoreSavedPosition();
                if (!element.isEnabled() || !element.isAvailable()) continue;
                var bounds = element.measureBounds(g, false);
                var delta = HudGroupLayout.clampDelta(bounds, 0, 0, screenW, screenH);
                var placed = HudGroupLayout.translate(bounds, delta);
                element.renderAt(g, placed.x(), placed.y(), false);
            }
            return;
        }
        var neededNames = new HashSet<String>();
        for (var element : elements) {
            // Loaded/profile-switched positions must also reach currently disabled HUDs.
            element.restoreSavedPosition();
            if (!element.isEnabled() || !element.isAvailable()) continue;
            neededNames.add(element.getModuleName());
            var group = HudSettings.getInstance().getGroupMembers(element.getModuleName());
            if (group != null) neededNames.addAll(group);
        }
        var measured = new LinkedHashMap<HudElement, HudGroupLayout.Rect>();
        for (HudElement element : elements) {
            if (!element.isAvailable() || !neededNames.contains(element.getModuleName())) continue;
            // Hidden members keep the same group geometry in gameplay and the editor. Preview
            // measurement reserves optional empty content; renderAt(false) never draws samples.
            boolean grouped = HudSettings.getInstance().getGroupMembers(element.getModuleName()) != null;
            measured.put(element, element.measureBounds(g, grouped));
        }
        HudGroupLayout.matchDockedWidths(measured);
        var placed = new LinkedHashMap<HudElement, HudGroupLayout.Rect>();
        for (var entry : measured.entrySet()) {
            if (placed.containsKey(entry.getKey())) continue;
            var names = HudSettings.getInstance().getGroupMembers(entry.getKey().getModuleName());
            var members = names == null ? List.of(entry.getKey()) : measured.keySet().stream()
                .filter(element -> names.contains(element.getModuleName())).toList();
            var group = HudGroupLayout.union(members.stream().map(measured::get).toList());
            var delta = HudGroupLayout.clampDelta(group, 0, 0, screenW, screenH);
            for (var member : members) placed.put(member, HudGroupLayout.translate(measured.get(member), delta));
        }
        // Keep original draw order even when nonadjacent elements belong to one rigid group.
        for (var element : measured.keySet()) {
            if (!element.isEnabled()) continue;
            var bounds = placed.get(element);
            element.renderAt(g, bounds.x(), bounds.y(), false);
        }
    }

    public List<HudElement> getElements() {
        return elements;
    }

    public ScoreboardHudElement getScoreboardElement() {
        return scoreboardElement;
    }
}
