package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.hypixel.quickplay.QuickplayScreen;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class QuickplayScreen189 extends GuiScreen {
    private final GuiScreen parent;
    private final QuickplayScreen ui = new QuickplayScreen();

    public QuickplayScreen189(GuiScreen parent) {
        this.parent = parent;
        ui.setOnClose(() -> Minecraft.getMinecraft().displayGuiScreen(parent));
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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ui.render(new GuiLadsAdapter(fontRendererObj, width, height), mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        ui.handleMouseClick(mouseX, mouseY, mouseButton, width, height);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            ui.handleMouseScroll(Integer.signum(wheel));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        int glfw = LadsSettingsScreen189.glfwKey(keyCode);
        boolean handled = ui.keyPressed(glfw);
        if (!handled && typedChar != 0) {
            ui.charTyped(typedChar);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
