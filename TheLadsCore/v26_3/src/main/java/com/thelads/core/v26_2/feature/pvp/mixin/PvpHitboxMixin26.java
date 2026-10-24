package com.thelads.core.v26_2.feature.pvp.mixin;

import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitboxes: each entity's F3+B gizmos in the module's style (the server-entity debug boxes stay vanilla). */
@Mixin(EntityHitboxDebugRenderer.class)
public class PvpHitboxMixin26 {
    @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
    private void lads$hitbox(Entity entity, float partial, boolean serverEntity, CallbackInfo ci) {
        if (!serverEntity && PvpVisuals26.hitbox(entity, partial)) ci.cancel();
    }
}
