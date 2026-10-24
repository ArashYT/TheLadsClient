package com.thelads.core.v1_8_9.qol;

import com.thelads.core.client.CrashCatcher;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;

/** Crash Catcher's screen after a caught crash: what crashed, Copy (the whole report) and Return to title. */
final class CrashScreen189 extends GuiScreen {
    private static final String TITLE = "The Lads Client caught a crash";
    private final List<String> text = new ArrayList<>();

    @Override
    public void initGui() {
        text.clear();
        for (String line : (CrashCatcher.summary + "\n\nYou left the world so the game could keep running. "
            + "The crash report is saved in the crash-reports folder.").split("\n", -1)) {
            if (line.isEmpty()) text.add("");
            else text.addAll(fontRendererObj.listFormattedStringToWidth(line, width - 50));
        }
        int y = Math.min(height - 50, 70 + text.size() * 9 + 12);
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 100, y, 200, 20, "Copy crash report"));
        buttonList.add(new GuiButton(1, width / 2 - 100, y + 24, 200, 20, "Return to title"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) setClipboardString(CrashCatcher.report);
        else if (button.id == 1) mc.displayGuiScreen(new GuiMainMenu());
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // Escape does not close it: the buttons say where to go.
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, TITLE, width / 2, 40, 0xFF5555);
        for (int i = 0; i < text.size(); i++) drawCenteredString(fontRendererObj, text.get(i), width / 2, 70 + i * 9, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
