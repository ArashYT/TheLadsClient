package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

/**
 * Hearts (Feather's Hearts restyle): vanilla's own heart and armour icons (hardcore ones in a hardcore world or with Vanilla HUD's
 * Always Hardcore Hearts), absorption as golden hearts condensed to one row, armour above, toughness as a number.
 */
public class HeartsHudElement extends HudElement {
    /** Vanilla's 9x9 icons: the GUI atlas sprite (1.20.2 and later) and the same icon's u in textures/gui/icons.png (v 0, hardcore 45). */
    private static final String[] HEART = {"hud/heart/container", "hud/heart/full", "hud/heart/half", "hud/heart/absorbing_full", "hud/heart/absorbing_half"};
    private static final String[] HARDCORE = {"hud/heart/container_hardcore", "hud/heart/hardcore_full", "hud/heart/hardcore_half",
        "hud/heart/absorbing_hardcore_full", "hud/heart/absorbing_hardcore_half"};
    private static final int[] HEART_U = {16, 52, 61, 160, 169};
    private static final int CONTAINER = 0, FULL = 1, HALF = 2, ABSORBING_FULL = 3, ABSORBING_HALF = 4;
    private static final String[] ARMOR = {"hud/armor_empty", "hud/armor_half", "hud/armor_full"};
    private static final int[] ARMOR_U = {16, 25, 34};

    // What prepareRender measured, drawn by render.
    private float hp, maxHp, absorption, toughness;
    private int armor, absorbingHearts, tier;
    private boolean armorRow, hardcore, absorbingHalf;
    private String toughnessText = "", tierText = "";
    private int shownToughness = -1, shownTier = -1;

    public HeartsHudElement() {
        this.x = 20;
        this.y = 160;
        this.width = 85;
        this.height = 32;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        LadsGameBridge game = g.getGame();
        hp = editor || game == null ? 18 : game.getHealth();
        maxHp = editor || game == null ? 20 : game.getMaxHealth();
        absorption = editor || game == null ? 4 : Math.max(0, game.getAbsorption());
        armor = editor || game == null ? 20 : game.getArmorValue();
        toughness = editor || game == null ? 8 : game.getToughness();
        hardcore = game != null && !editor && game.isHardcore() || HudExtras.VANILLA.hardcoreHearts;
        armorRow = optBool("Show armor", true);
        // Absorption in half hearts: one golden row, the last 1-10 hearts of it when condensed (the rows above get a "x2" and up).
        int points = (int) Math.ceil(absorption), hearts = (points + 1) / 2;
        boolean condense = optBool("Condense absorption", true);
        absorbingHearts = condense ? calculateAbsorptionHearts(absorption) : Math.min(10, hearts);
        tier = condense ? calculateAbsorptionTier(absorption) : 0;
        absorbingHalf = points % 2 == 1 && (condense || hearts <= 10);
        int shown = armorRow && optBool("Show toughness", true) && toughness > 0 ? (int) toughness : 0;
        if (shown != shownToughness) { // texts only when they change, not every frame
            shownToughness = shown;
            toughnessText = shown > 0 ? Integer.toString(shown) : "";
        }
        if (tier != shownTier) {
            shownTier = tier;
            tierText = tier > 0 ? "x" + (tier + 1) : "";
        }
        int rows = 1 + (armorRow ? 1 : 0) + (absorbingHearts > 0 ? 1 : 0);
        width = 85 + Math.max(toughnessText.isEmpty() ? 0 : g.textWidth(toughnessText) + 3, tierText.isEmpty() ? 0 : g.textWidth(tierText) + 3);
        height = rows * 10 + 2;
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
        drawBackground(g);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        String[] hearts = hardcore ? HARDCORE : HEART;
        int v = hardcore ? 45 : 0, row = y + 2, left = x + 2;
        // Vanilla's order from the top: armour, absorption, health.
        if (armorRow) {
            for (int i = 0; i < 10; i++) {
                int icon = armor >= i * 2 + 2 ? 2 : armor == i * 2 + 1 ? 1 : 0;
                g.drawGuiIcon(ARMOR[icon], ARMOR_U[icon], 9, left + i * 8, row, 9, 9);
            }
            if (!toughnessText.isEmpty()) g.drawText(toughnessText, left + 83, row + 1, 0xFF55FFFF, shadow);
            row += 10;
        }
        if (absorbingHearts > 0) {
            for (int i = 0; i < absorbingHearts; i++) {
                int icon = absorbingHalf && i == absorbingHearts - 1 ? ABSORBING_HALF : ABSORBING_FULL;
                g.drawGuiIcon(hearts[CONTAINER], HEART_U[CONTAINER], v, left + i * 8, row, 9, 9);
                g.drawGuiIcon(hearts[icon], HEART_U[icon], v, left + i * 8, row, 9, 9);
            }
            if (!tierText.isEmpty()) g.drawText(tierText, left + 83, row + 1, 0xFFFFDD44, shadow);
            row += 10;
        }
        int containers = Math.max(1, Math.min(10, (int) Math.ceil(maxHp / 2))), points = (int) Math.ceil(hp);
        for (int i = 0; i < containers; i++) {
            g.drawGuiIcon(hearts[CONTAINER], HEART_U[CONTAINER], v, left + i * 8, row, 9, 9);
            if (points >= i * 2 + 2) g.drawGuiIcon(hearts[FULL], HEART_U[FULL], v, left + i * 8, row, 9, 9);
            else if (points == i * 2 + 1) g.drawGuiIcon(hearts[HALF], HEART_U[HALF], v, left + i * 8, row, 9, 9);
        }
    }
}
