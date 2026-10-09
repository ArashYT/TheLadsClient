// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core, rewritten for the 1.20.1 screen API.
package com.thelads.core.v1_20_1.embedded.controlling.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.v1_20_1.embedded.controlling.ControllingConstants;
import com.thelads.core.v1_20_1.embedded.controlling.api.DisplayMode;
import com.thelads.core.v1_20_1.embedded.controlling.api.SortOrder;
import com.thelads.core.v1_20_1.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_20_1.embedded.controlling.platform.Services;
import com.thelads.core.v1_20_1.embedded.controlling.searchables.api.autcomplete.AutoCompletingEditBox;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Predicate;

/**
 * Controlling's key binds screen for 1.20.1: a search bar with auto-complete, a free keys view, show none/conflicting
 * filters and a sort order, with vanilla's {@code selectedKey} behaviour (click a key button, press the new key).
 */
public class NewKeyBindsScreen extends OptionsSubScreen {

    /** The key binding waiting for its new key, as in vanilla's key binds screen. */
    @Nullable
    public KeyMapping selectedKey;
    public long lastKeySelection;

    private AutoCompletingEditBox<KeyListEntry> search;
    private DisplayMode displayMode = DisplayMode.ALL;
    private SortOrder sortOrder = SortOrder.NONE;
    private Button buttonNone;
    private Button buttonConflicting;
    private Button buttonSort;
    private Button resetButton;
    private final DisplayableBoolean confirmingReset = new DisplayableBoolean(false, ControllingConstants.COMPONENT_OPTIONS_CONFIRM_RESET, ControllingConstants.COMPONENT_CONTROLS_RESET_ALL);
    private boolean showFree;
    private NewKeyBindsList newKeyList;
    private FreeKeysList freeKeyList;
    private CustomList list;

    public NewKeyBindsScreen(Screen lastScreen, Options options) {

        super(lastScreen, options, Component.translatable("controls.keybinds.title"));
    }

    @Override
    protected void init() {

        int searchWidth = 340;
        int centerX = this.width / 2;

        this.newKeyList = new NewKeyBindsList(this, this.minecraft);
        this.freeKeyList = new FreeKeysList(this, this.minecraft);
        this.list = showFree ? this.freeKeyList : this.newKeyList;
        this.addWidget(this.list);

        this.search = new AutoCompletingEditBox<>(this.font, centerX - searchWidth / 2, 20, searchWidth, Button.DEFAULT_HEIGHT, this.search,
                Component.translatable("selectWorld.search"), ControllingConstants.SEARCHABLE_KEYBINDINGS, () -> this.list.getAllEntries());
        this.search.addResponder(this::filterKeys);
        this.addRenderableWidget(this.search);
        this.setInitialFocus(this.search);

        int btnWidth = 99;
        int rowOne = this.height - 52;
        int rowTwo = this.height - 28;

        Button toggleFreeButton = this.addRenderableWidget(Button.builder(ControllingConstants.COMPONENT_OPTIONS_TOGGLE_FREE, PRESS_FREE)
                .bounds(centerX - 206, rowOne, btnWidth, Button.DEFAULT_HEIGHT).build());
        this.buttonSort = this.addRenderableWidget(Button.builder(sortOrder.getDisplay(), PRESS_SORT)
                .bounds(centerX - 103, rowOne, btnWidth, Button.DEFAULT_HEIGHT).build());
        this.buttonNone = this.addRenderableWidget(Button.builder(displayMode == DisplayMode.NONE ? ControllingConstants.COMPONENT_OPTIONS_SHOW_ALL : ControllingConstants.COMPONENT_OPTIONS_SHOW_NONE, PRESS_NONE)
                .bounds(centerX + 4, rowOne, btnWidth, Button.DEFAULT_HEIGHT).build());
        this.buttonConflicting = this.addRenderableWidget(Button.builder(displayMode == DisplayMode.CONFLICTING ? ControllingConstants.COMPONENT_OPTIONS_SHOW_ALL : ControllingConstants.COMPONENT_OPTIONS_SHOW_CONFLICTS, PRESS_CONFLICTING)
                .bounds(centerX + 107, rowOne, btnWidth, Button.DEFAULT_HEIGHT).build());
        this.resetButton = this.addRenderableWidget(Button.builder(confirmingReset.currentDisplay(), PRESS_RESET)
                .bounds(centerX - 155, rowTwo, 150, Button.DEFAULT_HEIGHT).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(centerX + 5, rowTwo, 150, Button.DEFAULT_HEIGHT).build());

        boolean free = this.showFree;
        this.buttonSort.active = !free;
        this.buttonNone.active = !free;
        this.buttonConflicting.active = !free;
        this.resetButton.active = !free && canReset();

        // Show the search the screen had before a resize, and trigger the first display of suggestions.
        this.search.autoComplete().accept(this.search.getValue());
        this.filterKeys(this.search.getValue());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {

        this.renderBackground(graphics);
        this.list.render(graphics, mouseX, mouseY, partialTicks);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        this.resetButton.active = !showFree && canReset();
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.search.autoComplete().render(graphics, mouseX, mouseY, partialTicks);
    }

    /** QA: types a search and returns how many rows the list then shows. */
    public int probeSearch(String query) {
        
        this.search.setValue(query);
        return this.list.children().size();
    }
    
    /** QA: presses the Toggle Free button. */
    public void probeToggleFree() {
        
        PRESS_FREE.onPress(null);
    }
    
    public CustomList keyList() {

        return this.list;
    }

    public void filterKeys(String lastSearch) {

        CustomList list = this.list;

        list.clearEntries();
        list.setScrollAmount(0);
        if(lastSearch.isEmpty() && displayMode == DisplayMode.ALL && sortOrder == SortOrder.NONE) {
            for(KeyListEntry allEntry : list.getAllEntries()) {
                list.addEntryInternal(allEntry);
            }
            return;
        }

        Predicate<KeyListEntry> extraPredicate = entry -> true;
        boolean keyList = list instanceof NewKeyBindsList;
        if(keyList) {
            extraPredicate = displayMode.getPredicate();
        }
        List<KeyListEntry> entries = ControllingConstants.SEARCHABLE_KEYBINDINGS.filterEntries(list.getAllEntries(), lastSearch, extraPredicate);
        for(KeyListEntry entry : entries) {
            list.addEntryInternal(entry);
        }

        if(keyList) {
            list.children().removeIf(entry -> !(entry instanceof IKeyEntry));
            list.sort(sortOrder);
        }
    }

    private void filterKeys() {

        filterKeys(search.getValue());
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {

        if(this.selectedKey != null) {
            Services.PLATFORM.setKey(this.options, this.selectedKey, InputConstants.Type.MOUSE.getOrCreate(button));
            this.selectedKey = null;
            this.list.resetMappingAndUpdateButtons();
            return true;
        }
        boolean handled = super.mouseClicked(x, y, button);
        if(!handled && search.isFocused() && !search.autoComplete().mouseClicked(x, y, button)) {
            this.setFocused(null);
            handled = true;
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {

        if(search.autoComplete().mouseScrolled(x, y, delta)) {
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {

        if(!search.isFocused() && this.selectedKey == null) {
            if(hasControlDown() && key == GLFW.GLFW_KEY_F) {
                this.setFocused(search);
                return true;
            }
        }
        if(search.isFocused()) {
            if(key == GLFW.GLFW_KEY_ESCAPE) {
                this.setFocused(null);
                return true;
            }
        }
        if(this.selectedKey != null) {
            Services.PLATFORM.handleKeyPress(this, this.options, key, scanCode);
            return true;
        } else {
            return super.keyPressed(key, scanCode, modifiers);
        }
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {

        if(Services.PLATFORM.handleKeyReleased(this, this.options, key, scanCode)) {
            return true;
        }
        return super.keyReleased(key, scanCode, modifiers);
    }

    private boolean canReset() {

        for(KeyMapping key : this.options.keyMappings) {
            if(!key.isDefault()) {
                return true;
            }
        }
        return false;
    }

    private final Button.OnPress PRESS_RESET = btn -> {
        Minecraft minecraft = this.minecraft;

        if(!confirmingReset.toggle()) {
            for(KeyMapping keybinding : minecraft.options.keyMappings) {
                Services.PLATFORM.setToDefault(minecraft.options, keybinding);
            }

            this.list.resetMappingAndUpdateButtons();
        }
        btn.setMessage(confirmingReset.currentDisplay());
    };

    private final Button.OnPress PRESS_NONE = btn -> {
        if(displayMode == DisplayMode.NONE) {
            buttonNone.setMessage(ControllingConstants.COMPONENT_OPTIONS_SHOW_NONE);
            displayMode = DisplayMode.ALL;
        } else {
            displayMode = DisplayMode.NONE;
            buttonNone.setMessage(ControllingConstants.COMPONENT_OPTIONS_SHOW_ALL);
            buttonConflicting.setMessage(ControllingConstants.COMPONENT_OPTIONS_SHOW_CONFLICTS);
        }
        filterKeys();
    };

    private final Button.OnPress PRESS_SORT = btn -> {
        sortOrder = sortOrder.cycle();
        btn.setMessage(sortOrder.getDisplay());
        filterKeys();
    };

    private final Button.OnPress PRESS_CONFLICTING = btn -> {
        if(displayMode == DisplayMode.CONFLICTING) {
            buttonConflicting.setMessage(ControllingConstants.COMPONENT_OPTIONS_SHOW_CONFLICTS);
            displayMode = DisplayMode.ALL;
        } else {
            displayMode = DisplayMode.CONFLICTING;
            buttonConflicting.setMessage(ControllingConstants.COMPONENT_OPTIONS_SHOW_ALL);
            buttonNone.setMessage(ControllingConstants.COMPONENT_OPTIONS_SHOW_NONE);
        }
        filterKeys();
    };

    private final Button.OnPress PRESS_FREE = btn -> {
        removeWidget(this.list);
        if(showFree) {
            buttonSort.active = true;
            buttonNone.active = true;
            buttonConflicting.active = true;
            this.list = this.newKeyList;
        } else {
            this.freeKeyList.recalculate();
            buttonSort.active = false;
            buttonNone.active = false;
            buttonConflicting.active = false;
            this.list = this.freeKeyList;
        }
        showFree = !showFree;
        this.resetButton.active = !showFree && canReset();
        filterKeys();
        this.addWidget(this.list);
    };

}
