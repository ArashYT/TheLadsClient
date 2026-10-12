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
    /** The armor row's top edge, in GUI pixels above the bottom of the screen (before Raised moves the hotbar group). */
    private static final int ARMOR_TOP = 49;

    private ChatLift() {}

    public static boolean on() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption(OPTION) instanceof BoolOption option && option.get();
    }

    /**
     * GUI pixels chat moves up now.
     *
     * @param raised      what chat already moves up (Raised's Chat slider)
     * @param survivalHud whether the hearts and the armor row show (survival and adventure)
     * @param hotbar      how far Raised has moved the hotbar group, armor row included
     * @param gap         how far above the bottom of the screen vanilla ends its newest chat line (40; 1.8.9: 28)
     */
    public static int lift(int raised, boolean survivalHud, int hotbar, int gap) {
        return survivalHud && on() ? Math.max(raised, ARMOR_TOP + 1 + hotbar - gap) : raised;
    }
}
