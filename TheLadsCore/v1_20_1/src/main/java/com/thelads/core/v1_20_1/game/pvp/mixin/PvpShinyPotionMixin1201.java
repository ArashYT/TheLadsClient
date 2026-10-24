package com.thelads.core.v1_20_1.game.pvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.v1_20_1.game.pvp.PvpVisuals1201;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Glint Colorizer's Shiny Potions: potions with effects glint again (drawing only: hasFoil has no game effect). */
@Mixin(ItemStack.class)
public class PvpShinyPotionMixin1201 {
    @ModifyReturnValue(method = "hasFoil", at = @At("RETURN"))
    private boolean lads$shiny(boolean foil) {
        return foil || PvpVisuals1201.shiny((ItemStack) (Object) this);
    }
}
