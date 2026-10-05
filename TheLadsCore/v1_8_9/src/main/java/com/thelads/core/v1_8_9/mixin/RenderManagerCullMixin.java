package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.EntityCulling189;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity Culling: an entity the culling thread found hidden behind blocks is not drawn in the world pass (RenderGlobal draws
 * every entity through renderEntityStatic; GUI previews use renderEntityWithPosYaw and are untouched). Its name tag still is,
 * through vanilla's own name-only path (renderWitherSkull), so tags show through walls as before. OptiFine patches this class:
 * require = 0 leaves everything drawn if the method ever moves.
 */
@Mixin(RenderManager.class)
public abstract class RenderManagerCullMixin {
    @Shadow private boolean renderOutlines;
    @Shadow private double renderPosX;
    @Shadow private double renderPosY;
    @Shadow private double renderPosZ;

    @Shadow public abstract void renderWitherSkull(Entity entity, float partialTicks);

    @Inject(method = "renderEntityStatic", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsCull(Entity entity, float partialTicks, boolean hideDebugBox, CallbackInfoReturnable<Boolean> cir) {
        if (!EntityCulling189.skip(entity, renderOutlines, renderPosX, renderPosY, renderPosZ)) return;
        renderWitherSkull(entity, partialTicks);
        cir.setReturnValue(true);
    }
}
