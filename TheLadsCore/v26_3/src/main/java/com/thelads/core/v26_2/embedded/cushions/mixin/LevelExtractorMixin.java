// Derived from Optimized Cushions 1.0.0 (tag 1.0.0) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Cushion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.thelads.core.v26_2.embedded.cushions.CushionTracker;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    // Named cushions keep the vanilla path so the name tag still renders (their model
    // submit is skipped in CushionRendererMixin). Changes the call's result rather than
    // isEntityVisible itself so F3+B hitboxes still see baked cushions.
    // 1.9.12 perf: @ModifyExpressionValue instead of @WrapOperation. The wrapper allocated an Operation and boxed all
    // eight arguments for every entity every frame (11.7 % of all allocations in the Legion bench's static scene).
    @ModifyExpressionValue(
        method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;isEntityVisible(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDFJ)Z"
        )
    )
    private boolean optimizedcushions$skipBakedCushions(boolean visible, @Local Entity entity) {
        return visible && !(entity instanceof Cushion cushion && CushionTracker.isBaked(cushion) && !cushion.hasCustomName());
    }
}
