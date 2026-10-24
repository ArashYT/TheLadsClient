package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;
import java.util.Locale;

public class HeartsHudElement extends HudElement {
    public HeartsHudElement() {
        this.x = 20;
        this.y = 160;
        this.width = 82;
        this.height = 20;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        boolean showArmor = optBool("Show armor", true);
        width = 82;
        height = showArmor ? 22 : 12;
    }

    public static int calculateAbsorptionHearts(float absorption) {
        if (absorption <= 0) return 0;
        int absHearts = (int) Math.ceil(absorption / 2.0f);
        return (absHearts - 1) % 10 + 1;
    }

    public static int calculateAbsorptionTier(float absorption) {
        if (absorption <= 0) return 0;
        int absHearts = (int) Math.ceil(absorption / 2.0f);
        return (absHearts - 1) / 10;
    }

    @Override
    public void render(LadsGraphics g) {
        renderHearts(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        renderHearts(g, true);
    }

    private void renderHearts(LadsGraphics g, boolean editor) {
        drawBackground(g);
        float hp = g.getGame().getHealth();
        float maxHp = g.getGame().getMaxHealth();
        float absorption = g.getGame().getAbsorption();
        int armor = g.getGame().getArmorValue();
        float toughness = g.getGame().getToughness();

        if (editor) {
            hp = 18.0f;
            maxHp = 20.0f;
            absorption = 4.0f;
            armor = 20;
            toughness = 8.0f;
        }

        boolean showArmor = optBool("Show armor", true);
        boolean showToughness = optBool("Show toughness", true);
        boolean condense = optBool("Condense absorption", true);
        boolean shadow = HudSettings.getInstance().isTextShadow();

        // Hearts row (height ~9)
        int heartY = y + 2;
        int heartsToDraw = 10;
        int fullHearts = (int) (hp / 2);
        boolean halfHeart = (hp % 2) >= 1;

        for (int i = 0; i < heartsToDraw; i++) {
            int hx = x + 2 + i * 8;
            // Background container (dark red outline)
            g.fill(hx, heartY, hx + 7, heartY + 7, 0x60000000);
            if (i < fullHearts) {
                g.fill(hx + 1, heartY + 1, hx + 6, heartY + 6, 0xFFFF2222);
            } else if (i == fullHearts && halfHeart) {
                g.fill(hx + 1, heartY + 1, hx + 4, heartY + 6, 0xFFFF2222);
            }
        }

        // Absorption overlay (condensed)
        if (absorption > 0) {
            int absTier = calculateAbsorptionTier(absorption);
            int absColor = switch (absTier) {
                case 1 -> 0xFFFF8800; // orange
                case 2 -> 0xFF00DDFF; // cyan
                default -> 0xFFFFCC00; // gold
            };
            int absHearts = condense ? calculateAbsorptionHearts(absorption) : (int) Math.min(10, Math.ceil(absorption / 2.0f));
            for (int i = 0; i < absHearts; i++) {
                int hx = x + 2 + i * 8;
                g.fill(hx + 2, heartY + 2, hx + 5, heartY + 5, absColor);
            }
        }

        // Armor & Toughness row
        if (showArmor) {
            int armorY = y + 11;
            int armorIcons = Math.min(10, armor / 2);
            for (int i = 0; i < 10; i++) {
                int ax = x + 2 + i * 8;
                g.fill(ax, armorY, ax + 7, armorY + 7, 0x60000000);
                if (i < armorIcons) {
                    g.fill(ax + 1, armorY + 1, ax + 6, armorY + 6, 0xFFCCCCCC);
                }
            }
            if (showToughness && toughness > 0) {
                // Toughness accent diamond dots on top of armor icons
                int toughIcons = Math.min(10, (int) (toughness / 2));
                for (int i = 0; i < toughIcons; i++) {
                    int ax = x + 2 + i * 8;
                    g.fill(ax + 2, armorY + 2, ax + 5, armorY + 5, 0xFF33CCFF);
                }
            }
        }
    }
}
