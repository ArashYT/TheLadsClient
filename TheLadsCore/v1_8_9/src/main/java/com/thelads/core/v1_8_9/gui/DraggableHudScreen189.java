package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The shared HUD editor on 1.8.9, as DraggableHudScreen26 on the other versions: GuiScreen's clicks, drags (mouseClickMove),
 * releases, wheel and LWJGL 2 keys go to the common editor with GLFW codes and modifiers. Pointer positions keep their
 * sub-GUI-pixel precision, since the preview is drawn smaller than the game. The preview's backdrop is this frame's world and
 * vanilla HUD, copied from the framebuffer before the editor draws (NativeHud skips the Lads HUD while the editor is open).
 */
public class DraggableHudScreen189 extends GuiScreen {
    private final GuiScreen parent;
    private final DraggableHudScreen ui = new DraggableHudScreen();
    private final GameView189 view = new GameView189();

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
        view.release();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        final boolean captured = view.capture(mc);
        // 2D over the world: with the depth test on, an armour icon (drawn 3D nearer the camera) would hide the chrome drawn over it.
        GlStateManager.disableDepth();
        try {
            ui.render(new GuiLadsAdapter(fontRendererObj, width, height) {
                @Override public boolean drawGameView(int x, int y, int w, int h) { return captured && view.draw(x, y, w, h); }
            }, mouseX, mouseY);
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
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) ui.mouseScrolled(pointerX(), pointerY(), Integer.signum(wheel));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (!ui.mouseClicked(pointerX(), pointerY(), button, modifiers())) super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        ui.mouseReleased(pointerX(), pointerY(), button, modifiers());
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long heldMillis) {
        ui.mouseDragged(pointerX(), pointerY(), button);
    }

    /** The current mouse event in GUI units, unrounded (GuiScreen floors it to whole GUI pixels). */
    private double pointerX() { return Mouse.getEventX() * width / (double) mc.displayWidth; }
    private double pointerY() { return (mc.displayHeight - 1 - Mouse.getEventY()) * height / (double) mc.displayHeight; }

    /** GLFW modifier bits: Shift 1, Control 2 (Command on macOS, as 1.8.9 treats it). */
    private static int modifiers() {
        return (isShiftKeyDown() ? 1 : 0) | (isCtrlKeyDown() ? 2 : 0);
    }
}
