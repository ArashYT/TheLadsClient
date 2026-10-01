package com.thelads.core.v26_2.embedded.emf.mixin.mixins.rendering.submits;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFEntityRenderState;

@Mixin(FlameFeatureRenderer.class)
public abstract class MixinFlameFeatureRenderer {

    private static final String METHOD = "prepare";


    @ModifyExpressionValue(method = METHOD, at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;boundingBoxWidth:F", opcode = Opcodes.GETFIELD))
    private float width(float original, @Local EntityRenderState vanilla, @Local PoseStack.Pose pose) {
        var state = EMFEntityRenderState.from(vanilla);
        if (state == null) return original;

        if (state.needsToModifyFire()) {
            pose.translate(
                    Float.isNaN(state.fireX()) ? 0 : state.fireX(),
                    Float.isNaN(state.fireY()) ? 0 : state.fireY(),
                    Float.isNaN(state.fireZ()) ? 0 : state.fireZ()
            );

            if (!Float.isNaN(state.fireScale())) {
                return state.fireScale();
            }
        }


        return original;
    }

    @ModifyExpressionValue(method = METHOD, at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;boundingBoxHeight:F", opcode = Opcodes.GETFIELD))
    private float height(float original, @Local EntityRenderState vanilla) {
        var state = EMFEntityRenderState.from(vanilla);
        if (state != null && !Float.isNaN(state.fireHeight())) {
            return state.fireHeight();
        }
        return original;
    }



}