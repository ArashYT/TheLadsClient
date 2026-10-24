package com.thelads.core.v1_8_9.hudextras.mixin;

import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.client.hud.JumpReset;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Jump Reset: your own jumps (EntityPlayer.jump calls this one). Read only. */
@Mixin(EntityLivingBase.class)
public class JumpMixin189 {
    @Inject(method = "jump", at = @At("HEAD"))
    private void lads$jumped(CallbackInfo ci) {
        if (HudExtras.jumpReset && (Object) this == Minecraft.getMinecraft().thePlayer) JumpReset.jumped();
    }
}
