package com.thelads.core.mixin;

import com.thelads.core.modules.killbanner.KillTracker;
import com.thelads.core.util.VBLivingEntityExtension;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements VBLivingEntityExtension {

    @Unique private float vb_prevRot = 0f;
    @Unique private float vb_rot = 0f;

    @Inject(method = "baseTick", at = @At("TAIL"), require = 0)
    private void vb$postBaseTick(CallbackInfo ci) {
        vb_prevRot = vb_rot;
    }

    @Override public float vB$getPrevRot() { return vb_prevRot; }
    @Override public float vB$getRot()     { return vb_rot; }
    @Override public void  vB$setRot(float value) { vb_rot = value; }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), require = 0)
    private void onHandleEntityEvent(byte eventId, CallbackInfo ci) {
        if (eventId == 3) {
            KillTracker.onEntityDeath((Entity) (Object) this);
        }
    }
}
