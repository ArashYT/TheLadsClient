package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.thelads.core.v1_20_1.hud.chat.NativeClientTools1201;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ReachDisplay: the distance of each attack the local player starts. */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientToolsAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"), require = 1)
    private void lads$reach(Player player, Entity target, CallbackInfo ci) {
        NativeClientTools1201.attacked(target);
    }
}
