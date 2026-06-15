/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.Minecraft
 *  net.minecraft.util.Mth
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.hud.ArmorHudElement;
import com.thelads.core.client.hud.BiomeHudElement;
import com.thelads.core.client.hud.CoordinatesHudElement;
import com.thelads.core.client.hud.CpsHudElement;
import com.thelads.core.client.hud.DayHudElement;
import com.thelads.core.client.hud.DirectionHudElement;
import com.thelads.core.client.hud.FPSHudElement;
import com.thelads.core.client.hud.HealthHudElement;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HungerHudElement;
import com.thelads.core.client.hud.KeystrokesHudElement;
import com.thelads.core.client.hud.MemoryHudElement;
import com.thelads.core.client.hud.PingHudElement;
import com.thelads.core.client.hud.PotionHudElement;
import com.thelads.core.client.hud.ScoreboardHudElement;
import com.thelads.core.client.hud.SpeedHudElement;
import com.thelads.core.client.hud.TexturePackHudElement;
import com.thelads.core.client.hud.TimeHudElement;
import com.thelads.core.client.hud.VoiceChatHudElement;
import com.thelads.core.client.hud.XaeroMinimapHudElement;
import com.thelads.core.client.hud.XpHudElement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

public class HudManager {
    private static HudManager instance;
    private final List<HudElement> elements = new ArrayList<HudElement>();
    private ScoreboardHudElement scoreboardElement;

    private HudManager() {
        this.add(new FPSHudElement(), "FPS");
        this.add(new CoordinatesHudElement(), "Coordinates");
        this.add(new BiomeHudElement(), "Biome");
        this.add(new PingHudElement(), "PingHUD");
        this.add(new ArmorHudElement(), "ArmorHUD");
        this.add(new MemoryHudElement(), "Memory");
        this.add(new DirectionHudElement(), "Direction");
        this.add(new SpeedHudElement(), "Speed");
        this.add(new DayHudElement(), "Day");
        this.add(new TimeHudElement(), "Time");
        this.add(new HealthHudElement(), "Health");
        this.add(new HungerHudElement(), "Hunger");
        this.add(new XpHudElement(), "XP");
        this.add(new KeystrokesHudElement(), "Keystrokes");
        this.add(new CpsHudElement(), "CPS");
        this.add(new TexturePackHudElement(), "TexturePacks");
        this.add(new PotionHudElement(), "Potions");
        this.add(new XaeroMinimapHudElement(), "XaeroMinimap");
        this.add(new VoiceChatHudElement(), "VoiceChat");
        this.scoreboardElement = new ScoreboardHudElement();
        this.add(this.scoreboardElement, "Scoreboard");
    }

    private void add(HudElement element, String moduleName) {
        element.setModuleName(moduleName);
        this.elements.add(element);
    }

    public static HudManager getInstance() {
        if (instance == null) {
            instance = new HudManager();
        }
        return instance;
    }

    public void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.hideGui) {
            return;
        }
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        for (HudElement element : this.elements) {
            if (!element.isEnabled()) continue;
            int ex = Mth.clamp((int)element.getX(), (int)0, (int)Math.max(0, screenW - element.getRenderWidth()));
            int ey = Mth.clamp((int)element.getY(), (int)0, (int)Math.max(0, screenH - element.getRenderHeight()));
            float s = element.getScale();
            Matrix3x2fStack pose = g.pose();
            if (s != 1.0f || ex != element.getX() || ey != element.getY()) {
                pose.pushMatrix();
                pose.translate(ex, ey);
                if (s != 1.0f) {
                    pose.scale(s, s);
                    pose.translate(-element.getX(), -element.getY());
                } else {
                    pose.translate(-element.getX(), -element.getY());
                }
                element.render(g);
                pose.popMatrix();
                continue;
            }
            element.render(g);
        }
    }

    public List<HudElement> getElements() {
        return this.elements;
    }

    public ScoreboardHudElement getScoreboardElement() {
        return this.scoreboardElement;
    }
}

