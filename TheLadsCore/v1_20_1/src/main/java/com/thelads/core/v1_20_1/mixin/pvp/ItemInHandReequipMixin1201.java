package com.thelads.core.v1_20_1.mixin.pvp;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.7 Animations, No re-equip on item changes: a held stack that changed but is still the same item counts as the shown one, so it
 * does not lower and rise again. A slot switch still re-equips fully: from the switch until vanilla shows the new stack, vanilla
 * decides. Forge (Zero SMP) keeps these matches calls before its own re-equip hook, which then sees the same stack and agrees.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandReequipMixin1201 {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private ItemStack mainHandItem;
    @Unique private int lads$slot = -1;
    @Unique private boolean lads$switching;

    @Inject(method = "tick", at = @At("HEAD"))
    private void lads$slot(CallbackInfo ci) {
        int selected = minecraft.player.getInventory().selected;
        if (selected != lads$slot) lads$switching = true;
        lads$slot = selected;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean lads$sameItem(ItemStack shown, ItemStack held, Operation<Boolean> original) {
        return original.call(shown, held) || PvpVisuals.noReequip && !lads$switching && !shown.isEmpty() && shown.getItem() == held.getItem();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void lads$switched(CallbackInfo ci) {
        if (mainHandItem == minecraft.player.getMainHandItem()) lads$switching = false;
    }
}
