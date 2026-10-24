package com.thelads.core.v1_8_9.mixin.visuals;

import com.thelads.core.modules.UhcOverlayModule;
import com.thelads.core.v1_8_9.feature.PvpVisuals189;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * UHC Overlay on 1.8.9: every inventory and hotbar icon goes through renderItemIntoGUI, which pushes a matrix and moves to the slot's
 * center (setupGuiTransform) before drawing the model. A UHC item is scaled right there, about that center; the count and the
 * durability bar (renderItemOverlayIntoGUI, drawn after) keep their size.
 */
@Mixin(RenderItem.class)
public abstract class RenderItemUhcMixin189 {
    @Unique private float ladsUhcScale = 1;

    @Inject(method = "renderItemIntoGUI(Lnet/minecraft/item/ItemStack;II)V", at = @At("HEAD"))
    private void ladsUhcItem(ItemStack stack, int x, int y, CallbackInfo ci) {
        ladsUhcScale = UhcOverlayModule.scale(PvpVisuals189.uhcGroup(stack));
    }

    @Inject(method = "setupGuiTransform(IIZ)V", at = @At("RETURN"))
    private void ladsUhcScale(int x, int y, boolean gui3d, CallbackInfo ci) {
        if (ladsUhcScale != 1) GlStateManager.scale(ladsUhcScale, ladsUhcScale, ladsUhcScale);
    }

    @Inject(method = "renderItemIntoGUI(Lnet/minecraft/item/ItemStack;II)V", at = @At("RETURN"))
    private void ladsUhcDone(ItemStack stack, int x, int y, CallbackInfo ci) {
        ladsUhcScale = 1;
    }
}
