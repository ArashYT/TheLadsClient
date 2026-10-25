package com.thelads.core.v1_8_9.social;

import com.thelads.core.client.social.ui.HostScreenLogic;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.io.File;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.server.integrated.IntegratedServer;
import org.lwjgl.input.Mouse;

/** Multiplayer Options (replaces Open to LAN) on 1.8.9: host screen for the common HostScreenLogic, forwards render and input only. */
public final class HostScreen189 extends GuiScreen {
    private final HostScreenLogic ui;

    public HostScreen189(GuiScreen parent) {
        Minecraft mc = Minecraft.getMinecraft();
        IntegratedServer server = mc.getIntegratedServer();
        this.ui = HostScreenLogic.forWorld(server == null ? null : new File(new File(mc.mcDataDir, "saves"), server.getFolderName()).toPath(),
            () -> Minecraft.getMinecraft().displayGuiScreen(parent));
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
    protected void keyTyped(char typedChar, int keyCode) {
        if (!ui.keyPressed(keyCode, 0, 0)) ui.charTyped(typedChar); // the logic knows 1.8.9's Esc/Enter/Backspace codes
    }
}
