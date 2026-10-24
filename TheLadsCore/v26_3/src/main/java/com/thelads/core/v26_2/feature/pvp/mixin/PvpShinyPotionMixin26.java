package com.thelads.core.v26_2.feature.pvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Glint Colorizer's Shiny Potions: potions with effects glint again (drawing only: hasFoil has no game effect). */
@Mixin(ItemStack.class)
public class PvpShinyPotionMixin26 {
    @ModifyReturnValue(method = "hasFoil", at = @At("RETURN"))
    private boolean lads$shiny(boolean foil) {
        return foil || PvpVisuals26.shiny((ItemStack) (Object) this);
    }
}
