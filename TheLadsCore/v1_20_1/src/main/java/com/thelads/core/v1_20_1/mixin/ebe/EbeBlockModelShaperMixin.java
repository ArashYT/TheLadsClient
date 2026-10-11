package com.thelads.core.v1_20_1.mixin.ebe;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v1_20_1.feature.ebe.BakedBlockEntities1201;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bakes the block entity models on every resource reload and returns them for their block states. getBlockModel reads the
 * model from the state, not from the shaper's cache: ModernFix's dynamic resources overwrites getBlockModel and never reads
 * that cache (injectors still apply to an overwritten method).
 */
@Mixin(BlockModelShaper.class)
public abstract class EbeBlockModelShaperMixin {
    @Shadow private Map<BlockState, BakedModel> modelByStateCache;
    @Shadow @Final private ModelManager modelManager;

    @Inject(method = "replaceCache", at = @At("TAIL"))
    private void lads$ebeInstall(Map<BlockState, BakedModel> map, CallbackInfo ci) {
        BakedBlockEntities1201.install(modelByStateCache, modelManager);
    }

    @ModifyReturnValue(method = "getBlockModel", at = @At("RETURN"))
    private BakedModel lads$ebeModel(BakedModel model, @Local(argsOnly = true) BlockState state) {
        BakedModel baked = ((BakedBlockEntities1201.Flag) state).lads$model();
        return baked != null ? baked : model;
    }
}
