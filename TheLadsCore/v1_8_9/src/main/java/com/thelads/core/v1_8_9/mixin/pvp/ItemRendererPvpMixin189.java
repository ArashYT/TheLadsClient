package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overlays' fire options and 1.7 Animations' No re-equip on item changes. OptiFine patches ItemRenderer: require = 0 keeps vanilla
 * if a point moves. The fire overlay sets one color (alpha 0.9) for both of its quads.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererPvpMixin189 {
    @Shadow @Final private Minecraft mc;

    @Inject(method = "renderFireInFirstPerson", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsHideFire(float partialTicks, CallbackInfo ci) {
        if (PvpVisuals.fireOpacity <= 0 || PvpVisuals.hideFireWithResistance && mc.thePlayer.isPotionActive(Potion.fireResistance)) ci.cancel();
    }

    @ModifyArg(method = "renderFireInFirstPerson", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V"), index = 3, require = 0)
    private float ladsFireOpacity(float alpha) {
        return alpha * PvpVisuals.fireOpacity;
    }

    /** Forge asks the item whether a changed stack replays the equip animation; with the option only a slot or item change does. */
    @Redirect(method = "updateEquippedItem", at = @At(value = "INVOKE", remap = false,
        target = "Lnet/minecraft/item/Item;shouldCauseReequipAnimation(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;Z)Z"), require = 0)
    private boolean ladsReequip(Item item, ItemStack from, ItemStack to, boolean slotChanged) {
        return PvpVisuals.noReequip ? slotChanged || from.getItem() != to.getItem() : item.shouldCauseReequipAnimation(from, to, slotChanged);
    }
}
