package com.thelads.core.v1_20_1.mixin.pvp;

import com.thelads.core.v1_20_1.feature.PredictionFx1201;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pearl Optimizer: shows a client-only pearl on the client-side use. The use packet and cooldown are untouched. */
@Mixin(EnderpearlItem.class)
public class PearlPredictMixin1201 {
    @Inject(method = "use", at = @At("HEAD"))
    private void lads$predictPearl(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (level.isClientSide) PredictionFx1201.throwPearl(level, player);
    }
}
