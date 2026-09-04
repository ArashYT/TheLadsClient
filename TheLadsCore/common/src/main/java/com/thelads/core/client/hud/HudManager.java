package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.ArrayList;
import java.util.List;

public class HudManager {
    private static HudManager instance;
    private final List<HudElement> elements = new ArrayList<>();
    private ScoreboardHudElement scoreboardElement;

    private HudManager() {
        add(new FPSHudElement(), "FPS");
        add(new CoordinatesHudElement(), "Coordinates");
        add(new BiomeHudElement(), "Biome");
        add(new PingHudElement(), "PingHUD");
        add(new ArmorHudElement(), "ArmorHUD");
        add(new MemoryHudElement(), "Memory");
        add(new DirectionHudElement(), "Direction");
        add(new SpeedHudElement(), "Speed");
        add(new DayHudElement(), "Day");
        add(new TimeHudElement(), "Time");
        add(new HealthHudElement(), "Health");
        add(new HungerHudElement(), "Hunger");
        add(new XpHudElement(), "XP");
        add(new KeystrokesHudElement(), "Keystrokes");
        add(new CpsHudElement(), "CPS");
        add(new TexturePackHudElement(), "TexturePacks");
        add(new PotionHudElement(), "Potion Effects");
        add(new PaperdollHudElement(), "Paperdoll");
        add(new XaeroMinimapHudElement(), "XaeroWorldmap");

        scoreboardElement = new ScoreboardHudElement();
        add(scoreboardElement, "Scoreboard");
    }

    private void add(HudElement element, String moduleName) {
        element.setModuleName(moduleName);
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

        int screenW = g.getScaledWidth();
        int screenH = g.getScaledHeight();

        for (HudElement element : elements) {
            if (!element.isEnabled()) continue;

            int ex = Math.max(0, Math.min(element.getX(), Math.max(0, screenW - element.getRenderWidth())));
            int ey = Math.max(0, Math.min(element.getY(), Math.max(0, screenH - element.getRenderHeight())));

            float s = element.getScale();
            if (s != 1.0f || ex != element.getX() || ey != element.getY()) {
                g.pushPose();
                g.translate((float) ex, (float) ey);
                if (s != 1.0f) {
                    g.scale(s, s);
                    g.translate((float) -element.getX(), (float) -element.getY());
                } else {
                    g.translate((float) -element.getX(), (float) -element.getY());
                }
                element.render(g);
                g.popPose();
            } else {
                element.render(g);
            }
        }
    }

    public List<HudElement> getElements() {
        return elements;
    }

    public ScoreboardHudElement getScoreboardElement() {
        return scoreboardElement;
    }
}
