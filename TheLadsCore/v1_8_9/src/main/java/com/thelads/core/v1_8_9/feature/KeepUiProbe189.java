package com.thelads.core.v1_8_9.feature;

import com.thelads.core.v1_8_9.mixin.GuiChatInputAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.network.play.server.S2EPacketCloseWindow;

/** Keep UI Open in the QA world: chat comes back after a loading screen, a server close leaves your inventory but closes a chest. */
final class KeepUiProbe189 {
    private KeepUiProbe189() {}

    static boolean run(Minecraft mc) {
        mc.displayGuiScreen(new GuiChat("keep me"));
        mc.displayGuiScreen(new GuiDownloadTerrain(mc.getNetHandler()));
        mc.displayGuiScreen(null);
        CoreProbe.check(mc.currentScreen instanceof GuiChat
            && "keep me".equals(((GuiChatInputAccessor) mc.currentScreen).lads$input().getText()),
            "Keep UI Open: a chat pushed away by the terrain screen comes back with its text");
        mc.getNetHandler().handleCloseWindow(new S2EPacketCloseWindow(0));
        CoreProbe.check(mc.currentScreen instanceof GuiChat, "Keep UI Open: the server's close-window packet leaves chat open");
        mc.displayGuiScreen(null);
        CoreProbe.check(mc.currentScreen == null, "Keep UI Open: closing chat yourself keeps it closed");
        mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
        mc.getNetHandler().handleCloseWindow(new S2EPacketCloseWindow(0));
        // In creative, GuiInventory opens the creative inventory in its place.
        CoreProbe.check(mc.currentScreen instanceof GuiInventory || mc.currentScreen instanceof GuiContainerCreative,
            "Keep UI Open: the server's close-window packet leaves your inventory open (" + mc.currentScreen + ")");
        mc.displayGuiScreen(new GuiChest(mc.thePlayer.inventory, new InventoryBasic("Lads QA", false, 27)));
        mc.getNetHandler().handleCloseWindow(new S2EPacketCloseWindow(1));
        CoreProbe.check(mc.currentScreen == null, "Keep UI Open: a chest still closes on the server's packet");
        return true;
    }
}
