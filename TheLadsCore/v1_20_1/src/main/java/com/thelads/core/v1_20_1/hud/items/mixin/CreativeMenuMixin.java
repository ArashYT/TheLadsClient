package com.thelads.core.v1_20_1.hud.items.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A creative inventory built off screen leaves its item grid as the player's open menu: Better Statistics Screen (in the 1.20.1
 * pack) builds one on joining every world. Container 0's updates then went into that grid instead of the inventory, so items
 * picked up or given before the inventory was first opened were missing from the main inventory (the hotbar is updated apart).
 * Unless the creative screen is showing, the player's own inventory menu is the open one when such an update arrives.
 */
@Mixin(ClientPacketListener.class)
abstract class CreativeMenuMixin {
    @Inject(method = {"handleContainerSetSlot", "handleContainerContent"}, at = @At("HEAD"))
    private void lads$ownMenu(CallbackInfo callback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread() && mc.player != null && mc.player.containerMenu instanceof CreativeModeInventoryScreen.ItemPickerMenu
            && !(mc.screen instanceof CreativeModeInventoryScreen)) mc.player.containerMenu = mc.player.inventoryMenu;
    }
}
