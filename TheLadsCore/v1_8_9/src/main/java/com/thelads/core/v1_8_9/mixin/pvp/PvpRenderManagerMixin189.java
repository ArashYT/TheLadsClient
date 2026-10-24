package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.v1_8_9.feature.PvpVisuals189;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hitboxes: shown while the module is on, F3+B or not (invisible entities still have none, as in vanilla), drawn in its style.
 * Hide Mob Deaths: a dying mob is culled like an entity off screen.
 */
@Mixin(RenderManager.class)
public class PvpRenderManagerMixin189 {
    @Shadow private boolean debugBoundingBox;

    @Redirect(method = "doRenderEntity", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RenderManager;debugBoundingBox:Z",
        opcode = Opcodes.GETFIELD), require = 0)
    private boolean lads$showHitboxes(RenderManager manager) {
        return debugBoundingBox || PvpVisuals.HITBOXES.on;
    }

    @Inject(method = "renderDebugBoundingBox", at = @At("HEAD"), cancellable = true, require = 0)
    private void lads$hitbox(Entity entity, double x, double y, double z, float yaw, float partial, CallbackInfo ci) {
        if (PvpVisuals189.hitbox(entity, x, y, z, partial)) ci.cancel();
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void lads$hideMobDeath(Entity entity, ICamera camera, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (PvpVisuals189.hidden(entity)) cir.setReturnValue(false);
    }
}
