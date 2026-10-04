package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.ChromaUtil;
import com.thelads.core.config.Module;
import com.thelads.core.config.*;
import com.thelads.core.modules.HudModule;

public abstract class HudElement {
    protected int x = 10;
    protected int y = 10;
    protected int width = 50;
    protected int height = 15;
    private int naturalWidth = -1;
    protected boolean enabled = true;
    protected String moduleName = null;
    private int defaultX;
    private int defaultY;
    private Integer restoredX;
    private Integer restoredY;
    private boolean editingPosition;
    private boolean organizedDefaults;
    public void useOrganizedDefaults(){organizedDefaults=true;}

    private static final float[] SCALES = { 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f };

    public abstract void render(LadsGraphics g);

    /** Editor-only entry point: sample content must never be emitted by render(). */
    public void renderEditor(LadsGraphics g) { render(g); }

    /** Optional measurement before anchors/clamping, with no drawing or persistent changes. */
    public void prepareRender(LadsGraphics g, boolean editor) {}

    /** Current-frame geometry in GUI coordinates, before viewport or rigid-group clamping. */
    public final HudGroupLayout.Rect measureBounds(LadsGraphics g, boolean editor) {
        restoreSavedPosition();
        if(naturalWidth>=0){width=naturalWidth;naturalWidth=-1;}
        prepareRender(g, editor);
        int dx=getDisplayX(g),dy=getDisplayY(g);
        if (organizedDefaults && followsOrganizedDefault() && !editingPosition && HudSettings.getInstance().getPosition(moduleName)==null) {
            int[] origin=HudDefaults.origin(moduleName,g.getScaledWidth(),g.getScaledHeight(),getRenderWidth(),getRenderHeight());
            if(origin!=null){dx=origin[0];dy=origin[1];}
        }
        return new HudGroupLayout.Rect(dx,dy,getRenderWidth(),getRenderHeight());
    }

    /** False while the element places itself by default (getDisplayX/Y) instead of at its HudDefaults origin. */
    protected boolean followsOrganizedDefault() { return true; }

    /** False for catalog entries that have no implemented HUD renderer. */
    public boolean isAvailable() { return true; }

    public int getDisplayX(LadsGraphics g) { return x; }
    public int getDisplayY(LadsGraphics g) { return y; }

    /** Inverse of display positioning for editor drags; stored positions exclude module offsets. */
    public void setDisplayPosition(int displayX, int displayY) { setPosition(displayX, displayY); }

    /** Shared origin/scale transform for live HUD content and editor previews. */
    public final void renderAt(LadsGraphics g, int displayX, int displayY, boolean editor) {
        float scale = getScale();
        g.pushPose();
        try {
            g.translate(displayX, displayY);
            if (scale != 1.0f) g.scale(scale, scale);
            g.translate(-x, -y);
            if (editor) renderEditor(g); else render(g);
        } finally {
            g.popPose();
        }
    }

    /** Consume loaded/profile-switched settings without continually resetting an in-progress drag. */
    public final void restoreSavedPosition() {
        if (moduleName == null || editingPosition) return;
        int[] saved = HudSettings.getInstance().getPosition(moduleName);
        if (saved != null && saved.length >= 2) {
            if (restoredX == null || restoredY == null || restoredX != saved[0] || restoredY != saved[1]) {
                setPosition(saved[0], saved[1]);
                restoredX = saved[0];
                restoredY = saved[1];
            }
        } else if (restoredX != null) {
            setPosition(defaultX, defaultY);
            restoredX = null;
            restoredY = null;
        }
    }

    public void beginPositionEdit() { editingPosition = true; }
    public void endPositionEdit() { editingPosition = false; }

    protected void drawBackground(LadsGraphics g) {
        int bg = resolveBackground();
        if ((bg & 0xFF000000) != 0) {
            g.fill(x, y, x + width, y + height, bg);
        }
    }

    protected int resolveBackground() {
        if (!HudSettings.getInstance().isBackgrounds()) return 0;
        int c = HudSettings.getInstance().getGlobalBackground();
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m != null) {
                Option o = m.getOption("Background");
                if (o instanceof ColorOption co) {
                    c = co.isUseGlobal() ? HudSettings.getInstance().getGlobalBackground() : co.getColor();
                }
            }
        }
        return c;
    }

    public float getScale() {
        if (moduleName != null) {
            Module module = ModuleManager.getInstance().getModule(moduleName);
            if (module != null && module.getOption("Size") instanceof SliderOption slider) {
                double scale = slider.getValue() / 100.0;
                return Double.isFinite(scale) ? (float) Math.max(0.5, Math.min(2.0, scale)) : 1.0f;
            }
        }
        // Retain compatibility with callers that still supply the old dropdown.
        int idx = optCycle("Size", 2);
        if (idx < 0 || idx >= SCALES.length) {
            return 1.0f;
        }
        return SCALES[idx];
    }

    public void matchLayoutWidth(int pixels) { if(naturalWidth<0)naturalWidth=width; width = Math.max(width, (int)Math.ceil(pixels / getScale())); }

    public int getRenderWidth() { return Math.max(1, (int) Math.ceil(width * (double) getScale())); }
    public int getRenderHeight() { return Math.max(1, (int) Math.ceil(height * (double) getScale())); }

    protected int resolveColor() {
        int mode = optCycle("Color mode", 0);
        if (mode != 0) {
            return ChromaUtil.forMode(mode, this.x * 12L);
        }
        int c = HudSettings.getInstance().getGlobalColor();
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m instanceof HudModule hm) {
                c = hm.isUseGlobalColor() ? HudSettings.getInstance().getGlobalColor() : hm.getCustomColor();
            }
        }
        if ((c & 0xFF000000) == 0) {
            c |= 0xFF000000;
        }
        return c;
    }

    protected void drawCenteredText(LadsGraphics g, String text) {
        drawCenteredText(g, text, resolveColor());
    }

    protected void drawCenteredText(LadsGraphics g, String text, int color) {
        drawCenteredText(g, text, color, g.textWidth(text));
    }

    /** Reuse a width measured during this render, never across font/resource reloads. */
    protected void drawCenteredText(LadsGraphics g, String text, int color, int tw) {
        int tx = x + (width - tw) / 2;
        int ty = y + (height - g.fontHeight()) / 2 + 1;
        g.drawText(text, tx, ty, color, HudSettings.getInstance().isTextShadow());
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
        defaultX = x;
        defaultY = y;
        restoredX = null;
        restoredY = null;
        restoreSavedPosition();
    }
    public String getModuleName() { return moduleName; }

    public boolean isEnabled() {
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            return m != null && m.isEnabled();
        }
        return enabled;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    protected boolean optBool(String name, boolean def) {
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m != null) {
                Option o = m.getOption(name);
                if (o instanceof BoolOption b) {
                    return b.get();
                }
            }
        }
        return def;
    }

    protected int optCycle(String name, int def) {
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m != null) {
                Option o = m.getOption(name);
                if (o instanceof DropdownOption d) {
                    return d.getIndex();
                }
            }
        }
        return def;
    }

    protected int optColor(String name, int def) {
        if (moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m != null) {
                Option o = m.getOption(name);
                if (o instanceof ColorOption co) {
                    return co.isUseGlobal() ? resolveColor() : (co.getColor() | 0xFF000000);
                }
            }
        }
        return def;
    }
}
