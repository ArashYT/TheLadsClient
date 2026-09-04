package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;

import java.util.ArrayList;
import java.util.List;

public class DraggableHudScreen {
    private HudElement dragging = null;
    private int dragOffX = 0;
    private int dragOffY = 0;
    private boolean showGrid = true;
    private static final int GRID = 10;
    private static final int SNAP = 6;
    private Runnable onClose = () -> {};

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void render(LadsGraphics g, int mouseX, int mouseY) {
        int width = g.getScaledWidth();
        int height = g.getScaledHeight();

        // Dark editor overlay
        g.fill(0, 0, width, height, 0x55000000);

        // Draw grid if active
        if (showGrid) {
            for (int x = 0; x < width; x += GRID) {
                g.fill(x, 0, x + 1, height, 0x15FFFFFF);
            }
            for (int y = 0; y < height; y += GRID) {
                g.fill(0, y, width, y + 1, 0x15FFFFFF);
            }
        }

        // Render all HUD elements (even disabled ones in editor mode so user can arrange them)
        List<HudElement> elements = HudManager.getInstance().getElements();
        for (HudElement element : elements) {
            element.render(g);

            // Draw bounding border
            int ex = element.getX();
            int ey = element.getY();
            int ew = element.getRenderWidth();
            int eh = element.getRenderHeight();

            boolean isHover = mouseX >= ex && mouseX <= ex + ew && mouseY >= ey && mouseY <= ey + eh;
            boolean isDraggingThis = (dragging == element);

            int borderCol = isDraggingThis ? 0xFF55FF55 : (isHover ? 0xFF6C63FF : 0x55FFFFFF);
            g.fill(ex, ey, ex + ew, ey + 1, borderCol);
            g.fill(ex, ey + eh - 1, ex + ew, ey + eh, borderCol);
            g.fill(ex, ey, ex + 1, ey + eh, borderCol);
            g.fill(ex + ew - 1, ey, ex + ew, ey + eh, borderCol);
        }

        // Editor instructions bar at top
        g.fill(0, 0, width, 24, 0xCC111118);
        g.drawCenteredText("DRAG TO POSITION  |  'G' TOGGLE GRID  |  ESC TO SAVE & EXIT", width / 2, 7, 0xFFFFFFFF, true);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            List<HudElement> elements = HudManager.getInstance().getElements();
            for (int i = elements.size() - 1; i >= 0; i--) {
                HudElement element = elements.get(i);
                int ex = element.getX();
                int ey = element.getY();
                int ew = element.getRenderWidth();
                int eh = element.getRenderHeight();

                if (mouseX >= ex && mouseX <= ex + ew && mouseY >= ey && mouseY <= ey + eh) {
                    dragging = element;
                    dragOffX = (int) mouseX - ex;
                    dragOffY = (int) mouseY - ey;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            // Save position
            String name = dragging.getModuleName();
            if (name != null) {
                HudSettings.getInstance().setPosition(name, dragging.getX(), dragging.getY());
                ConfigManager.save();
            }
            dragging = null;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            int targetX = (int) mouseX - dragOffX;
            int targetY = (int) mouseY - dragOffY;

            // Grid snapping
            if (showGrid) {
                int modX = targetX % GRID;
                if (modX < SNAP) targetX -= modX;
                else if (modX > GRID - SNAP) targetX += (GRID - modX);

                int modY = targetY % GRID;
                if (modY < SNAP) targetY -= modY;
                else if (modY > GRID - SNAP) targetY += (GRID - modY);
            }

            dragging.setPosition(targetX, targetY);
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == 71) { // 'G' key
            showGrid = !showGrid;
            return true;
        }
        if (keyCode == 256) { // ESC key
            if (onClose != null) onClose.run();
            return true;
        }
        return false;
    }
}
