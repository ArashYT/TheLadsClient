package com.thelads.core.v1_8_9.gui;

import com.thelads.core.v1_8_9.feature.Reconnect189;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** AutoReconnect's actions, as 26.x ReconnectActionsScreen26: explicitly configured per server or world. Editing never sends anything. */
public final class ReconnectActionsScreen189 extends GuiScreen {
    private final GuiScreen parent;
    private final Reconnect189.Settings draft;
    private int selected, left, span;
    private GuiTextField id, delay;
    private LinesField189 messages;
    private String error = "";

    public ReconnectActionsScreen189(GuiScreen parent) {
        this.parent = parent;
        draft = Reconnect189.settings().copy();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        String currentId = id == null ? null : id.getText(), currentDelay = delay == null ? null : delay.getText();
        List<String> currentMessages = messages == null ? null : messages.lines();
        span = Math.min(620, width - 32);
        left = (width - span) / 2;
        buttonList.clear();
        id = delay = null;
        messages = null;
        int count = draft.autoMessages.size();
        add(new GuiButton(0, left, 40, 60, 20, "Add"), count < 64);
        add(new GuiButton(1, left + 66, 40, 68, 20, "Remove"), count > 0);
        add(new GuiButton(2, left + span - 60, 40, 26, 20, "<"), selected > 0);
        add(new GuiButton(3, left + span - 26, 40, 26, 20, ">"), selected + 1 < count);
        if (count > 0) {
            Reconnect189.Settings.Action action = draft.autoMessages.get(selected);
            id = new GuiTextField(0, fontRendererObj, left, 82, span - 130, 20);
            id.setMaxStringLength(512);
            id.setText(currentId == null ? action.id : currentId);
            delay = new GuiTextField(1, fontRendererObj, left + span - 120, 82, 120, 20);
            delay.setMaxStringLength(12);
            delay.setText(currentDelay == null ? Double.toString(action.delay) : currentDelay);
            messages = new LinesField189(fontRendererObj, left, 121, span, Math.max(35, height - 217), currentMessages == null ? action.messages : currentMessages, 32767, 100);
            add(new GuiButton(4, left, height - 84, 140, 20, action.enabled ? "Profile enabled" : "Profile disabled"), true);
        }
        add(new GuiButton(5, left, height - 28, 95, 20, "Save"), true);
        add(new GuiButton(6, left + 103, height - 28, 95, 20, "Cancel"), true);
    }

    private void add(GuiButton button, boolean enabled) {
        button.enabled = enabled;
        buttonList.add(button);
    }

    @Override
    public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    /** A different profile, or the list changed: the fields are rebuilt for the selected one. */
    private void rebuild() {
        id = delay = null;
        messages = null;
        initGui();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                if (!stash()) return;
                draft.autoMessages.add(new Reconnect189.Settings.Action());
                selected = draft.autoMessages.size() - 1;
                rebuild();
                break;
            case 1:
                if (draft.autoMessages.isEmpty()) return;
                draft.autoMessages.remove(selected);
                selected = Math.max(0, selected - 1);
                rebuild();
                break;
            case 2: if (stash()) { selected--; rebuild(); } break;
            case 3: if (stash()) { selected++; rebuild(); } break;
            case 4:
                Reconnect189.Settings.Action action = draft.autoMessages.get(selected);
                action.enabled = !action.enabled;
                button.displayString = action.enabled ? "Profile enabled" : "Profile disabled";
                break;
            case 5: save(); break;
            default: mc.displayGuiScreen(parent); break;
        }
    }

    private boolean stash() {
        if (id == null || draft.autoMessages.isEmpty()) return true;
        try {
            Reconnect189.Settings.Action action = draft.autoMessages.get(selected);
            double seconds = Double.parseDouble(delay.getText());
            if (!(seconds >= .1 && seconds <= 3600)) throw new IllegalArgumentException("Action delay must be 0.1–3600 seconds.");
            if (Reconnect189.module().regexIds.get()) Pattern.compile(id.getText());
            List<String> lines = messages.lines();
            for (String line : lines)
                if (line.length() > (line.startsWith("/") ? 32767 : 256)) throw new IllegalArgumentException("A chat message exceeds 256 characters or a command exceeds 32767.");
            action.id = id.getText();
            action.delay = seconds;
            action.messages = lines;
            error = "";
            return true;
        } catch (RuntimeException invalid) {
            error = invalid instanceof NumberFormatException ? "Enter a numeric action delay." : invalid.getMessage();
            return false;
        }
    }

    private void save() {
        if (!stash()) return;
        try {
            Reconnect189.replaceSettings(draft);
            mc.displayGuiScreen(parent);
        } catch (RuntimeException failed) {
            error = failed.getMessage();
        }
    }

    @Override
    public void updateScreen() {
        if (id == null) return;
        id.updateCursorCounter();
        delay.updateCursorCounter();
        messages.update();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else if (id == null) return;
        else if (id.isFocused()) id.textboxKeyTyped(typedChar, keyCode);
        else if (delay.isFocused()) delay.textboxKeyTyped(typedChar, keyCode);
        else messages.key(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (id != null) {
            id.mouseClicked(mouseX, mouseY, button);
            delay.mouseClicked(mouseX, mouseY, button);
            messages.click(mouseX, mouseY, button);
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && messages != null)
            messages.scroll(Mouse.getEventX() * width / mc.displayWidth, height - Mouse.getEventY() * height / mc.displayHeight - 1, wheel);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xff111820);
        fontRendererObj.drawString("Actions after reconnecting", left, 18, 0xffe8f1f5);
        fontRendererObj.drawString(draft.autoMessages.isEmpty() ? "No actions configured" : "Profile " + (selected + 1) + " / " + draft.autoMessages.size(), left + 146, 46, 0xffaabcc8);
        if (id != null) {
            fontRendererObj.drawString("Server address or world folder", left, 69, 0xffaabcc8);
            fontRendererObj.drawString("Delay (seconds)", left + span - 120, 69, 0xffaabcc8);
            fontRendererObj.drawString("One message or /command per line", left, 108, 0xffaabcc8);
            id.drawTextBox();
            delay.drawTextBox();
            messages.draw();
        }
        fontRendererObj.drawString("Runs only after automatic reconnect, with Enable Reconnect Actions on.", left, height - 56, 0xffaabcc8);
        if (!error.isEmpty()) fontRendererObj.drawString(fontRendererObj.trimStringToWidth(error, span), left, height - 43, 0xffff9292);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
