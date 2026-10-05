package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class TimeHudElement extends TextHudElement {
    private static final java.util.regex.Pattern TIME_24 = java.util.regex.Pattern.compile("(?:[01][0-9]|2[0-3]):[0-5][0-9]");

    public TimeHudElement() {
        super(50);
        this.x = 5;
        this.y = 185;
        this.width = 65;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        String time = g.getGame().getGameTime();
        if (time == null) time = "12:00";
        if (optBool("12-hour", false) && TIME_24.matcher(time).matches()) {
            int hour = Integer.parseInt(time.substring(0, 2));
            time = (hour % 12 == 0 ? 12 : hour % 12) + ":" + time.substring(3) + (hour < 12 ? " AM" : " PM");
        }
        boolean label = optBool("Show label", false);
        String text = (label ? "Time: " : "") + time;
        return text;
    }
}
