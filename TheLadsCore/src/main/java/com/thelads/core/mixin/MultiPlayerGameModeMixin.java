package com.thelads.core.mixin;

import com.thelads.core.modules.killbanner.KillTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"), require = 0)
    private void onAttack(Player player, Entity target, CallbackInfo ci) {
        KillTracker.setLastAttackedEntity(target);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ladsSafeTick(CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().player == null) {
            ci.cancel();
        }
    }
}
