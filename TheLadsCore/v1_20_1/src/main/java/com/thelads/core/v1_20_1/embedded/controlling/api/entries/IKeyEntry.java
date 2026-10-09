// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_20_1.embedded.controlling.api.entries;

import com.thelads.core.v1_20_1.embedded.controlling.api.MouseClick;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.List;

public interface IKeyEntry {

    Component categoryName();

    KeyMapping getKey();

    Component getName();

    Button getBtnResetKeyBinding();

    Button getBtnChangeKeyBinding();

    List<GuiEventListener> children();

    boolean mouseClicked(MouseClick event, boolean doubleClick);

    boolean mouseReleased(MouseClick event);

}
