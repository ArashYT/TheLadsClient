// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.client.gui.screens;

import com.thelads.core.v26_2.feature.raised.config.Config;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public abstract class AbstractInputPopupScreen extends AbstractPopupScreen {

    public SelectScreen parent;
    public EditBox optionInput;
    public AbstractWidget optionConfirm;
    public int widgetWidthInput;
    public int widgetWidthConfirm;

    public AbstractInputPopupScreen(SelectScreen parent) {
        super(parent);
        this.parent = parent;
    }

    @Override
    public void setSizes() {
        super.setSizes();

        widgetWidthInput = 100;
        widgetWidthConfirm = panelWidth - PANEL_GAP - widgetWidthInput;
    }

    @Override
    public void addContent() {
        createControls();
        createOptions();
    }

    @Override
    public void createOptions() {
        options = new ArrayList<>();

        optionInput = new EditBox(font, panelX, panelY + WIDGET_AND_GAP_HEIGHT, widgetWidthInput, WIDGET_HEIGHT, Component.translatable("options.raised.popup.input"));
        optionConfirm = Button.builder(Component.translatable("options.raised.popup.confirm"), button -> confirmAction())
                .size(widgetWidthConfirm, WIDGET_HEIGHT)
                .pos(panelX + widgetWidthInput + PANEL_GAP, panelY + WIDGET_AND_GAP_HEIGHT)
                .build();

        optionInput.setValue(initialValue());

        options.add(optionInput);
        options.add(optionConfirm);

        options.forEach(this::addRenderableWidget);
    }

    public abstract String initialValue();

    public void confirmAction() {
        parent.resetLeftList();
        onClose();
    }

    @Override
    public void tick() {
        super.tick();

        boolean exists = Config.getOptions().getGroups().containsKey(optionInput.getValue());
        optionConfirm.active = !(optionInput.getValue().isBlank() || exists);
        optionConfirm.setTooltip(exists ? Tooltip.create(Component.translatable("options.raised.popup.submit.tooltip", optionInput.getValue())) : null);
    }

}
