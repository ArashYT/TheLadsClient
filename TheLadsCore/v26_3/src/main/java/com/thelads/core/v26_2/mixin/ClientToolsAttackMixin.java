package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeClientTools;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class ClientToolsAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"), require = 1)
    private void ladsReach(Player player, Entity target, CallbackInfo ci) { NativeClientTools.attacked(target); }
}
