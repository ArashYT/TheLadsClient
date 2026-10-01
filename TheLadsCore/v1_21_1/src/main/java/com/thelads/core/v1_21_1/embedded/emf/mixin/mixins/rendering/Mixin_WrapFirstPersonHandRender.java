package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.rendering;


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.thelads.core.v1_21_1.embedded.emf.EMFManager;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.state.EMFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFState;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;

@Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
public class Mixin_WrapFirstPersonHandRender {
    private static final String RENDER = "renderHandsWithItems";

    @WrapMethod(method = RENDER)
    private void wrapRenderHandsWithItems(
            float a, PoseStack b, net.minecraft.client.renderer.MultiBufferSource.BufferSource c, LocalPlayer d, int e, Operation<Void> original
    ) {
        pre();

        try {
            original.call(a, b, c, d, e);
        } finally {
            post();
        }
    }

    @Unique
    private static void pre() {
        EMFManager.getInstance().entityRenderCount++;
        EMFState.isInHand = true;
        var state = EMFEntityRenderState.manualPlayerState();
        if (state != null) {
            ETFState.mount(state);
        }
    }

    @Unique
    private static void post() {
        var state = EMFState.state();
        if (state != null && state.isManualPlayerState()) {
            ETFState.unMount();
        }
        EMFState.isInHand = false;
    }

}

