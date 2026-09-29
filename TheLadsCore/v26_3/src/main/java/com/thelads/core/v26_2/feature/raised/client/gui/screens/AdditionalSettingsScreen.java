// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.client.gui.screens;

import com.thelads.core.v26_2.feature.raised.config.Config;
import com.thelads.core.v26_2.feature.raised.option.AdditionalSettings;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class AdditionalSettingsScreen extends AbstractPopupScreen {

    public AbstractWidget optionHotbarSelectionFix;

    public AdditionalSettingsScreen(SelectScreen parent) {
        super(parent);
        this.parent = parent;
    }

    public void createOptions() {
        options = new ArrayList<>();

        optionHotbarSelectionFix = CycleButton.builder(AdditionalSettings.HotbarSelectionFix::caption, Config.getOptions().getAdditionalSettings().getHotbarSelectionFix())
                .withValues(AdditionalSettings.HotbarSelectionFix.values())
                .withTooltip(value -> Tooltip.create(Component.translatable("options.raised.hotbar_selection_fix." + value.getSerializedName() + ".tooltip")))
                .create(panelX,
                        panelY + WIDGET_AND_GAP_HEIGHT,
                        panelWidth,
                        WIDGET_HEIGHT,
                        Component.translatable("options.raised.hotbar_selection_fix"),
                        (button, value) -> Config.update(o -> o.getAdditionalSettings().setHotbarSelectionFix(value)));

        options.add(optionHotbarSelectionFix);

        options.forEach(this::addRenderableWidget);
    }

    @Override
    public Component getPopupTitle() {
        return Component.translatable("options.raised.additional_settings");
    }

}
