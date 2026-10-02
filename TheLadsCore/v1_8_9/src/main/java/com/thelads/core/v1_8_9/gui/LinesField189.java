package com.thelads.core.v1_8_9.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/**
 * One entry per line for the reconnect editors (26.x uses MultiLineEditBox; 1.8.9 has none): a scrolling column of text fields
 * that always ends with an empty one. Enter adds a line below, Backspace on an empty line removes it, Up and Down move.
 */
final class LinesField189 {
    private static final int ROW = 14;
    private final FontRenderer font;
    private final int x, y, width, height, maxLength, maxLines;
    private final List<GuiTextField> rows = new ArrayList<>();
    private int scroll;

    LinesField189(FontRenderer font, int x, int y, int width, int height, List<String> lines, int maxLength, int maxLines) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.maxLength = maxLength;
        this.maxLines = maxLines;
        for (String line : lines) insert(rows.size(), line);
        insert(rows.size(), "");
    }

    /** The non-blank lines, in order. */
    List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (GuiTextField row : rows) if (!row.getText().trim().isEmpty()) lines.add(row.getText());
        return lines;
    }

    private GuiTextField insert(int index, String text) {
        GuiTextField row = new GuiTextField(0, font, x + 2, 0, width - 4, ROW - 2);
        row.setMaxStringLength(maxLength);
        row.setText(text);
        rows.add(index, row);
        return row;
    }

    private int visible() { return Math.max(1, (height - 4) / ROW); }

    void draw() {
        Gui.drawRect(x, y, x + width, y + height, 0xff2a3440);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xff0b1016);
        for (int i = scroll; i < Math.min(rows.size(), scroll + visible()); i++) {
            rows.get(i).yPosition = y + 3 + (i - scroll) * ROW;
            rows.get(i).drawTextBox();
        }
    }

    void update() { for (GuiTextField row : rows) if (row.isFocused()) row.updateCursorCounter(); }

    void click(int mouseX, int mouseY, int button) {
        for (int i = 0; i < rows.size(); i++) {
            if (i >= scroll && i < scroll + visible()) rows.get(i).mouseClicked(mouseX, mouseY, button);
            else rows.get(i).setFocused(false);
        }
    }

    void scroll(int mouseX, int mouseY, int wheel) {
        if (mouseX < x || mouseY < y || mouseX >= x + width || mouseY >= y + height) return;
        scroll = Math.max(0, Math.min(rows.size() - visible(), scroll - Integer.signum(wheel)));
    }

    /** True when a line here had the keyboard. */
    boolean key(char character, int key) {
        int focused = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).isFocused()) focused = i;
        if (focused < 0) return false;
        GuiTextField row = rows.get(focused);
        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && rows.size() < maxLines) focus(focused, insert(focused + 1, ""), focused + 1);
        else if (key == Keyboard.KEY_BACK && row.getText().isEmpty() && focused > 0) {
            rows.remove(focused);
            focus(-1, rows.get(focused - 1), focused - 1);
        } else if (key == Keyboard.KEY_UP && focused > 0) focus(focused, rows.get(focused - 1), focused - 1);
        else if ((key == Keyboard.KEY_DOWN || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && focused + 1 < rows.size())
            focus(focused, rows.get(focused + 1), focused + 1);
        else row.textboxKeyTyped(character, key);
        if (!rows.get(rows.size() - 1).getText().isEmpty() && rows.size() < maxLines) insert(rows.size(), "");
        return true;
    }

    private void focus(int from, GuiTextField to, int index) {
        if (from >= 0) rows.get(from).setFocused(false);
        to.setFocused(true);
        if (index < scroll) scroll = index;
        else if (index >= scroll + visible()) scroll = index - visible() + 1;
    }
}
