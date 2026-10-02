package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.rendering;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.state.EMFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFState;

import net.minecraft.world.entity.Entity;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    private static final String RENDER_ETF =
            "render"
            ;


    @Inject(method = RENDER_ETF, at = @At(value = "RETURN"))
    private <E extends Entity> void emf$endOfRender( CallbackInfo ci, @Local(argsOnly = true) E entity) {
        if (EMFState.announceModels) {
            EMFState.anounceModels(EMFState.state());
        }
    }
    

    //region shadow modification

    private static final String SHADOW_RENDER_ETF =
            "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V"
            ;

    
         @ModifyExpressionValue(method = RENDER_ETF, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getShadowRadius(Lnet/minecraft/world/entity/Entity;)F"))
    private float radius(float original) {
        var state = EMFState.state();
        if (state != null && !Float.isNaN(state.shadowSize())) {
            return state.shadowSize();
        }
        return original;
    }
    
    @ModifyExpressionValue(method = RENDER_ETF, at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shadowStrength:F", opcode = Opcodes.GETFIELD))
    private float strength(float original) {
        var state = EMFState.state();
        if (state != null && !Float.isNaN(state.shadowOpacity())) {
            return state.shadowOpacity();
        }
        return original;
    }
    
    @Inject(method = RENDER_ETF, at = @At(value = "INVOKE", target = SHADOW_RENDER_ETF))
    private void preShadow(CallbackInfo ci, @Local PoseStack poseStack, @Local(argsOnly = true) Entity entity) {
        var state = EMFState.state();
        if (state == null || !state.needsToModifyShadow()) return;
    
        entity.setPos(
                entity.position().x + (Float.isNaN(state.shadowX()) ? 0 : state.shadowX()),
                entity.position().y,
                entity.position().z + (Float.isNaN(state.shadowZ()) ? 0 : state.shadowZ())
        );
    
        poseStack.translate(Float.isNaN(state.shadowX()) ? 0 : state.shadowX(), 0, Float.isNaN(state.shadowZ()) ? 0 : state.shadowZ());
    }
    
    @Inject(method = RENDER_ETF, at = @At(value = "INVOKE", target = SHADOW_RENDER_ETF, shift = At.Shift.AFTER))
    private void postShadow(CallbackInfo ci, @Local PoseStack poseStack, @Local(argsOnly = true) Entity entity) {
        var state =EMFState.state();
        if (state == null || !state.needsToModifyShadow()) return;
    
        entity.setPos(
                entity.position().x + (Float.isNaN(state.shadowX()) ? 0 : -state.shadowX()),
                entity.position().y,
                entity.position().z + (Float.isNaN(state.shadowZ()) ? 0 : -state.shadowZ())
        );
    
        poseStack.translate(Float.isNaN(state.shadowX()) ? 0 : -state.shadowX(), 0, Float.isNaN(state.shadowZ()) ? 0 : -state.shadowZ());
    }

    //endregion

    //region flame modification

    @ModifyExpressionValue(method = "renderFlame", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBbWidth()F"))
    private float width(float original, @Local PoseStack pose) {
        var state = EMFState.state();
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
    
    @ModifyExpressionValue(method = "renderFlame", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBbHeight()F"))
    private float height(float original) {
        var state = EMFState.state();
        if (state != null && !Float.isNaN(state.fireHeight())) {
            return state.fireHeight();
        }
        return original;
    }

    //endregion
}
