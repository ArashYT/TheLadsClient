package com.thelads.core.v26_2.feature;

import com.thelads.core.v26_2.mixin.ChatScreenInputAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.world.inventory.ChestMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Keep UI Open in the QA world: chat comes back after a loading screen, a server close leaves your inventory but closes a chest. */
final class KeepUiProbe26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static boolean done;
    private static int passed;
    private KeepUiProbe26() {}

    static void tick(boolean ready) {
        if (done || !ready) return;
        done = true;
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.gui.setScreen(new ChatScreen("keep me", false));
            mc.gui.setScreen(new GenericMessageScreen(Component.literal("Lads QA")));
            mc.gui.setScreen(null);
            check(mc.gui.screen() instanceof ChatScreen chat && "keep me".equals(((ChatScreenInputAccessor) chat).lads$input().getValue()),
                "a chat pushed away by a loading screen comes back with its text");
            mc.getConnection().handleContainerClose(new ClientboundContainerClosePacket(0));
            check(mc.gui.screen() instanceof ChatScreen, "the server's close-container packet leaves chat open");
            mc.gui.setScreen(null);
            check(mc.gui.screen() == null, "closing chat yourself keeps it closed");
            mc.gui.setScreen(new InventoryScreen(mc.player));
            mc.getConnection().handleContainerClose(new ClientboundContainerClosePacket(0));
            check(mc.gui.screen() instanceof InventoryScreen, "the server's close-container packet leaves your inventory open");
            mc.gui.setScreen(new ContainerScreen(ChestMenu.threeRows(1, mc.player.getInventory()), mc.player.getInventory(), Component.literal("Lads QA")));
            mc.getConnection().handleContainerClose(new ClientboundContainerClosePacket(1));
            check(mc.gui.screen() == null, "a chest still closes on the server's packet");
            LOGGER.info("Lads keep UI probe END: {} passed, 0 failed", passed);
        } catch (RuntimeException failure) {
            mc.gui.setScreen(null);
            LOGGER.error("Lads keep UI probe END: {} passed, 1 failed", passed, failure);
        }
    }

    private static void check(boolean result, String description) {
        if (!result) throw new IllegalStateException("Keep UI Open: " + description);
        passed++;
        LOGGER.info("Lads keep UI probe PASS {}: {}", passed, description);
    }
}
