package com.thelads.core.v1_8_9.mixin.visuals;

import com.thelads.core.v1_8_9.feature.PvpVisuals189;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Despawn (the item is skipped on its blink's off frames) and Loot Beams (a beam after the item) on 1.8.9's dropped items. */
@Mixin(RenderEntityItem.class)
public abstract class RenderEntityItemVisualsMixin189 {
    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void ladsDespawnBlink(EntityItem item, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        if (PvpVisuals189.despawnHidden(item)) ci.cancel();
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At("RETURN"))
    private void ladsLootBeam(EntityItem item, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        ItemStack stack = item.getEntityItem();
        if (stack != null) PvpVisuals189.beam(x, y, z, stack.getRarity().ordinal());
    }
}
