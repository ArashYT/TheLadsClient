package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.entity.misc;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_11.embedded.etf.ETF;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.HoldsETFRenderState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFEntity;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

@Mixin(BlockEntityRenderDispatcher.class)
public class MixinBlockEntityRenderDispatcher {

    private static final String RENDER_METHOD =
            "submit";

    @Inject(method = RENDER_METHOD, at = @At(value = "HEAD"))
    private static <S extends BlockEntityRenderState> void etf$grabContext(final CallbackInfo ci, @Local(argsOnly = true) S state, @Share("state_etf") LocalRef<ETFEntityRenderState> stateRef) {

        var etf = ((HoldsETFRenderState) state).etf$getState();
        if (etf != null) {
            ETFState.mount(etf);
        }
        stateRef.set(etf);
    }

    @Inject(method = RENDER_METHOD, at = @At(value = "RETURN"))
    private static void etf$clearContext(CallbackInfo ci, @Share("state_etf") LocalRef<ETFEntityRenderState> stateRef) {
        if (stateRef.get() != null) {
            ETFState.stackVerify(stateRef.get());
            ETFState.unMount();
        }

    }


}
