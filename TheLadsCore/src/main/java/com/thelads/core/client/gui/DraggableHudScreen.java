package com.thelads.core.client.gui;

import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.ScoreboardHudElement;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.*;

/**
 * HUD layout editor.
 *
 * Controls
 *   Drag          — move element (or whole group if element is grouped)
 *   Ctrl+click    — add / remove element from selection
 *   L             — lock / unlock selected element(s)
 *   Ctrl+G        — group all selected elements together
 *   Ctrl+U        — ungroup selected element(s)
 *   G             — toggle grid
 *   Esc           — close
 */
public class DraggableHudScreen extends Screen {

    // ─── Single-element drag ───────────────────────────────────────────────────
    private HudElement dragging  = null;
    private int        dragOffX  = 0;
    private int        dragOffY  = 0;

    // ─── Group drag ───────────────────────────────────────────────────────────
    // When dragging an element that belongs to a group, all group members move.
    // groupPartners maps partner → initial offset (dx, dy) from the dragged element.
    private final Map<HudElement, int[]> groupPartners = new LinkedHashMap<>();
    private int prevDragX = 0;
    private int prevDragY = 0;

    // ─── Multi-select ─────────────────────────────────────────────────────────
    private final Set<HudElement> selected = new LinkedHashSet<>();
    private boolean ctrlHeld = false;

    // ─── Snap / grid ──────────────────────────────────────────────────────────
    private static final int GRID = 10;
    private static final int SNAP = 6;
    private boolean showGrid = true;
    private Integer guideX   = null;
    private Integer guideY   = null;

    // Group colours (one per group index, cycles)
    private static final int[] GROUP_COLORS = {
        0xFFFF8844, 0xFF44FF88, 0xFF88AAFF, 0xFFFF44CC, 0xFFFFDD44
    };

    public DraggableHudScreen() {
        super(Component.literal("Edit HUD"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // RENDER
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        this.extractTransparentBackground(g);

        // Grid
        if (showGrid) {
            for (int x = 0; x <= this.width;  x += GRID) g.fill(x, 0, x + 1, this.height, 0x18FFFFFF);
            for (int y = 0; y <= this.height; y += GRID) g.fill(0, y, this.width, y + 1, 0x18FFFFFF);
            g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x44FFFFFF);
            g.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x44FFFFFF);
        }

        HudSettings hs = HudSettings.getInstance();

        for (HudElement el : HudManager.getInstance().getElements()) {
            if (!el.isEnabled()) continue;

            int x = el.getX(), y = el.getY();
            float s = el.getScale();

            if (el instanceof ScoreboardHudElement sbEl) {
                sbEl.renderEditorPreview(g);
            } else if (s != 1.0f) {
                var pose = g.pose();
                pose.pushMatrix();
                pose.translate(x, y);
                pose.scale(s, s);
                pose.translate(-x, -y);
                el.render(g);
                pose.popMatrix();
            } else {
                el.render(g);
            }

            int w = el.getRenderWidth(), h = el.getRenderHeight();
            String name = el.getModuleName();

            // Determine border colour: dragging > selected > grouped > normal
            int color;
            if (dragging == el)           color = 0xFF00FF00;
            else if (selected.contains(el)) color = 0xFF4499FF;
            else {
                int gi = (name != null) ? hs.getGroupIndex(name) : -1;
                color = (gi >= 0) ? GROUP_COLORS[gi % GROUP_COLORS.length] : 0xFFFFFFFF;
            }

            // Draw border
            g.fill(x - 1, y - 1, x + w + 1, y,     color);
            g.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
            g.fill(x - 1, y, x,     y + h, color);
            g.fill(x + w, y, x + w + 1, y + h, color);

            // Lock icon (drawn as a small filled square with inner hole)
            if (name != null && hs.isLocked(name)) {
                g.fill(x + w - 8, y + 1, x + w - 1, y + 8, 0xFFFFDD44);
                g.fill(x + w - 7, y + 2, x + w - 2, y + 7, 0xFF444400);
            }
        }

        // Snap guides
        if (guideX != null) g.fill(guideX, 0, guideX + 1, this.height, 0xFF55FFFF);
        if (guideY != null) g.fill(0, guideY, this.width, guideY + 1, 0xFF55FFFF);

        // ── Instruction bar ──
        g.fill(0, 0, this.width, 16, 0xBB000000);
        g.text(this.font,
            "§fDrag§7 move  §fCtrl+click§7 select  §fL§7 lock  §fCtrl+G§7 group  §fCtrl+U§7 ungroup  §fG§7 grid  §fEsc§7 done",
            6, 4, 0xFFFFFFFF, true);

        // Selection count
        if (!selected.isEmpty()) {
            String sel = selected.size() + " selected";
            g.fill(this.width - 90, 0, this.width, 16, 0xBB222244);
            g.text(this.font, "§b" + sel, this.width - 86, 4, 0xFFFFFFFF, false);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MOUSE
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (event.button() != 0) return super.mouseClicked(event, isDouble);

        double mx = event.x(), my = event.y();
        HudElement hit = hitTest(mx, my);

        if (ctrlHeld) {
            // Ctrl+click: toggle in selection, don't start drag
            if (hit != null) {
                if (selected.contains(hit)) selected.remove(hit);
                else selected.add(hit);
            }
            return true;
        }

        if (hit != null) {
            String name = hit.getModuleName();
            if (name != null && HudSettings.getInstance().isLocked(name)) {
                // Locked — don't drag, just select
                selected.clear();
                selected.add(hit);
                return true;
            }

            // Start drag
            dragging   = hit;
            dragOffX   = (int) mx - hit.getX();
            dragOffY   = (int) my - hit.getY();
            prevDragX  = hit.getX();
            prevDragY  = hit.getY();

            // Build group partners
            groupPartners.clear();
            if (name != null) {
                Set<String> members = HudSettings.getInstance().getGroupMembers(name);
                if (members != null) {
                    for (HudElement other : HudManager.getInstance().getElements()) {
                        if (other == hit) continue;
                        String oName = other.getModuleName();
                        if (oName != null && members.contains(oName) && other.isEnabled()) {
                            groupPartners.put(other, new int[]{
                                other.getX() - hit.getX(),
                                other.getY() - hit.getY()
                            });
                        }
                    }
                }
            }

            selected.clear();
            return true;
        }

        selected.clear();
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null || event.button() != 0) return super.mouseDragged(event, dragX, dragY);

        int px = (int) event.x() - dragOffX;
        int py = (int) event.y() - dragOffY;
        guideX = null;
        guideY = null;

        int w = dragging.getRenderWidth(), h = dragging.getRenderHeight();

        int[] sx = snapAxis(px, w, collectTargets(true));
        int[] sy = snapAxis(py, h, collectTargets(false));

        if (sx[1] != Integer.MIN_VALUE) { px = sx[0]; guideX = sx[1]; }
        else px = Math.round(px / (float) GRID) * GRID;
        if (sy[1] != Integer.MIN_VALUE) { py = sy[0]; guideY = sy[1]; }
        else py = Math.round(py / (float) GRID) * GRID;

        int dx = px - dragging.getX();
        int dy = py - dragging.getY();
        dragging.setPosition(px, py);

        // Move all group partners by the same delta
        for (Map.Entry<HudElement, int[]> e : groupPartners.entrySet()) {
            HudElement partner = e.getKey();
            partner.setPosition(px + e.getValue()[0], py + e.getValue()[1]);
        }

        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && dragging != null) {
            savePosition(dragging);
            for (HudElement partner : groupPartners.keySet()) savePosition(partner);
            dragging = null;
            groupPartners.clear();
            guideX = null;
            guideY = null;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // KEYBOARD
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        // Track ctrl held state
        if (key == 341 || key == 345) { ctrlHeld = true; return true; }

        if (key == 71 && !ctrlHeld) { // G — toggle grid
            showGrid = !showGrid;
            return true;
        }

        if (key == 71 && ctrlHeld) { // Ctrl+G — group selected
            if (selected.size() >= 2) {
                Set<String> names = new LinkedHashSet<>();
                for (HudElement el : selected) {
                    if (el.getModuleName() != null) names.add(el.getModuleName());
                }
                if (names.size() >= 2) {
                    HudSettings.getInstance().addGroup(names);
                    ConfigManager.save();
                }
            }
            return true;
        }

        if (key == 85 && ctrlHeld) { // Ctrl+U — ungroup selected
            for (HudElement el : selected) {
                if (el.getModuleName() != null) {
                    int gi = HudSettings.getInstance().getGroupIndex(el.getModuleName());
                    if (gi >= 0) HudSettings.getInstance().removeGroup(gi);
                }
            }
            ConfigManager.save();
            return true;
        }

        if (key == 76 && !ctrlHeld) { // L — lock / unlock selected
            HudSettings hs = HudSettings.getInstance();
            for (HudElement el : selected) {
                if (el.getModuleName() != null) {
                    boolean lock = !hs.isLocked(el.getModuleName());
                    hs.setLocked(el.getModuleName(), lock);
                }
            }
            ConfigManager.save();
            return true;
        }

        if (key == 256) { // Escape — clear selection first, then close
            if (!selected.isEmpty()) {
                selected.clear();
                return true;
            }
        }

        return super.keyPressed(event);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────────────────

    private HudElement hitTest(double mx, double my) {
        List<HudElement> els = HudManager.getInstance().getElements();
        for (int i = els.size() - 1; i >= 0; i--) {
            HudElement el = els.get(i);
            if (el.isEnabled()
                && mx >= el.getX() && mx <= el.getX() + el.getRenderWidth()
                && my >= el.getY() && my <= el.getY() + el.getRenderHeight()) {
                return el;
            }
        }
        return null;
    }

    private void savePosition(HudElement el) {
        if (el.getModuleName() != null) {
            HudSettings.getInstance().setPosition(el.getModuleName(), el.getX(), el.getY());
        }
    }

    private int[] snapAxis(int pos, int size, List<Integer> targets) {
        int[] offsets = { 0, size / 2, size };
        int bestDist = SNAP + 1, bestPos = pos, bestGuide = Integer.MIN_VALUE;
        for (int t : targets) {
            for (int off : offsets) {
                int dist = Math.abs((pos + off) - t);
                if (dist < bestDist) { bestDist = dist; bestPos = t - off; bestGuide = t; }
            }
        }
        return new int[]{ bestPos, bestGuide };
    }

    private List<Integer> collectTargets(boolean xAxis) {
        List<Integer> t = new ArrayList<>();
        if (xAxis) { t.add(0); t.add(this.width / 2);  t.add(this.width);  }
        else       { t.add(0); t.add(this.height / 2); t.add(this.height); }
        for (HudElement e : HudManager.getInstance().getElements()) {
            if (!e.isEnabled() || e == dragging) continue;
            if (xAxis) { t.add(e.getX()); t.add(e.getX() + e.getRenderWidth()  / 2); t.add(e.getX() + e.getRenderWidth());  }
            else       { t.add(e.getY()); t.add(e.getY() + e.getRenderHeight() / 2); t.add(e.getY() + e.getRenderHeight()); }
        }
        return t;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        int key = event.key();
        if (key == 341 || key == 345) { ctrlHeld = false; return true; }
        return super.keyReleased(event);
    }

    @Override public boolean isPauseScreen() { return false; }
}
