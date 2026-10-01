package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.LegacySwing189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LegacySwing (LegacySwing189): the legacy swing of the held item, and no re-equip dip, as 26.x LegacySwingMixin. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Shadow private ItemStack itemToRender;
    @Shadow private float equippedProgress, prevEquippedProgress;
    @Shadow private int equippedItemSlot;

    @Inject(method = "transformFirstPersonItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsSwing(float equipProgress, float swingProgress, CallbackInfo ci) {
        if (LegacySwing189.transform(equipProgress, swingProgress)) ci.cancel();
    }

    @Inject(method = "updateEquippedItem", at = @At("RETURN"), require = 1)
    private void ladsNoReequip(CallbackInfo ci) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null || !LegacySwing189.enabled()) return;
        itemToRender = player.inventory.getCurrentItem();
        equippedItemSlot = player.inventory.currentItem;
        equippedProgress = prevEquippedProgress = 1;
    }
}
