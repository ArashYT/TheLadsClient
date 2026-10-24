package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Title Tweaker (Lunar's Titles, OneClient's title auto-scale, Feather's Title Tweaker): the big title and subtitle in the middle of the
 * screen moved and scaled, shrunk to fit the screen, copied into chat or not shown at all. Visual only: the copy is a local chat line.
 * The adapters read what {@link #refresh} cached.
 */
public final class TitleTweakerModule extends Module {
    public static final String NAME = "TitleTweaker";

    public final SliderOption scale = addOption(new SliderOption("Scale", 100, 25, 200, 5).percent());
    public final SliderOption x = addOption(new SliderOption("X Offset", 0, -400, 400, 1));
    public final SliderOption y = addOption(new SliderOption("Y Offset", 0, -400, 400, 1));
    public final BoolOption fit = addOption(new BoolOption("Fit to Screen", true));
    public final BoolOption toChat = addOption(new BoolOption("Send to Chat", false));
    public final BoolOption hide = addOption(new BoolOption("Hide on Screen", false));

    /** Cached each tick. */
    public boolean on, fits, chat, hidden;
    public float size = 1;
    public int dx, dy;
    private String lastTitle, lastSubtitle;

    public TitleTweakerModule() {
        super(NAME, "Move and scale titles, fit them to the screen, copy them into chat or hide them.");
    }

    public void refresh() {
        on = isEnabled();
        size = (float) scale.getValue() / 100;
        dx = x.getIntValue();
        dy = y.getIntValue();
        fits = fit.get();
        chat = on && toChat.get();
        hidden = on && hide.get();
    }

    /**
     * The scale a title is drawn at: the chosen one, shrunk so the title (drawn 4x) and subtitle (2x) stay inside the screen with a
     * 10 px margin each side when Fit to Screen is on. Widths are in GUI pixels at 1x.
     */
    public static float drawScale(float scale, boolean fit, int titleWidth, int subtitleWidth, int screenWidth) {
        if (!fit) return scale;
        int widest = Math.max(titleWidth * 4, subtitleWidth * 2);
        if (widest <= 0) return scale;
        return Math.min(scale, (screenWidth - 20) / (float) widest);
    }

    /** A title the server sent: the chat line it becomes with Send to Chat (null when there is none, or it repeats the last one). */
    public String chatLine(String text, boolean subtitle) {
        if (!chat || text == null || text.isEmpty()) return null;
        if (text.equals(subtitle ? lastSubtitle : lastTitle)) return null; // servers resend titles every tick for animations
        if (subtitle) lastSubtitle = text; else lastTitle = text;
        return text;
    }
}
