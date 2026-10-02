package com.thelads.core.v26_2.embedded.emf.mixin.mixins.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.emf.EMFManager;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFEntityRenderState;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;


import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SkullSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class MixinBlockEntityWithoutLevelRenderer_skull {

    @Shadow
    @Nullable
    private SpecialModelRenderer<?> specialRenderer;

    private static final String RENDER = "submit";

    @Inject(method = RENDER, at = @At(value = "HEAD"))
    private void emf$setRenderFactory(CallbackInfo ci) {
        var state = EMFState.state();
        if (specialRenderer instanceof SkullSpecialRenderer && state != null) {
            state.setLayerFactory(
                    RenderTypes::entityCutoutZOffset
            );
        }
    }



}

