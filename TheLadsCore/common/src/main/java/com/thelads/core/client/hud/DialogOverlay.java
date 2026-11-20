package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.client.ladsapi.minigame.DialogState;
import com.thelads.core.client.ladsapi.minigame.DialogState.Dialog;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.config.HudSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * Server dialogs (Lads API, PROTOCOL.md section 21) in the Lads look: toast and vote cards stacked in the top right corner, a modal
 * card centred. A card has a title, a wrapped body, one row per button (a colour stripe, an optional item icon, "1" to "6" on vote
 * and modal) and a bar that runs down with the dialog's time. Version-neutral: the version draws it through {@link LadsGraphics} on
 * the HUD (no screen) and on every screen, and feeds {@link #click}. A choice goes out through {@link LadsApiClient#dialogChoice}.
 */
public final class DialogOverlay {
    private DialogOverlay() {}

    /** A clickable rect from the last draw: a button (choice = its id) or the card's close mark (choice "dismiss"). */
    private record Hit(int x, int y, int w, int h, String dialog, String choice) {
        boolean has(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    private static final int CARD_W = 176, PAD = 6, ROW_H = 15, BAR_H = 3, ACCENT = 0xFF55FFFF;
    private static volatile List<Hit> hits = List.of();

    /** Whether anything is open now (the version opens the modal screen when a modal shows). */
    public static boolean any() {
        return !Minigame.dialogs.open(Minigame.now()).isEmpty();
    }

    /** The open modal dialog, or null. */
    public static Dialog modal() {
        for (Dialog d : Minigame.dialogs.open(Minigame.now())) if (d.style() == DialogState.Style.MODAL) return d;
        return null;
    }

    /** The oldest open vote or modal: what the number keys choose from. */
    public static Dialog numbered() {
        Dialog vote = null;
        for (Dialog d : Minigame.dialogs.open(Minigame.now())) {
            if (d.style() == DialogState.Style.MODAL) return d;
            if (d.style() == DialogState.Style.VOTE && vote == null) vote = d;
        }
        return vote;
    }

    /** The number key (index 0-5) was pressed: choose that button of the numbered dialog. True when it was used. */
    public static boolean choose(int index) {
        Dialog d = numbered();
        if (d == null || index < 0 || index >= d.buttons().size()) return false;
        return LadsApiClient.dialogChoice(d.id(), d.buttons().get(index).id());
    }

    /** Dismisses every open modal (Esc on the modal screen). */
    public static void dismissModals() {
        for (Dialog d : Minigame.dialogs.open(Minigame.now())) {
            if (d.style() == DialogState.Style.MODAL) LadsApiClient.dialogChoice(d.id(), DialogState.DISMISS);
        }
    }

    /** A click at GUI coordinates: true when it hit a button or a close mark of the last frame (the choice is sent). */
    public static boolean click(double mx, double my) {
        for (Hit h : hits) {
            if (h.has(mx, my)) {
                LadsApiClient.dialogChoice(h.dialog(), h.choice());
                return true;
            }
        }
        return false;
    }

    /** Whether the mouse is over a card (so a click there is not meant for what is behind it). */
    public static boolean over(double mx, double my) {
        for (Hit h : hits) if (h.has(mx, my)) return true;
        return false;
    }

    /** Draws every open dialog. Call once per frame; mouse in GUI coordinates (-1 when the mouse is not free). */
    public static void render(LadsGraphics g, int mx, int my) {
        long now = Minigame.now();
        List<Dialog> open = Minigame.dialogs.open(now);
        if (open.isEmpty()) {
            hits = List.of();
            return;
        }
        List<Hit> out = new ArrayList<>();
        int sw = g.getScaledWidth(), sh = g.getScaledHeight();
        int stack = 8;
        for (Dialog d : open) {
            if (d.style() == DialogState.Style.MODAL) continue;
            stack += card(g, d, sw - CARD_W - 8, stack, mx, my, now, out) + 4;
        }
        for (Dialog d : open) {
            if (d.style() != DialogState.Style.MODAL) continue;
            g.fill(0, 0, sw, sh, 0x88000000);
            int h = measure(g, d);
            card(g, d, (sw - CARD_W) / 2, Math.max(8, (sh - h) / 2), mx, my, now, out);
        }
        hits = out;
    }

    private static List<String> wrap(LadsGraphics g, String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && g.textWidth(test) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines.size() > 4 ? lines.subList(0, 4) : lines;
    }

    private static int measure(LadsGraphics g, Dialog d) {
        int h = PAD + 11;
        h += wrap(g, d.body(), CARD_W - PAD * 2).size() * 10;
        if (!d.buttons().isEmpty()) h += 3 + d.buttons().size() * (ROW_H + 2);
        return h + PAD + BAR_H;
    }

    /** Draws one card at x, y; returns its height. */
    private static int card(LadsGraphics g, Dialog d, int x, int y, int mx, int my, long now, List<Hit> out) {
        int h = measure(g, d);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        g.fill(x - 1, y - 1, x + CARD_W + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + CARD_W, y + h, 0xF0141418);
        g.fill(x, y, x + 2, y + h - BAR_H, ACCENT);
        int tx = x + PAD + 2, ty = y + PAD;
        String title = d.title() == null || d.title().isEmpty() ? "Server" : d.title();
        g.drawText(title, tx, ty, 0xFFFFFFFF, shadow);
        int cx = x + CARD_W - 11;
        boolean overClose = mx >= cx - 1 && my >= y + 2 && mx < cx + 9 && my < y + 12;
        g.drawText("x", cx + 2, ty, overClose ? 0xFFFFFFFF : 0xFF888888, shadow);
        out.add(new Hit(cx - 1, y + 2, 10, 10, d.id(), DialogState.DISMISS));
        ty += 11;
        for (String line : wrap(g, d.body(), CARD_W - PAD * 2 - 2)) {
            g.drawText(line, tx, ty, 0xFFBBBBBB, shadow);
            ty += 10;
        }
        if (!d.buttons().isEmpty()) ty += 3;
        boolean numbered = d.style() != DialogState.Style.TOAST;
        int n = 0;
        for (DialogState.Button b : d.buttons()) {
            int bx = x + PAD, bw = CARD_W - PAD * 2;
            boolean hover = mx >= bx && my >= ty && mx < bx + bw && my < ty + ROW_H;
            g.fill(bx, ty, bx + bw, ty + ROW_H, hover ? 0xFF3A3A46 : 0xFF24242C);
            g.fill(bx, ty, bx + 2, ty + ROW_H, 0xFF000000 | b.color());
            int lx = bx + 6;
            if (numbered) {
                g.drawText(String.valueOf(n + 1), lx, ty + 4, 0xFF888888, false);
                lx += 9;
            }
            if (b.icon() != null) {
                g.pushPose();
                g.translate(lx, ty - 0.5f);
                g.scale(0.8f, 0.8f);
                g.drawItemIcon(b.icon(), 0, 0);
                g.popPose();
                lx += 14;
            }
            g.drawText(b.label(), lx, ty + 4, 0xFFFFFFFF, shadow);
            out.add(new Hit(bx, ty, bw, ROW_H, d.id(), b.id()));
            ty += ROW_H + 2;
            n++;
        }
        long left = Math.max(0, d.openedAt() + d.ms() - now);
        double frac = Math.min(1, left / (double) d.ms());
        int by = y + h - BAR_H;
        g.fill(x, by, x + CARD_W, y + h, 0xFF000000);
        g.fill(x, by, x + (int) Math.round(CARD_W * frac), y + h, ACCENT);
        return h;
    }
}
