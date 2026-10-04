package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.ReconnectSettings;
import com.thelads.core.v1_8_9.feature.AutoReconnect189;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** AutoReconnect's list editor, as 26.x ReconnectOptionsScreen26: one delay per attempt, one reason key/pattern per line. */
public final class ReconnectOptionsScreen189 extends GuiScreen {
    private final GuiScreen parent;
    private final ReconnectSettings draft;
    private String delayText;
    private List<String> keyLines, patternLines;
    private String error = "";
    private GuiTextField delays;
    private LinesField189 keys, patterns;
    private int left, span;

    public ReconnectOptionsScreen189(GuiScreen parent) {
        this.parent = parent;
        draft = AutoReconnect189.settings().copy();
        StringBuilder text = new StringBuilder();
        for (Integer seconds : draft.retryDelays) text.append(text.length() == 0 ? "" : ", ").append(seconds);
        delayText = text.toString();
        keyLines = draft.reasonKeys;
        patternLines = draft.reasonPatterns;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (delays != null) { delayText = delays.getText(); keyLines = keys.lines(); patternLines = patterns.lines(); }
        span = Math.min(620, width - 32);
        left = (width - span) / 2;
        delays = new GuiTextField(0, fontRendererObj, left, 53, span, 20);
        delays.setMaxStringLength(1200);
        delays.setText(delayText);
        int column = (span - 10) / 2, boxHeight = Math.max(35, height - 165);
        keys = new LinesField189(fontRendererObj, left, 101, column, boxHeight, keyLines, 16384, 128);
        patterns = new LinesField189(fontRendererObj, left + column + 10, 101, column, boxHeight, patternLines, 16384, 128);
        buttonList.clear();
        buttonList.add(new GuiButton(0, left, height - 28, 95, 20, "Save"));
        buttonList.add(new GuiButton(1, left + 103, height - 28, 95, 20, "Cancel"));
    }

    @Override
    public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) save();
        else mc.displayGuiScreen(parent);
    }

    private void save() {
        try {
            List<Integer> parsed = new ArrayList<>();
            if (!delays.getText().trim().isEmpty()) for (String token : delays.getText().trim().split("[,\\s]+")) {
                int seconds = Integer.parseInt(token);
                if (seconds < 1 || seconds > 86400) throw new IllegalArgumentException("Each delay must be 1–86400 seconds.");
                parsed.add(seconds);
            }
            if (parsed.size() > 100) throw new IllegalArgumentException("Use at most 100 retry delays.");
            List<String> keyList = keys.lines(), patternList = patterns.lines();
            if (keyList.size() > 128 || patternList.size() > 128) throw new IllegalArgumentException("Use at most 128 entries in each filter list.");
            for (String expression : patternList) Pattern.compile(expression);
            draft.retryDelays = parsed;
            draft.reasonKeys = keyList;
            draft.reasonPatterns = patternList;
            AutoReconnect189.replaceSettings(draft);
            mc.displayGuiScreen(parent);
        } catch (RuntimeException invalid) {
            error = invalid instanceof NumberFormatException ? "Enter whole seconds separated by commas." : invalid.getMessage();
        }
    }

    @Override
    public void updateScreen() {
        delays.updateCursorCounter();
        keys.update();
        patterns.update();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else if (delays.isFocused()) delays.textboxKeyTyped(typedChar, keyCode);
        else if (!keys.key(typedChar, keyCode)) patterns.key(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        delays.mouseClicked(mouseX, mouseY, button);
        keys.click(mouseX, mouseY, button);
        patterns.click(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int x = Mouse.getEventX() * width / mc.displayWidth, y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        keys.scroll(x, y, wheel);
        patterns.scroll(x, y, wheel);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xff111820);
        fontRendererObj.drawString("Reconnect delays and filters", left, 18, 0xffe8f1f5);
        fontRendererObj.drawString("One delay per attempt; an empty list disables automatic attempts.", left, 38, 0xffaabcc8);
        fontRendererObj.drawString("Reason keys (one per line)", left, 87, 0xffaabcc8);
        fontRendererObj.drawString("Reason patterns (regular expressions)", left + (span + 10) / 2, 87, 0xffaabcc8);
        delays.drawTextBox();
        keys.draw();
        patterns.draw();
        if (!error.isEmpty()) fontRendererObj.drawString(fontRendererObj.trimStringToWidth(error, span), left, height - 44, 0xffff9292);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
