package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/** Totem Pop Counter: each player who used a totem lately and how many (TotemPops), one per line, newest kept for Duration. */
public class TotemPopHudElement extends TextHudElement {
    private final StringBuilder out = new StringBuilder();
    private String text = "";
    private int stamp = -1;

    public TotemPopHudElement() {
        super(70);
    }

    @Override
    protected String updateText(LadsGraphics g) {
        int size = TotemPops.size();
        if (size == 0) return editor ? "Notch: 2 pops\njeb_: 1 pop" : "";
        if (TotemPops.changes != stamp) { // rebuilt only when a pop changed the list
            out.setLength(0);
            for (int i = 0; i < size; i++) {
                if (i > 0) out.append('\n');
                int count = TotemPops.count(i);
                out.append(TotemPops.name(i)).append(": ").append(count).append(count == 1 ? " pop" : " pops");
            }
            text = out.toString();
            stamp = TotemPops.changes;
        }
        return text;
    }
}
