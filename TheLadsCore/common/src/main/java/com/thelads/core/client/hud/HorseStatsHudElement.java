package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.Locale;

/** Horse Stats: jump height, speed and health of the horse you ride or look at (HudExtras.horse*, written by each version's tick). */
public class HorseStatsHudElement extends TextHudElement {
    private String text = "";
    private float jump = -1, speed = -1, health = -1, max = -1;
    private int options = -1;

    public HorseStatsHudElement() {
        super(70);
    }

    @Override
    protected String updateText(LadsGraphics g) {
        boolean shown = HudExtras.horseShown;
        if (!shown && !editor) return "";
        float j = shown ? HudExtras.horseJump : 3.6f, s = shown ? HudExtras.horseSpeed : 11.4f;
        float h = shown ? HudExtras.horseHealth : 26, m = shown ? HudExtras.horseMax : 26;
        int o = (optBool("Show Jump", true) ? 1 : 0) | (optBool("Show Speed", true) ? 2 : 0) | (optBool("Show Health", true) ? 4 : 0)
            | optCycle("Speed Unit", 0) << 3;
        if (j != jump || s != speed || h != health || m != max || o != options) {
            text = text(j, s, h, m, o);
            jump = j;
            speed = s;
            health = h;
            max = m;
            options = o;
        }
        return text;
    }

    /** One line per shown stat; options: bit 0 jump, 1 speed, 2 health, bits 3+ the speed unit (0 blocks/s, 1 km/h). */
    static String text(float jump, float speed, float health, float max, int options) {
        StringBuilder out = new StringBuilder();
        if ((options & 1) != 0) out.append(String.format(Locale.ROOT, "Jump: %.2f blocks", jump));
        if ((options & 2) != 0) {
            if (out.length() > 0) out.append('\n');
            out.append(options >> 3 == 1 ? String.format(Locale.ROOT, "Speed: %.1f km/h", speed * 3.6f)
                : String.format(Locale.ROOT, "Speed: %.2f blocks/s", speed));
        }
        if ((options & 4) != 0) {
            if (out.length() > 0) out.append('\n');
            out.append(String.format(Locale.ROOT, "Health: %.1f/%.0f", health / 2, max / 2)).append(DamageNumbers.HEART);
        }
        return out.toString();
    }
}
