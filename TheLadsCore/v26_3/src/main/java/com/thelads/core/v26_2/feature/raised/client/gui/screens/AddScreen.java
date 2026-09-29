// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.client.gui.screens;

import com.thelads.core.v26_2.feature.raised.client.gui.group.Group;
import com.thelads.core.v26_2.feature.raised.config.Config;
import net.minecraft.network.chat.Component;

import java.util.TreeSet;

public class AddScreen extends AbstractInputPopupScreen {

    public AddScreen(SelectScreen parent) {
        super(parent);
    }

    @Override
    public String initialValue() {
        return "";
    }

    @Override
    public void confirmAction() {
        Config.update(o -> o.getGroups().putIfAbsent(optionInput.getValue(), new Group(new Group.Offset(0, 0), new TreeSet<>())));
        super.confirmAction();
    }

    @Override
    public Component getPopupTitle() {
        return Component.translatable("options.raised.add");
    }

}
