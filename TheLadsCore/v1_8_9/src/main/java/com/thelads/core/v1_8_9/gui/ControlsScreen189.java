package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.gui.KeyBindSearch;
import com.thelads.core.v1_8_9.mixin.GuiControlsAccessor;
import com.thelads.core.v1_8_9.mixin.KeyEntryAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiKeyBindingList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * Controls with Controlling's search, as the embedded Controlling on 1.21.x and 26.x: a search box (KeyBindSearch terms, Ctrl+F),
 * Show Conflicts and Show Unbound, and Reset All asking first. Every vanilla Controls screen opens as this one (replace).
 */
public final class ControlsScreen189 extends GuiControls {
    private static final int RESET = 201, CONFLICTS = 202, UNBOUND = 203;
    private static final String HINT = "Search... (name, category:, key:)";
    private enum Show { ALL, CONFLICTS, UNBOUND }
    private GuiTextField search;
    private KeyList list;
    private Show show = Show.ALL;
    private GuiButton conflicts, unbound;
    private boolean confirmingReset;

    public ControlsScreen189(GuiScreen parent, GameSettings settings) {
        super(parent, settings);
    }

    /** Forge's GuiOpenEvent: vanilla's Controls screen becomes this one, with the same parent. */
    public static GuiScreen replace(GuiScreen next) {
        return next != null && next.getClass() == GuiControls.class
            ? new ControlsScreen189(((GuiControlsAccessor) next).getParentScreen(), Minecraft.getMinecraft().gameSettings) : next;
    }

    /** Vanilla's layout (title, mouse options, list, Done and Reset All) with the search below the options and a row of filters. */
    @Override
    public void initGui() {
        String query = search == null ? "" : search.getText();
        boolean focused = search == null || search.isFocused();
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        list = new KeyList(this, mc);
        list.top = 90;
        list.bottom = height - 56;
        ((GuiControlsAccessor) (Object) this).setKeyBindingList(list);
        search = new GuiTextField(0, fontRendererObj, width / 2 - 155, 66, 310, 20);
        search.setMaxStringLength(128);
        search.setText(query);
        search.setFocused(focused);
        buttonList.add(conflicts = new GuiButton(CONFLICTS, width / 2 - 155, height - 53, 150, 20, ""));
        buttonList.add(unbound = new GuiButton(UNBOUND, width / 2 + 5, height - 53, 150, 20, ""));
        confirmingReset = false;
        refresh();
    }

    private void refresh() {
        conflicts.displayString = show == Show.CONFLICTS ? "Show All" : "Show Conflicts";
        unbound.displayString = show == Show.UNBOUND ? "Show All" : "Show Unbound";
        list.filter(search.getText(), show);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == CONFLICTS || button.id == UNBOUND) {
            Show pressed = button.id == CONFLICTS ? Show.CONFLICTS : Show.UNBOUND;
            show = show == pressed ? Show.ALL : pressed;
            refresh();
        } else if (button.id == RESET && !confirmingReset) {
            confirmingReset = true;
            button.displayString = "Confirm?";
        } else {
            super.actionPerformed(button);
            if (button.id != RESET) return;
            confirmingReset = false;
            button.displayString = I18n.format("controls.resetAll");
            refresh();
        }
    }

    @Override
    protected void keyTyped(char typed, int key) {
        if (buttonId == null && key == Keyboard.KEY_F && isCtrlKeyDown()) {
            search.setFocused(true);
            return;
        }
        if (buttonId == null && search.isFocused()) {
            if (key == Keyboard.KEY_ESCAPE) {
                search.setFocused(false);
                return;
            }
            String before = search.getText();
            if (search.textboxKeyTyped(typed, key)) {
                if (!before.equals(search.getText())) refresh();
                return;
            }
        }
        super.keyTyped(typed, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (buttonId == null) search.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        search.drawTextBox();
        if (search.getText().isEmpty()) drawString(fontRendererObj, HINT, search.xPosition + 6, search.yPosition + 6, 0x808080);
    }

    /** QA: the search box, a button by its label, and the key bindings listed, in order. */
    public GuiTextField search() { return search; }

    public GuiButton button(String label) {
        for (GuiButton button : buttonList) if (label.equals(button.displayString)) return button;
        return null;
    }

    public List<KeyBinding> shownKeys() { return list.keys(); }

    /** Vanilla's Controls rows, only those passing the search and filter, each under its category. */
    private static final class KeyList extends GuiKeyBindingList {
        private final List<IGuiListEntry> shown = new ArrayList<>();

        KeyList(GuiControls screen, Minecraft mc) {
            super(screen, mc);
        }

        void filter(String query, Show show) {
            shown.clear();
            IGuiListEntry category = null;
            for (int i = 0; i < super.getSize(); i++) {
                IGuiListEntry entry = super.getListEntry(i);
                if (!(entry instanceof KeyEntry)) {
                    category = entry;
                    continue;
                }
                KeyBinding key = ((KeyEntryAccessor) entry).getKeybinding();
                int code = key.getKeyCode();
                if (show == Show.UNBOUND && code != 0 || show == Show.CONFLICTS && !conflicts(key)
                    || !KeyBindSearch.matches(query, I18n.format(key.getKeyDescription()), I18n.format(key.getKeyCategory()),
                        GameSettings.getKeyDisplayString(code))) continue;
                if (category != null) shown.add(category);
                category = null;
                shown.add(entry);
            }
            amountScrolled = 0;
        }

        /** As vanilla marks a key red: another binding on the same key. */
        private static boolean conflicts(KeyBinding key) {
            if (key.getKeyCode() == 0) return false;
            for (KeyBinding other : Minecraft.getMinecraft().gameSettings.keyBindings)
                if (other != key && other.getKeyCode() == key.getKeyCode()) return true;
            return false;
        }

        List<KeyBinding> keys() {
            List<KeyBinding> keys = new ArrayList<>();
            for (IGuiListEntry entry : shown) if (entry instanceof KeyEntry) keys.add(((KeyEntryAccessor) entry).getKeybinding());
            return keys;
        }

        @Override
        protected int getSize() {
            return shown.size();
        }

        @Override
        public IGuiListEntry getListEntry(int index) {
            return shown.get(index);
        }
    }
}
