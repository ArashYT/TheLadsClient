package com.thelads.core.v26_2.mixin.ebe;

import com.thelads.core.v26_2.feature.ebe.BakedBlockEntities263;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.block.LoadedBlockModels;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.level.block.state.BlockState;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the baked block entity models to every model reload (see BakedBlockEntities263). */
@Mixin(ModelManager.class)
public abstract class EbeModelManagerMixin {
    @Inject(method = "loadModels", at = @At("HEAD"))
    private static void lads$ebePrepare(SpriteLoader.Preparations blockAtlas, SpriteLoader.Preparations itemAtlas, ModelBakery bakery,
                                        LoadedBlockModels blockModels, Object2IntMap<BlockState> modelGroups, EntityModelSet entityModelSet,
                                        Executor taskExecutor, CallbackInfoReturnable<CompletableFuture<?>> cir) {
        BakedBlockEntities263.prepare(blockAtlas, entityModelSet);
    }

    @Inject(method = "createBlockStateToModelDispatch", at = @At("RETURN"))
    private static void lads$ebeBake(Map<BlockState, BlockStateModel> bakedModels, BlockStateModel missingModel,
                                     CallbackInfoReturnable<Map<BlockState, BlockStateModel>> cir) {
        BakedBlockEntities263.bake(cir.getReturnValue());
    }

    @Inject(method = "apply", at = @At("TAIL"))
    private void lads$ebeApply(CallbackInfo ci) {
        BakedBlockEntities263.apply();
    }
}
