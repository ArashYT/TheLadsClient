package com.thelads.core.v26_2.embedded.emf.mixin.mixins.rendering;

import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v26_2.embedded.emf.EMF;
import com.thelads.core.v26_2.embedded.emf.config.EMFConfig;
import com.thelads.core.v26_2.embedded.emf.EMFManager;
import com.thelads.core.v26_2.embedded.emf.utils.EMFUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import net.minecraft.client.renderer.item.SpecialModelWrapper;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(net.minecraft.client.renderer.block.LoadedBlockModels.class)
public class Mixin_SpecialModelRenderers_SetCurrentSpecifiedModel {

    @Unique
    private static final List<String> emf$renderers = new ArrayList<>();

    @Inject(method =
            "bake"
            , at = @At(value = "RETURN"))
    private static void emf$clearMarker(CallbackInfoReturnable<Map<Block, SpecialModelRenderer<?>>> cir) {
        if (EMF.testForForgeLoadingError()) return;
        EMFManager.getInstance().currentSpecifiedModelLoading.set("");
        EMFManager.getInstance().currentBlockEntityTypeLoading = null;
        if (EMF.config().getConfig().logModelCreationData || EMF.config().getConfig().automaticModelExporting)
            EMFUtils.log("Identified SPECIAL block entity renderers: " + emf$renderers);
        emf$renderers.clear();
    }

    @Inject(method = "lambda$bake$1", at = @At(value = "HEAD"))
    private static void setEmf$Model(CallbackInfo ci, @Local(argsOnly = true) BlockState blockState, @Local(argsOnly = true) net.minecraft.client.renderer.block.model.BlockModel.Unbaked unbakedModel) {
        if (unbakedModel instanceof SpecialModelWrapper<?>) {
            setModel(blockState);
        }
    }

    @Unique
    private static void setModel(BlockState state) {
        //mark which variant is currently specified for use by otherwise identical block entity renderers
        if (EMF.testForForgeLoadingError()) return;

        // TODO DONT FORGET TO REPLICATE CHANGES IN REGULAR RENDERERS
        if (state.is(Blocks.ENCHANTING_TABLE))
            EMFManager.getInstance().currentSpecifiedModelLoading.set("enchanting_book");
        else if (state.is(Blocks.LECTERN))
            EMFManager.getInstance().currentSpecifiedModelLoading.set("lectern_book");
        else if (state.is(Blocks.CHEST))
            EMFManager.getInstance().currentSpecifiedModelLoading.set("chest");
        else if (state.is(Blocks.ENDER_CHEST))
            EMFManager.getInstance().currentSpecifiedModelLoading.set("ender_chest");
        else if (state.is(Blocks.TRAPPED_CHEST))
            EMFManager.getInstance().currentSpecifiedModelLoading.set("trapped_chest");
        else {
            try {
                Identifier id = BuiltInRegistries.BLOCK.wrapAsHolder(state.getBlock()).unwrapKey().get().identifier();

                if (id.getNamespace().equals("minecraft")) {
                    EMFManager.getInstance().currentSpecifiedModelLoading.set(id.getPath());
                } else {
                    EMFManager.getInstance().currentSpecifiedModelLoading.set(id.getNamespace() + ":" + id.getPath());
                }
            } catch (Exception e) {
                EMFManager.getInstance().currentSpecifiedModelLoading.set(state.toString());
            }
        }
        emf$renderers.add(EMFManager.getInstance().currentSpecifiedModelLoading.get());
        if (EMF.config().getConfig().logModelCreationData)
            EMFUtils.log("Seeing SPECIAL block entity renderer init for: " + EMFManager.getInstance().currentSpecifiedModelLoading.get());
    }

}
