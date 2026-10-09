package com.thelads.core.v1_20_1.mixin.ebe;

import com.thelads.core.v1_20_1.feature.ebe.BakedBlockEntities1201;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the baked block entity models to the block-state model cache on every resource reload. */
@Mixin(BlockModelShaper.class)
public abstract class EbeBlockModelShaperMixin {
    @Shadow private Map<BlockState, BakedModel> modelByStateCache;

    @Inject(method = "replaceCache", at = @At("TAIL"))
    private void lads$ebeInstall(Map<BlockState, BakedModel> map, CallbackInfo ci) {
        modelByStateCache = BakedBlockEntities1201.install(modelByStateCache);
    }
}
