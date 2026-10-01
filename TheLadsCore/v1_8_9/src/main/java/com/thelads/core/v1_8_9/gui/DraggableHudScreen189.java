package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

/**
 * The shared HUD editor on 1.8.9, as DraggableHudScreen12x/26 on the other versions: GuiScreen's clicks, drags (mouseClickMove),
 * releases and LWJGL 2 keys go to the common editor with GLFW codes and modifiers. The editor has no wheel input on any version.
 */
public class DraggableHudScreen189 extends GuiScreen {
    private final GuiScreen parent;
    private final DraggableHudScreen ui = new DraggableHudScreen();

    public DraggableHudScreen189(GuiScreen parent) {
        this.parent = parent;
        // Escape and Done: the editor finishes (and saves) a drag, then its parent returns.
        ui.setOnClose(() -> Minecraft.getMinecraft().displayGuiScreen(parent));
        ui.setOnSettings(name -> {
            LadsSettingsScreen189 settings = new LadsSettingsScreen189(this);
            settings.openModule(name);
            Minecraft.getMinecraft().displayGuiScreen(settings);
        });
    }

    /** The screen the editor returns to (the Lads menu). */
    public GuiScreen parent() { return parent; }
    /** The shared editor this screen draws and routes input to (QA reads its bounds and controls). */
    public DraggableHudScreen ui() { return ui; }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true); // held arrows keep nudging, as GLFW repeats do
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        ui.close();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 2D over the world: with the depth test on, an armour icon (drawn 3D nearer the camera) would hide the chrome drawn over it.
        GlStateManager.disableDepth();
        try {
            ui.render(new GuiLadsAdapter(fontRendererObj, width, height), mouseX, mouseY);
        } finally {
            GlStateManager.enableDepth(); // a screen leaves the depth test on, as GuiContainer does
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        ui.keyPressed(LadsSettingsScreen189.glfwKey(keyCode), modifiers());
        if (typedChar != 0) ui.charTyped(typedChar);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (!ui.mouseClicked(mouseX, mouseY, button, modifiers())) super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        ui.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long heldMillis) {
        ui.mouseDragged(mouseX, mouseY, button);
    }

    /** GLFW modifier bits: Shift 1, Control 2 (Command on macOS, as 1.8.9 treats it). */
    private static int modifiers() {
        return (isShiftKeyDown() ? 1 : 0) | (isCtrlKeyDown() ? 2 : 0);
    }
}
