package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.StonecutterMemoryManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to StonecutterScreen to memorize last selected stonecutting recipes per item
 * and restore the selection upon insertion (Stonecutter Memory).
 */
@Environment(EnvType.CLIENT)
@Mixin(StonecutterScreen.class)
public abstract class MixinStonecutterScreen extends AbstractContainerScreen<StonecutterMenu> {

    public MixinStonecutterScreen(StonecutterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @ModifyArg(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleInventoryButtonClick(II)V"
            ),
            index = 1
    )
    private int onSelectRecipe(int buttonIndex) {
        if (StonecutterMemoryManager.isStonecutterMemoryEnabled() && this.menu != null) {
            ItemStack input = this.menu.getSlot(StonecutterMenu.INPUT_SLOT).getItem();
            if (!input.isEmpty()) {
                StonecutterMemoryManager.recordSelection(input.getItem(), buttonIndex);
            }
        }
        return buttonIndex;
    }

    @Inject(
            method = "containerChanged()V",
            at = @At("TAIL")
    )
    private void onContainerChanged(CallbackInfo ci) {
        if (!StonecutterMemoryManager.isStonecutterMemoryEnabled() || this.menu == null) {
            return;
        }
        if (this.minecraft == null || this.minecraft.gameMode == null) {
            return;
        }

        ItemStack input = this.menu.getSlot(StonecutterMenu.INPUT_SLOT).getItem();
        if (!input.isEmpty()) {
            int remembered = StonecutterMemoryManager.getRememberedIndex(input.getItem());
            if (remembered >= 0 && remembered < this.menu.getNumberOfVisibleRecipes()) {
                if (this.menu.getSelectedRecipeIndex() != remembered) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, remembered);
                }
            }
        }
    }
}
