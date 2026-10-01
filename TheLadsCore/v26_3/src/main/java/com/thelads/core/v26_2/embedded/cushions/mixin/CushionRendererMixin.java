// Derived from Optimized Cushions 1.0.0 (tag 1.0.0) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.CushionRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.CushionRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.decoration.Cushion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.cushions.CushionRenderStateExt;
import com.thelads.core.v26_2.embedded.cushions.CushionTracker;

@Mixin(CushionRenderer.class)
public abstract class CushionRendererMixin extends EntityRenderer<Cushion, CushionRenderState> {
    protected CushionRendererMixin(final EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/decoration/Cushion;Lnet/minecraft/client/renderer/entity/state/CushionRenderState;F)V",
        at = @At("TAIL")
    )
    private void optimizedcushions$flagBaked(final Cushion cushion, final CushionRenderState state, final float partialTicks, final CallbackInfo ci) {
        ((CushionRenderStateExt)state).optimizedcushions$setBaked(CushionTracker.isBaked(cushion));
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/CushionRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void optimizedcushions$skipModel(
        final CushionRenderState state,
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final CameraRenderState camera,
        final CallbackInfo ci
    ) {
        if (((CushionRenderStateExt)state).optimizedcushions$isBaked()) {
            // The cushion is part of the chunk mesh; keep only what super.submit would do
            // (cushions are not Leashable, so that is just the name display).
            this.submitNameDisplay(state, poseStack, submitNodeCollector, camera);
            ci.cancel();
        }
    }
}
