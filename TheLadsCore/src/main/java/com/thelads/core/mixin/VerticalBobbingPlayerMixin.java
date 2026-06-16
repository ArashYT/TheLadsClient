package com.thelads.core.mixin;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.util.VBLivingEntityExtension;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class VerticalBobbingPlayerMixin extends LivingEntity {

    protected VerticalBobbingPlayerMixin() {
        super(null, null);
    }

    @Inject(method = "aiStep", at = @At("TAIL"), require = 0)
    private void vb$tickBobbing(CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("VerticalBobbing");
        if (m == null || !m.isEnabled()) return;

        float speed = this.getSpeed();
        float f = !this.isCrouching() && speed > 0.0f
            ? (float) (Math.atan(-this.getDeltaMovement().y * 0.2) * 15.0)
            : 0.0f;
        VBLivingEntityExtension ext = (VBLivingEntityExtension) this;
        ext.vB$setRot(ext.vB$getRot() + (f - ext.vB$getRot()) * 0.8f);
    }
}
