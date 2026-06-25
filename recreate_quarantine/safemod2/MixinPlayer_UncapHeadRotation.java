package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class MixinPlayer_UncapHeadRotation {
    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void onTick(net.minecraft.world.level.Level worldIn, net.minecraft.core.BlockPos pos) {
        // Custom logic here
    }
}
