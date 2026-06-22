package com.thelads.core.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import com.thelads.core.modules.XaeroWorldMapModule;
import org.lwjgl.glfw.GLFW;

public class LadsWorldMapScreen extends Screen {
    private final Screen parent;
    private float zoom = 1.5f;
    private float panX = 0f;
    private float panY = 0f;

    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private boolean isDragging = false;

    public LadsWorldMapScreen(Screen parent) {
        super(Component.literal("World Map"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft != null) {
            XaeroWorldMapModule.getInstance().updateMap(this.minecraft);
        }

        // Close button
        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.minecraft.setScreenAndShow(parent))
                .bounds(this.width - 70, 10, 60, 20).build());

        // Refresh button
        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> {
            if (this.minecraft != null) {
                XaeroWorldMapModule.getInstance().updateMap(this.minecraft);
            }
        }).bounds(this.width - 140, 10, 60, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Draw dark background
        g.fill(0, 0, this.width, this.height, 0xFF000000);

        Identifier textureLocation = XaeroWorldMapModule.getInstance().getTextureLocation();
        if (textureLocation != null) {
            // Center of the screen
            int cx = this.width / 2;
            int cy = this.height / 2;

            int mapSize = (int) (256 * zoom);
            int mapX = (int) (cx - mapSize / 2 + panX);
            int mapY = (int) (cy - mapSize / 2 + panY);

            // Draw map texture
            g.blit(textureLocation, mapX, mapY, mapX + mapSize, mapY + mapSize, 0.0f, 1.0f, 0.0f, 1.0f);

            // Draw player location marker on map
            if (this.minecraft != null && this.minecraft.player != null) {
                double px = this.minecraft.player.getX();
                double pz = this.minecraft.player.getZ();
                int mapCenterX = XaeroWorldMapModule.getInstance().getMapCenterX();
                int mapCenterZ = XaeroWorldMapModule.getInstance().getMapCenterZ();

                double relX = px - mapCenterX;
                double relZ = pz - mapCenterZ;

                // Relative pixel offset from map center
                int markerX = (int) (cx + panX + (relX * zoom));
                int markerY = (int) (cy + panY + (relZ * zoom));

                // Draw yellow/red marker dot if within map bounds
                if (markerX >= mapX && markerX <= mapX + mapSize && markerY >= mapY && markerY <= mapY + mapSize) {
                    g.fill(markerX - 3, markerY - 3, markerX + 3, markerY + 3, 0xFFFF3333);
                    g.fill(markerX - 1, markerY - 1, markerX + 1, markerY + 1, 0xFFFFFF33);
                }
            }
        }

        // Draw coordinate text overlays
        if (this.minecraft != null && this.minecraft.player != null) {
            int px = (int) this.minecraft.player.getX();
            int py = (int) this.minecraft.player.getY();
            int pz = (int) this.minecraft.player.getZ();
            String coords = String.format("Player Position: X:%d, Y:%d, Z:%d", px, py, pz);
            g.text(this.font, coords, 10, 10, 0xFFFFFFFF);
        }

        g.text(this.font, "Zoom: " + String.format("%.1fx", zoom), 10, 25, 0xFFFFFFFF);
        g.text(this.font, "Scroll to Zoom | Drag Left Click to Pan", 10, this.height - 20, 0xFFAAAAAA);

        // Track drag
        if (isDragging && this.minecraft != null) {
            if (GLFW.glfwGetMouseButton(this.minecraft.getWindow().handle(), 0) == 1) {
                panX += (mouseX - lastMouseX);
                panY += (mouseY - lastMouseY);
                lastMouseX = mouseX;
                lastMouseY = mouseY;
            } else {
                isDragging = false;
            }
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float oldZoom = zoom;
        zoom = Math.max(0.5f, Math.min(5.0f, zoom + (float) scrollY * 0.15f));
        // Adjust panning to feel natural during zoom
        panX = (panX * zoom) / oldZoom;
        panY = (panY * zoom) / oldZoom;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (event.button() == 0) {
            isDragging = true;
            lastMouseX = event.x();
            lastMouseY = event.y();
            return true;
        }
        return super.mouseClicked(event, isDouble);
    }
}
