package com.thelads.core.v1_8_9.render.mixin;

import com.thelads.core.v1_8_9.render.ItemCull189;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads Flerovium (1.8.5): ItemCull189 filters the quad lists of every item model RenderItem draws (with or without OptiFine). */
@Mixin(RenderItem.class)
public abstract class RenderItemCullMixin {
    @Inject(method = "renderItemModelTransform", at = @At("HEAD"))
    private void lads$context(ItemStack stack, IBakedModel model, ItemCameraTransforms.TransformType type, CallbackInfo ci) { ItemCull189.context(type); }

    @Inject(method = "renderItemModelTransform", at = @At("RETURN"))
    private void lads$contextEnd(ItemStack stack, IBakedModel model, ItemCameraTransforms.TransformType type, CallbackInfo ci) { ItemCull189.context(null); }

    @Inject(method = "renderModel(Lnet/minecraft/client/resources/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V", at = @At("HEAD"))
    private void lads$model(IBakedModel model, int color, ItemStack stack, CallbackInfo ci) { com.thelads.core.perf.RenderQa.itemStart(); ItemCull189.begin(); }

    @Inject(method = "renderModel(Lnet/minecraft/client/resources/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V", at = @At("RETURN"))
    private void lads$modelEnd(IBakedModel model, int color, ItemStack stack, CallbackInfo ci) { com.thelads.core.perf.RenderQa.itemEnd(); } // QA timing only

    @ModifyVariable(method = "renderQuads", at = @At("HEAD"), index = 2)
    private List<BakedQuad> lads$quads(List<BakedQuad> quads) { return ItemCull189.filter(quads); }
}
