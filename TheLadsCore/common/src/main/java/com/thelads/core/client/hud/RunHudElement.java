package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.ladsapi.minigame.Caps;
import com.thelads.core.client.ladsapi.minigame.Fmt;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.client.ladsapi.minigame.RunState;
import com.thelads.core.config.HudSettings;

/**
 * The parkour / speedrun HUD (Lads API, PROTOCOL.md section 19): course name, the running clock, checkpoint n/N, the split delta
 * for 3 s after a checkpoint (green ahead of the best, red behind) and the finish summary. The server is the timer authority; this
 * only shows what it sent.
 */
public class RunHudElement extends HudElement {
    public static final String NAME = "Run Timer";
    private static final int W = 110, RUN_H = 46, FINISH_H = 42, GREEN = 0xFF55FF55, RED = 0xFFFF5555;

    public RunHudElement() {
        this.moduleName = NAME;
        this.width = W;
        this.height = RUN_H;
    }

    private boolean live(long now) {
        return Caps.declared(Caps.RUN) && Minigame.run.active(now);
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        long now = Minigame.now();
        width = live(now) || editor ? W : 1;
        height = live(now) ? heightNow(now) : editor ? RUN_H : 1;
    }

    private static int heightNow(long now) {
        RunState r = Minigame.run;
        return r.finish(now) != null ? FINISH_H : RUN_H;
    }

    @Override
    public void render(LadsGraphics g) {
        long now = Minigame.now();
        if (!live(now)) return;
        RunState r = Minigame.run;
        String name = r.name().isEmpty() ? r.course() : r.name();
        draw(g, name, r.elapsedMs(now), r.reached(), r.checkpoints(), r.split(now), r.finish(now), now);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        long now = Minigame.now();
        if (live(now)) {
            render(g);
            return;
        }
        draw(g, "Easy 1", 41_250, 3, 10, new RunState.Split(2, 41_250, -420, true), null, now);
    }

    private void draw(LadsGraphics g, String name, long elapsed, int reached, int total, RunState.Split split, RunState.Finish finish,
                      long now) {
        int h = finish != null ? FINISH_H : RUN_H;
        width = W;
        height = h;
        int bg = resolveBackground();
        if ((bg & 0xFF000000) != 0) g.fill(x, y, x + W, y + h, bg);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        int tx = x + 5, ty = y + 4;
        g.drawText(name, tx, ty, 0xFFCCCCCC, shadow);
        if (finish != null) {
            String t = Fmt.runTime(finish.timeMs());
            g.drawText("FINISH", x + W - 5 - g.textWidth("FINISH"), ty, 0xFFFFD84A, shadow);
            g.drawText(t, tx, ty + 11, 0xFFFFFFFF, shadow);
            if (finish.hasDelta()) {
                String d = Fmt.delta(finish.deltaMs());
                g.drawText(d, x + W - 5 - g.textWidth(d), ty + 11, finish.ahead() ? GREEN : RED, shadow);
                g.drawText(finish.ahead() ? "New best pace" : "Behind your best", tx, ty + 24, 0xFFAAAAAA, shadow);
            } else {
                g.drawText("No best to compare", tx, ty + 24, 0xFFAAAAAA, shadow);
            }
            return;
        }
        g.drawText(Fmt.runTime(elapsed), tx, ty + 11, 0xFFFFFFFF, shadow);
        if (total > 0) {
            String cp = "CP " + Math.min(reached, total) + "/" + total;
            g.drawText(cp, x + W - 5 - g.textWidth(cp), ty + 11, 0xFFFFFFFF, shadow);
            g.fill(tx, ty + 24, x + W - 5, ty + 27, 0x99000000);
            int fill = (int) Math.round((W - 10) * Math.min(1.0, reached / (double) total));
            if (fill > 0) g.fill(tx, ty + 24, tx + fill, ty + 27, 0xFF55FFFF);
        }
        if (split != null) {
            String d = split.hasDelta() ? Fmt.delta(split.deltaMs()) : Fmt.runTime(split.timeMs());
            g.drawText(d, tx, ty + 29, split.hasDelta() ? (split.ahead() ? GREEN : RED) : 0xFFFFFFFF, shadow);
        }
    }
}
