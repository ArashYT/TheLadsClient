package com.thelads.core.v26_2.embedded.etf.mixin.mixins.entity.misc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v26_2.embedded.etf.ETF;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import com.thelads.core.v26_2.embedded.etf.features.state.HoldsETFRenderState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;

@Mixin(BlockEntityRenderState.class)
public class MixinBlockEntityRenderState implements HoldsETFRenderState {

    @Unique
    private ETFEntityRenderState etf$state = null;

    @Override
    public @NotNull ETFEntityRenderState etf$getState() {
        assert this.etf$state != null; // something has gone badly wrong usage wise otherwise
        return etf$state;
    }

    @Override
    public void etf$initState(@NotNull final ETFEntity entity) {
        etf$state = ETFEntityRenderState.forEntity(entity);
        etf$state.setVanillaBlockState((BlockEntityRenderState) (Object) this);
    }

    @Inject(method = "extractBase",
            at = @At(value = "HEAD"))
    private static void etf$createRenderState(final BlockEntity blockEntity, final BlockEntityRenderState blockEntityRenderState, final ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, final CallbackInfo ci) {
        var holder = (HoldsETFRenderState) blockEntityRenderState;
        holder.etf$initState((ETFEntity) blockEntity);
    }

    @ModifyExpressionValue(method = "extractBase",
            at = @At(value = "INVOKE",target =
                    "Lnet/minecraft/util/LightCoordsUtil;getLightCoords(Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/core/BlockPos;)I"
            ))
    private static int etf$vanillaLightOverride(final int light, @Local(argsOnly = true) BlockEntityRenderState blockEntityRenderState) {
        //if need to override vanilla brightness behaviour
        //change return with overridden light value still respecting higher block and sky lights
        return ETF.config().getConfig().getLightOverrideBE(light, ((HoldsETFRenderState) blockEntityRenderState).etf$getState());
    }
}
