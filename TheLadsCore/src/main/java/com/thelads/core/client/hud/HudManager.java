package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import java.util.ArrayList;
import java.util.List;

public class HudManager {
    private static HudManager instance;
    private final List<HudElement> elements = new ArrayList<>();
    private ScoreboardHudElement scoreboardElement;

    private HudManager() {
        // Each element is linked to a Module by name. Visibility is driven by
        // that Module's enabled state (toggled in the settings screen), so every
        // overlay is off by default and shares the same toggle/persistence path.
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
        add(new PotionHudElement(), "Potions");
        add(new PaperdollHudElement(), "Paperdoll");
        add(new XaeroMinimapHudElement(), "XaeroWorldmap");
        
        // Scoreboard proxy — driven by GuiMixin; this element controls position in the HUD editor.
        scoreboardElement = new ScoreboardHudElement();
        add(scoreboardElement, "Scoreboard");
    }

    private void add(HudElement element, String moduleName) {
        element.setModuleName(moduleName);
        elements.add(element);
    }

    public static HudManager getInstance() {
        if (instance == null) {
            instance = new HudManager();
        }
        return instance;
    }

    public void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.gui.hud.isHidden) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        for (HudElement element : elements) {
            if (!element.isEnabled()) continue;

            // Clamp element position so it never leaves the screen
            int ex = net.minecraft.util.Mth.clamp(element.getX(), 0, Math.max(0, screenW - element.getRenderWidth()));
            int ey = net.minecraft.util.Mth.clamp(element.getY(), 0, Math.max(0, screenH - element.getRenderHeight()));

            float s = element.getScale();
            var pose = g.pose();
            if (s != 1.0f || ex != element.getX() || ey != element.getY()) {
                pose.pushMatrix();
                pose.translate((float) ex, (float) ey);
                if (s != 1.0f) {
                    pose.scale(s, s);
                    pose.translate((float) -element.getX(), (float) -element.getY());
                } else {
                    pose.translate((float) -element.getX(), (float) -element.getY());
                }
                element.render(g);
                pose.popMatrix();
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
