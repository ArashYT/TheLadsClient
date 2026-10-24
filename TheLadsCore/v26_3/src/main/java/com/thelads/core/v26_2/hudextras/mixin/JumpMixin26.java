package com.thelads.core.v26_2.hudextras.mixin;

import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.client.hud.JumpReset;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Jump Reset: your own jumps (Player.jumpFromGround calls this one). Read only. */
@Mixin(LivingEntity.class)
public class JumpMixin26 {
    @Inject(method = "jumpFromGround()V", at = @At("HEAD"))
    private void lads$jumped(CallbackInfo ci) {
        if (HudExtras.jumpReset && (Object) this == Minecraft.getInstance().player) JumpReset.jumped();
    }
}
