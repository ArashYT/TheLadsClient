package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge.PotionEffectInfo;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;
import java.util.List;
import java.util.regex.Pattern;

public class PotionHudElement extends HudElement {
    private static final List<PotionEffectInfo> SAMPLE_POTIONS = List.of(
        new PotionEffectInfo("Speed II", "1:30", 0, "speed", 0x7CAFC6),
        new PotionEffectInfo("Strength I", "0:45", 9, "strength", 0x932423),
        new PotionEffectInfo("Regeneration", "0:22", 6, "regeneration", 0xCD5CAB)
    );

    private LadsGraphics preparedGraphics;
    private List<PotionEffectInfo> activePotions = List.of();
    private List<String> legacyEffects = List.of();
    private static final Pattern DURATION_SUFFIX = Pattern.compile(" \\([0-9]+s\\)$");

    private final List<String> renderedLines = new java.util.ArrayList<>();
    private List<PotionEffectInfo> lastActivePotions = null;
    private List<String> lastLegacyEffects = null;
    private boolean lastShowDur = false;

    private static com.thelads.core.config.Module cachedModule;

    public PotionHudElement() {
        this.x = 10;
        this.y = 220;
        this.width = 90;
        this.height = 16;
    }

    /** Vanilla's top-right potion effect icons are hidden while the Lads Potion Effects module is enabled. */
    public static boolean shouldReplaceVanillaEffects() {
        var m = cachedModule;
        if (m == null) cachedModule = m = com.thelads.core.config.ModuleManager.getInstance().getModule("Potion Effects");
        return m != null && m.isEnabled();
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        preparedGraphics = g;
        activePotions = List.copyOf(g.getGame().getActivePotions());
        legacyEffects = List.copyOf(g.getGame().getActivePotionEffects());

        if (activePotions.isEmpty() && legacyEffects.isEmpty() && editor) {
            activePotions = SAMPLE_POTIONS;
        }

        boolean showDur = optBool("Show duration", true);
        boolean showIcon = optBool("Show icon", true);
        int iconSize = showIcon ? 14 : 0;
        int iconGap = showIcon ? 4 : 0;

        if (activePotions.isEmpty() && legacyEffects.isEmpty()) {
            renderedLines.clear();
            lastActivePotions = null;
            lastLegacyEffects = null;
            this.width = optBool("Show when empty", false) ? Math.max(90, g.textWidth("No Effects") + 12) : 90;
            this.height = Math.max(16, g.fontHeight() + 6);
            return;
        }

        int lineH = Math.max(iconSize, g.fontHeight()) + 3;
        int count = !activePotions.isEmpty() ? activePotions.size() : legacyEffects.size();
        this.height = count * lineH + 4;
        int maxW = 80;

        boolean needRebuild = showDur != lastShowDur;
        if (!needRebuild) {
            if (!activePotions.isEmpty()) {
                needRebuild = !activePotions.equals(lastActivePotions);
            } else {
                needRebuild = !legacyEffects.equals(lastLegacyEffects);
            }
        }

        if (needRebuild) {
            renderedLines.clear();
            lastShowDur = showDur;
            if (!activePotions.isEmpty()) {
                lastActivePotions = activePotions;
                lastLegacyEffects = null;
                for (PotionEffectInfo p : activePotions) {
                    String line = p.name() + (showDur && p.duration() != null && !p.duration().isEmpty() ? " (" + p.duration() + ")" : "");
                    renderedLines.add(line);
                }
            } else {
                lastActivePotions = null;
                lastLegacyEffects = legacyEffects;
                for (String eff : legacyEffects) {
                    String line = showDur ? eff : DURATION_SUFFIX.matcher(eff).replaceFirst("");
                    renderedLines.add(line);
                }
            }
        }

        for (String line : renderedLines) {
            maxW = Math.max(maxW, iconSize + iconGap + g.textWidth(line) + 8);
        }
        this.width = maxW;
    }

    @Override
    public void render(LadsGraphics g) {
        if (preparedGraphics != g) prepareRender(g, false);
        preparedGraphics = null;

        if (activePotions.isEmpty() && legacyEffects.isEmpty()) {
            if (optBool("Show when empty", false)) {
                drawBackground(g);
                drawCenteredText(g, "No Effects");
            }
            return;
        }
        renderInternal(g);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        if (activePotions.isEmpty() && legacyEffects.isEmpty()) {
            activePotions = SAMPLE_POTIONS;
            prepareRender(g, true);
        }
        renderInternal(g);
    }

    private void renderInternal(LadsGraphics g) {
        boolean showDur = optBool("Show duration", true);
        boolean showIcon = optBool("Show icon", true);
        int iconSize = showIcon ? 14 : 0;
        int iconGap = showIcon ? 4 : 0;
        int lineH = Math.max(iconSize, g.fontHeight()) + 3;
        drawBackground(g);

        int ty = y + 2;
        int color = resolveColor();
        boolean shadow = HudSettings.getInstance().isTextShadow();

        if (!activePotions.isEmpty()) {
            for (int i = 0; i < activePotions.size() && i < renderedLines.size(); i++) {
                PotionEffectInfo p = activePotions.get(i);
                int contentX = x + 4;
                if (showIcon) {
                    g.drawPotionIcon(p.effectId(), p.iconIndex(), contentX, ty + (lineH - iconSize) / 2, iconSize);
                    contentX += iconSize + iconGap;
                }
                String line = renderedLines.get(i);
                int textY = ty + (lineH - g.fontHeight()) / 2;
                g.drawText(line, contentX, textY, color, shadow);
                ty += lineH;
            }
        } else {
            for (int i = 0; i < legacyEffects.size() && i < renderedLines.size(); i++) {
                int contentX = x + 4;
                String line = renderedLines.get(i);
                int textY = ty + (lineH - g.fontHeight()) / 2;
                g.drawText(line, contentX, textY, color, shadow);
                ty += lineH;
            }
        }
    }
}
