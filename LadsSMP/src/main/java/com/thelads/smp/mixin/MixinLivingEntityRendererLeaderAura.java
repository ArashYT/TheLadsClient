package com.thelads.smp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.features.LeaderRenderStateExtension;
import com.thelads.smp.features.ZombieImprovementsManager;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.tags.EntityTypeTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Consolidates 'zombieimprovements': Renders the red energy swirl aura layer around Leader Zombies.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRendererLeaderAura<S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Shadow
    public abstract M getModel();

    @ModifyExpressionValue(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"
            )
    )
    private boolean ladssmp$renderZombiePowerLayer(
            boolean original,
            @Local(argsOnly = true) S state,
            @Local(argsOnly = true) PoseStack poseStack,
            @Local(argsOnly = true) SubmitNodeCollector submitNodeCollector
    ) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (cfg.zombieImprovements
                && cfg.zombieLeaderRedAura
                && state.entityType != null
                && state.entityType.builtInRegistryHolder().is(EntityTypeTags.ZOMBIES)
                && state instanceof HumanoidRenderState
                && ((LeaderRenderStateExtension) state).ladssmp$isLeader()) {
            float h = state.ageInTicks;
            M entityModel = this.getModel();
            submitNodeCollector.order(1).submitModel(
                    entityModel,
                    state,
                    poseStack,
                    RenderTypes.energySwirl(
                            ZombieImprovementsManager.id("textures/entity/zombie/leader_zombie_aura.png"),
                            (h * 0.01f) % 1.0f,
                            (h * 0.01f) % 1.0f
                    ),
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    -8355712,
                    null,
                    state.outlineColor
            );
        }
        return original;
    }
}
