package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeKillBanner;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** KillBanner headshots: where the local player's attack landed (NativeKillBanner.attacked). */
@Mixin(MultiPlayerGameMode.class)
public class KillBannerAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"), require = 1)
    private void lads$attack(Player player, Entity target, CallbackInfo ci) {
        NativeKillBanner.attacked(target);
    }
}
