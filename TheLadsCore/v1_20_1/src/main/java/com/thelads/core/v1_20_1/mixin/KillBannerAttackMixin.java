package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.feature.NativeKillBanner1201;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class KillBannerAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"), require = 0)
    private void lads$attack(Player player, Entity target, CallbackInfo ci) {
        NativeKillBanner1201.attacked(target);
    }
}
