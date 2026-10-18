package com.thelads.smp.features.arise.nb6.mixin;

import com.thelads.smp.features.arise.nb6.AriseNB6Config;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to FishingRodItem to enable hold-to-charge casting mechanics.
 * When the player has no active hook, holding right-click begins charging rather than casting instantly.
 */
@Mixin(FishingRodItem.class)
public abstract class MixinFishingRodItem {

    @Inject(
            method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ladssmp$onUseFishingRod(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        AriseNB6Config config = AriseNB6Config.get();
        if (config.enabled && config.holdToCast && player.fishing == null) {
            player.startUsingItem(hand);
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }
}
