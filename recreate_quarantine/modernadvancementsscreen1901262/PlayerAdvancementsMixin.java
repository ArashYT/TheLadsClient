package com.thelads.core.mixin.auto.modernadvancementsscreen1901262;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerAdvancementsMixin {
    @Inject(method = "award", at = @At("HEAD"), require = 0)
    private void onAward(net.minecraft.resources.Identifier advancementId, CallbackInfo ci) {
        // Custom logic here
    }
}
