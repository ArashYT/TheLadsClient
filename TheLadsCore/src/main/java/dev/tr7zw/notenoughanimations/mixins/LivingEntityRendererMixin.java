/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.entity.LivingEntityRenderer
 *  net.minecraft.client.renderer.entity.state.ArmedEntityRenderState
 *  net.minecraft.client.renderer.entity.state.LivingEntityRenderState
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.entity.LivingEntity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.tr7zw.notenoughanimations.mixins;

import dev.tr7zw.notenoughanimations.access.ExtendedItemStackRenderState;
import dev.tr7zw.notenoughanimations.access.ExtendedLivingRenderState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LivingEntityRenderer.class})
public class LivingEntityRendererMixin {
    @Inject(method={"extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V"}, at={@At(value="HEAD")})
    private void addEntityToRenderState(LivingEntity livingEntity, LivingEntityRenderState livingEntityRenderState, float f, CallbackInfo ci) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("NotEnoughAnimations").isEnabled()) {
            return;
        }
        ((ExtendedLivingRenderState)livingEntityRenderState).setEntity(livingEntity);
        if (livingEntityRenderState instanceof ArmedEntityRenderState) {
            ArmedEntityRenderState armed = (ArmedEntityRenderState)livingEntityRenderState;
            ((ExtendedItemStackRenderState)armed.leftHandItemState).setItemStack(livingEntity.getItemHeldByArm(HumanoidArm.LEFT));
            ((ExtendedItemStackRenderState)armed.rightHandItemState).setItemStack(livingEntity.getItemHeldByArm(HumanoidArm.RIGHT));
        }
    }
}

