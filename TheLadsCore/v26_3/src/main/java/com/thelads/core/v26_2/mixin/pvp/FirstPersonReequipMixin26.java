package com.thelads.core.v26_2.mixin.pvp;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.7 Animations, No re-equip on item changes: a held stack that changed but is still the same item replaces the shown one at once,
 * so it does not lower and rise again. A slot switch still re-equips fully: from the switch until the new stack shows, vanilla decides.
 */
@Mixin(FirstPersonHandsAndItems.class)
public class FirstPersonReequipMixin26 {
    @Shadow private ItemStack mainHandItem;
    @Unique private int lads$slot = -1;
    @Unique private boolean lads$switching;

    @Inject(method = "tick", at = @At("HEAD"))
    private void lads$slot(LocalPlayer player, CallbackInfo ci) {
        int selected = player.getInventory().getSelectedSlot();
        if (selected != lads$slot) lads$switching = true;
        lads$slot = selected;
    }

    @ModifyReturnValue(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"))
    private boolean lads$sameItem(boolean replace, @Local(argsOnly = true, ordinal = 0) ItemStack shown,
                                  @Local(argsOnly = true, ordinal = 1) ItemStack held) {
        return replace || PvpVisuals.noReequip && !lads$switching && !shown.isEmpty() && shown.getItem() == held.getItem();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void lads$switched(LocalPlayer player, CallbackInfo ci) {
        if (mainHandItem == player.getMainHandItem()) lads$switching = false;
    }
}
