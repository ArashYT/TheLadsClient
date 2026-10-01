// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v1_21_1.embedded.cushions.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.cushions.CushionTracker;

// Pre-render-state era, 1.21-1.21.11: the backport renderer has no render state, so we key on
// the entity directly. Cancel the whole render for baked cushions - their model is in the
// chunk mesh. 1.20.1 uses CushionRendererLegacyMixin (5-arg renderNameTag).
//
// Extends EntityRenderer so shouldShowName/renderNameTag resolve through the vanilla
// superclass: the legacy Mixin AP emits no @Shadow-method refmap entries, and the backport
// renderer does not redeclare those EntityRenderer members, so @Shadow of them breaks in
// obfuscated production. 1.20.1 uses CushionRendererLegacyMixin (5-arg renderNameTag).
@Mixin(targets = "com.leclowndu93150.cushionbackport.client.CushionRenderer")
public abstract class CushionRendererOldMixin extends EntityRenderer<Entity> {
    protected CushionRendererOldMixin(final EntityRendererProvider.Context context) {
        super(context);
    }

    // Target EntityRenderer's erased (bridge) signature, not the backport's typed render(Cushion, …)
    // override: only the Minecraft supertype carries the obfuscation mapping the refmap AP needs.
    @Inject(
        method = "render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void optimizedcushions$skipBaked(
        final Entity cushion,
        final float yaw,
        final float partialTicks,
        final PoseStack poseStack,
        final MultiBufferSource bufferSource,
        final int packedLight,
        final CallbackInfo ci
    ) {
        if (CushionTracker.isBaked(cushion)) {
            // The model is in the chunk mesh; keep the nameplate like the >=1.21.5 path does.
            if (this.shouldShowName(cushion)) {
                this.renderNameTag(cushion, cushion.getDisplayName(), poseStack, bufferSource, packedLight, partialTicks);
            }
            ci.cancel();
        }
    }
}
