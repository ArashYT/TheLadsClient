package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiVideoSettings;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** The shared Lads menu on 1.8.9: LWJGL 2 key codes are translated to the GLFW codes the common menu uses. */
public class LadsSettingsScreen189 extends GuiScreen {
    private final GuiScreen parent;
    private final LadsSettingsScreen ui = new LadsSettingsScreen();
    private int lastX, lastY;

    public LadsSettingsScreen189(GuiScreen parent) {
        this.parent = parent;
        ui.setOnClose(() -> Minecraft.getMinecraft().displayGuiScreen(parent));
        ui.setOnOpenResourcePacks(() -> Minecraft.getMinecraft().displayGuiScreen(new GuiScreenResourcePacks(this)));
        ui.setOnOpenVideoSettings(() -> Minecraft.getMinecraft().displayGuiScreen(new GuiVideoSettings(this, Minecraft.getMinecraft().gameSettings)));
        ui.setClipboardReader(GuiScreen::getClipboardString);
        ui.setOnOpenHudEditor(() -> Minecraft.getMinecraft().displayGuiScreen(new DraggableHudScreen189(this)));
    }

    /** The screen the menu returns to (null: gameplay). */
    public GuiScreen parent() { return parent; }
    /** The shared menu this screen draws and routes input to (QA reads its state and control bounds). */
    public LadsSettingsScreen ui() { return ui; }
    /** Opens a module's settings (the HUD editor's gear). */
    public void openModule(String name) { ui.openModule(name); }
    public void searchKillBanners(String query) { ui.searchKillBanners(query); }

    @Override
    public void initGui() {
        ui.refreshCatalog();
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ui.render(new GuiLadsAdapter(fontRendererObj, width, height), mouseX, mouseY);
    }

    /** A key press for the menu (LWJGL 2 code); true when the menu used it. */
    public boolean keyPressed(int lwjglKey) {
        return ui.keyPressed(glfwKey(lwjglKey), (isShiftKeyDown() ? 1 : 0) | (isCtrlKeyDown() ? 2 : 0));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // The key, then its character, as GLFW reports them on the other versions. Escape closes through the menu.
        keyPressed(keyCode);
        if (typedChar != 0) ui.charTyped(typedChar);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        lastX = mouseX;
        lastY = mouseY;
        if (!ui.mouseClicked(mouseX, mouseY, button)) super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        ui.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long heldMillis) {
        ui.mouseDragged(mouseX, mouseY, button, mouseX - lastX, mouseY - lastY);
        lastX = mouseX;
        lastY = mouseY;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) ui.mouseScrolled(Mouse.getEventX() * width / mc.displayWidth,
            height - Mouse.getEventY() * height / mc.displayHeight - 1, Integer.signum(wheel));
    }

    /** LWJGL 2 key code to the GLFW code the common menu expects; -1 for keys it does not use. */
    static int glfwKey(int key) {
        switch (key) {
            case Keyboard.KEY_ESCAPE: return 256;
            case Keyboard.KEY_RETURN: case Keyboard.KEY_NUMPADENTER: return 257;
            case Keyboard.KEY_TAB: return 258;
            case Keyboard.KEY_BACK: return 259;
            case Keyboard.KEY_DELETE: return 261;
            case Keyboard.KEY_RIGHT: return 262;
            case Keyboard.KEY_LEFT: return 263;
            case Keyboard.KEY_DOWN: return 264;
            case Keyboard.KEY_UP: return 265;
            case Keyboard.KEY_PRIOR: return 266;
            case Keyboard.KEY_NEXT: return 267;
            case Keyboard.KEY_HOME: return 268;
            case Keyboard.KEY_END: return 269;
            case Keyboard.KEY_SPACE: return 32;
            case Keyboard.KEY_LSHIFT: return 340;
            case Keyboard.KEY_LCONTROL: return 341;
            case Keyboard.KEY_RSHIFT: return 344;
            case Keyboard.KEY_RCONTROL: return 345;
            default:
                // Letters and digits: GLFW uses their ASCII codes.
                String name = key > 0 && key < Keyboard.KEYBOARD_SIZE ? Keyboard.getKeyName(key) : null;
                return name != null && name.length() == 1 && (name.charAt(0) >= 'A' && name.charAt(0) <= 'Z' || Character.isDigit(name.charAt(0)))
                    ? name.charAt(0) : -1;
        }
    }
}
