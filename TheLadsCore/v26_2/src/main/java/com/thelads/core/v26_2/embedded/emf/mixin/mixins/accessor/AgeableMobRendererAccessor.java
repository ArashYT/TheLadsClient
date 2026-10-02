package com.thelads.core.v26_2.embedded.emf.mixin.mixins.accessor;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AgeableMobRenderer.class)
public interface AgeableMobRendererAccessor {
    @Accessor
    <S extends LivingEntityRenderState, M extends EntityModel<? super S>> M getBabyModel();
}