package com.thelads.core.v1_8_9.social;

import com.thelads.core.client.social.ui.FriendsScreenLogic;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Host screen for the common FriendsScreenLogic on 1.8.9: forwards render and input only. */
public final class FriendsScreen189 extends GuiScreen {
    private final FriendsScreenLogic ui;

    public FriendsScreen189(GuiScreen parent) {
        this.ui = FriendsScreenLogic.social(() -> Minecraft.getMinecraft().displayGuiScreen(parent));
    }

    public FriendsScreenLogic ui() {
        return ui;
    }

    public boolean isEditingText() {
        return ui.isEditingText();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        ui.render(new GuiLadsAdapter(fontRendererObj, width, height), mx, my);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (!ui.mouseClicked(mx, my, button)) super.mouseClicked(mx, my, button);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) ui.mouseScrolled(Mouse.getEventX() * width / mc.displayWidth,
            height - Mouse.getEventY() * height / mc.displayHeight - 1, Integer.signum(wheel));
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        int key = Keyboard.getEventKey();
        char ch = Keyboard.getEventCharacter();
        boolean editingBefore = ui.isEditingText();
        if (Keyboard.getEventKeyState() || (key == 0 && ch >= 32)) {
            keyTyped(ch, key);
        }
        if (!(editingBefore || ui.isEditingText()) || isFunctionKey(key)) {
            mc.dispatchKeypresses();
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        ui.keyPressed(keyCode, 0, 0);
        if (typedChar >= 32 && typedChar != 127) ui.charTyped(typedChar);
    }

    private static boolean isFunctionKey(int key) {
        return (key >= Keyboard.KEY_F1 && key <= Keyboard.KEY_F10)
            || key == Keyboard.KEY_F11 || key == Keyboard.KEY_F12
            || (key >= Keyboard.KEY_F13 && key <= Keyboard.KEY_F15);
    }
}
