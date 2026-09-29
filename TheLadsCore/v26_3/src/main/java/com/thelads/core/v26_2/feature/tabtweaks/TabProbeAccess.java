package com.thelads.core.v26_2.feature.tabtweaks;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Only used on an isolated overlay instance by the opt-in, local runtime probe. */
public interface TabProbeAccess {
    void ladsTab$fixture(List<PlayerInfo> players);
    List<PlayerInfo> ladsTab$players();
    void ladsTab$ping(GuiGraphicsExtractor graphics, PlayerInfo player);
}
