// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.client.gui.screens;

import com.thelads.core.v26_2.feature.raised.config.Config;
import net.minecraft.network.chat.Component;

public class RenameScreen extends AbstractInputPopupScreen {

    public RenameScreen(SelectScreen parent) {
        super(parent);
    }

    @Override
    public String initialValue() {
        return parent.getCurrentGroup().getGroupName();
    }

    @Override
    public void confirmAction() {
        Config.update(o -> o.getGroups().put(optionInput.getValue(), o.getGroups().remove(parent.getCurrentGroup().getGroupName())));
        super.confirmAction();
    }

    @Override
    public Component getPopupTitle() {
        return Component.translatable("options.raised.rename");
    }

}