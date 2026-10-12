package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * The Chat module's Above Armor Bar (1.9.1, on by default): in the survival HUD (hearts and the armor row above them) chat is drawn
 * higher, so its bottom sits just above the armor row and a message never covers the armor icons. Each version moves its chat by
 * what {@link #lift} gives, drawing, hovering and clicking alike (the same place Raised's Chat slider already moves it).
 */
public final class ChatLift {
    public static final String OPTION = "Above Armor Bar";
    /** The armor row's top edge above the bottom of the screen with one row of hearts, in GUI pixels (before Raised moves the hotbar group). */
    private static final int ARMOR_TOP = 49;

    private ChatLift() {}

    public static boolean on() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption(OPTION) instanceof BoolOption option && option.get();
    }

    /**
     * How many rows of hearts vanilla draws: ten hearts (20 points of health, absorption included) a row. The armor row stands above
     * them all, so it rises with each extra row (golden apples, Health Boost). Vanilla counts the larger of the maximum and the
     * current health, and the absorption rounded up.
     */
    public static int healthRows(float maxHealth, float health, float absorption) {
        return Math.max(1, (int) Math.ceil((Math.max(maxHealth, (float) Math.ceil(health)) + (float) Math.ceil(absorption)) / 20f));
    }

    /**
     * The armor row's top edge above the bottom of the screen for that many rows of hearts: vanilla spaces the rows 10 px apart,
     * closer together from the third row on (9, 8... down to 3), and the armor stands one row above the highest.
     */
    public static int armorTop(int rows) {
        int count = Math.max(1, rows);
        return ARMOR_TOP + (count - 1) * Math.max(10 - (count - 2), 3);
    }

    /**
     * GUI pixels chat moves up now.
     *
     * @param raised      what chat already moves up (Raised's Chat slider)
     * @param survivalHud whether the hearts and the armor row show (survival and adventure)
     * @param hotbar      how far Raised has moved the hotbar group, armor row included
     * @param gap         how far above the bottom of the screen vanilla ends its newest chat line (40; 1.8.9: 28)
     * @param rows        the rows of hearts now ({@link #healthRows})
     */
    public static int lift(int raised, boolean survivalHud, int hotbar, int gap, int rows) {
        return survivalHud && on() ? Math.max(raised, armorTop(rows) + 1 + hotbar - gap) : raised;
    }
}
