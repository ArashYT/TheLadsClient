/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.Component
 */
package com.thelads.core.client.gui;

import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.ScoreboardHudElement;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

public class DraggableHudScreen
extends Screen {
    private HudElement dragging = null;
    private int dragOffX = 0;
    private int dragOffY = 0;
    private final Map<HudElement, int[]> groupPartners = new LinkedHashMap<HudElement, int[]>();
    private int prevDragX = 0;
    private int prevDragY = 0;
    private final Set<HudElement> selected = new LinkedHashSet<HudElement>();
    private boolean ctrlHeld = false;
    private static final int GRID = 10;
    private static final int SNAP = 6;
    private boolean showGrid = true;
    private Integer guideX = null;
    private Integer guideY = null;
    private static final int[] GROUP_COLORS = new int[]{-30652, -12255352, -7820545, -47924, -8892};

    public DraggableHudScreen() {
        super((Component)Component.literal((String)"Edit HUD"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        this.extractTransparentBackground(g);
        if (this.showGrid) {
            for (int x = 0; x <= this.width; x += 10) {
                g.fill(x, 0, x + 1, this.height, 0x18FFFFFF);
            }
            for (int y = 0; y <= this.height; y += 10) {
                g.fill(0, y, this.width, y + 1, 0x18FFFFFF);
            }
            g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x44FFFFFF);
            g.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x44FFFFFF);
        }
        HudSettings hs = HudSettings.getInstance();
        for (HudElement el : HudManager.getInstance().getElements()) {
            int color;
            if (!el.isEnabled()) continue;
            int x = el.getX();
            int y = el.getY();
            float s = el.getScale();
            if (el instanceof ScoreboardHudElement) {
                ScoreboardHudElement sbEl = (ScoreboardHudElement)el;
                sbEl.renderEditorPreview(g);
            } else if (s != 1.0f) {
                Matrix3x2fStack pose = g.pose();
                pose.pushMatrix();
                pose.translate(x, y);
                pose.scale(s, s);
                pose.translate(-x, -y);
                el.render(g);
                pose.popMatrix();
            } else {
                el.render(g);
            }
            int w = el.getRenderWidth();
            int h = el.getRenderHeight();
            String name = el.getModuleName();
            if (this.dragging == el) {
                color = -16711936;
            } else if (this.selected.contains(el)) {
                color = -12281345;
            } else {
                int gi = name != null ? hs.getGroupIndex(name) : -1;
                color = gi >= 0 ? GROUP_COLORS[gi % GROUP_COLORS.length] : -1;
            }
            g.fill(x - 1, y - 1, x + w + 1, y, color);
            g.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
            g.fill(x - 1, y, x, y + h, color);
            g.fill(x + w, y, x + w + 1, y + h, color);
            if (name == null || !hs.isLocked(name)) continue;
            g.fill(x + w - 8, y + 1, x + w - 1, y + 8, -8892);
            g.fill(x + w - 7, y + 2, x + w - 2, y + 7, -12303360);
        }
        if (this.guideX != null) {
            g.fill(this.guideX, 0, this.guideX + 1, this.height, -11141121);
        }
        if (this.guideY != null) {
            g.fill(0, this.guideY, this.width, this.guideY + 1, -11141121);
        }
        g.fill(0, 0, this.width, 16, -1157627904);
        g.text(this.font, "\u00a7fDrag\u00a77 move  \u00a7fCtrl+click\u00a77 select  \u00a7fL\u00a77 lock  \u00a7fCtrl+G\u00a77 group  \u00a7fCtrl+U\u00a77 ungroup  \u00a7fG\u00a77 grid  \u00a7fEsc\u00a77 done", 6, 4, -1, true);
        if (!this.selected.isEmpty()) {
            String sel = this.selected.size() + " selected";
            g.fill(this.width - 90, 0, this.width, 16, -1155390908);
            g.text(this.font, "\u00a7b" + sel, this.width - 86, 4, -1, false);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (event.button() != 0) {
            return super.mouseClicked(event, isDouble);
        }
        double mx = event.x();
        double my = event.y();
        HudElement hit = this.hitTest(mx, my);
        if (this.ctrlHeld) {
            if (hit != null) {
                if (this.selected.contains(hit)) {
                    this.selected.remove(hit);
                } else {
                    this.selected.add(hit);
                }
            }
            return true;
        }
        if (hit != null) {
            Set<String> members;
            String name = hit.getModuleName();
            if (name != null && HudSettings.getInstance().isLocked(name)) {
                this.selected.clear();
                this.selected.add(hit);
                return true;
            }
            this.dragging = hit;
            this.dragOffX = (int)mx - hit.getX();
            this.dragOffY = (int)my - hit.getY();
            this.prevDragX = hit.getX();
            this.prevDragY = hit.getY();
            this.groupPartners.clear();
            if (name != null && (members = HudSettings.getInstance().getGroupMembers(name)) != null) {
                for (HudElement other : HudManager.getInstance().getElements()) {
                    String oName;
                    if (other == hit || (oName = other.getModuleName()) == null || !members.contains(oName) || !other.isEnabled()) continue;
                    this.groupPartners.put(other, new int[]{other.getX() - hit.getX(), other.getY() - hit.getY()});
                }
            }
            this.selected.clear();
            return true;
        }
        this.selected.clear();
        return super.mouseClicked(event, isDouble);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging == null || event.button() != 0) {
            return super.mouseDragged(event, dragX, dragY);
        }
        int px = (int)event.x() - this.dragOffX;
        int py = (int)event.y() - this.dragOffY;
        this.guideX = null;
        this.guideY = null;
        int w = this.dragging.getRenderWidth();
        int h = this.dragging.getRenderHeight();
        int[] sx = this.snapAxis(px, w, this.collectTargets(true));
        int[] sy = this.snapAxis(py, h, this.collectTargets(false));
        if (sx[1] != Integer.MIN_VALUE) {
            px = sx[0];
            this.guideX = sx[1];
        } else {
            px = Math.round((float)px / 10.0f) * 10;
        }
        if (sy[1] != Integer.MIN_VALUE) {
            py = sy[0];
            this.guideY = sy[1];
        } else {
            py = Math.round((float)py / 10.0f) * 10;
        }
        int dx = px - this.dragging.getX();
        int dy = py - this.dragging.getY();
        this.dragging.setPosition(px, py);
        for (Map.Entry<HudElement, int[]> e : this.groupPartners.entrySet()) {
            HudElement partner = e.getKey();
            partner.setPosition(px + e.getValue()[0], py + e.getValue()[1]);
        }
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && this.dragging != null) {
            this.savePosition(this.dragging);
            for (HudElement partner : this.groupPartners.keySet()) {
                this.savePosition(partner);
            }
            this.dragging = null;
            this.groupPartners.clear();
            this.guideX = null;
            this.guideY = null;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 341 || key == 345) {
            this.ctrlHeld = true;
            return true;
        }
        if (key == 71 && !this.ctrlHeld) {
            this.showGrid = !this.showGrid;
            return true;
        }
        if (key == 71 && this.ctrlHeld) {
            if (this.selected.size() >= 2) {
                LinkedHashSet<String> names = new LinkedHashSet<String>();
                for (HudElement el : this.selected) {
                    if (el.getModuleName() == null) continue;
                    names.add(el.getModuleName());
                }
                if (names.size() >= 2) {
                    HudSettings.getInstance().addGroup(names);
                    ConfigManager.save();
                }
            }
            return true;
        }
        if (key == 85 && this.ctrlHeld) {
            for (HudElement el : this.selected) {
                int gi;
                if (el.getModuleName() == null || (gi = HudSettings.getInstance().getGroupIndex(el.getModuleName())) < 0) continue;
                HudSettings.getInstance().removeGroup(gi);
            }
            ConfigManager.save();
            return true;
        }
        if (key == 76 && !this.ctrlHeld) {
            HudSettings hs = HudSettings.getInstance();
            for (HudElement el : this.selected) {
                if (el.getModuleName() == null) continue;
                boolean lock = !hs.isLocked(el.getModuleName());
                hs.setLocked(el.getModuleName(), lock);
            }
            ConfigManager.save();
            return true;
        }
        if (key == 256 && !this.selected.isEmpty()) {
            this.selected.clear();
            return true;
        }
        return super.keyPressed(event);
    }

    private HudElement hitTest(double mx, double my) {
        List<HudElement> els = HudManager.getInstance().getElements();
        for (int i = els.size() - 1; i >= 0; --i) {
            HudElement el = els.get(i);
            if (!el.isEnabled() || !(mx >= (double)el.getX()) || !(mx <= (double)(el.getX() + el.getRenderWidth())) || !(my >= (double)el.getY()) || !(my <= (double)(el.getY() + el.getRenderHeight()))) continue;
            return el;
        }
        return null;
    }

    private void savePosition(HudElement el) {
        if (el.getModuleName() != null) {
            HudSettings.getInstance().setPosition(el.getModuleName(), el.getX(), el.getY());
        }
    }

    private int[] snapAxis(int pos, int size, List<Integer> targets) {
        int[] offsets = new int[]{0, size / 2, size};
        int bestDist = 7;
        int bestPos = pos;
        int bestGuide = Integer.MIN_VALUE;
        for (int t : targets) {
            for (int off : offsets) {
                int dist = Math.abs(pos + off - t);
                if (dist >= bestDist) continue;
                bestDist = dist;
                bestPos = t - off;
                bestGuide = t;
            }
        }
        return new int[]{bestPos, bestGuide};
    }

    private List<Integer> collectTargets(boolean xAxis) {
        ArrayList<Integer> t = new ArrayList<Integer>();
        if (xAxis) {
            t.add(0);
            t.add(this.width / 2);
            t.add(this.width);
        } else {
            t.add(0);
            t.add(this.height / 2);
            t.add(this.height);
        }
        for (HudElement e : HudManager.getInstance().getElements()) {
            if (!e.isEnabled() || e == this.dragging) continue;
            if (xAxis) {
                t.add(e.getX());
                t.add(e.getX() + e.getRenderWidth() / 2);
                t.add(e.getX() + e.getRenderWidth());
                continue;
            }
            t.add(e.getY());
            t.add(e.getY() + e.getRenderHeight() / 2);
            t.add(e.getY() + e.getRenderHeight());
        }
        return t;
    }

    public boolean keyReleased(KeyEvent event) {
        int key = event.key();
        if (key == 341 || key == 345) {
            this.ctrlHeld = false;
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

