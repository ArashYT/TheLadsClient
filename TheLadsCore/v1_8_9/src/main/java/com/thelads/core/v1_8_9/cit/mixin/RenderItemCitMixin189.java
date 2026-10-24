package com.thelads.core.v1_8_9.cit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v1_8_9.cit.Cit189;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Custom Item Textures: the bow-pulling and cast-rod models a held item switches to (texture.bow_pulling_0 ...) pass Cit189 too, and
 * the glint renderItem draws for an enchanted stack becomes the pack's enchantment effects when one matches.
 */
@Mixin(RenderItem.class)
public abstract class RenderItemCitMixin189 {
    @ModifyExpressionValue(method = "renderItemModelForEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/resources/model/ModelManager;getModel(Lnet/minecraft/client/resources/model/ModelResourceLocation;)Lnet/minecraft/client/resources/model/IBakedModel;"))
    private IBakedModel lads$citInUse(IBakedModel model, @Local(argsOnly = true) ItemStack stack) {
        return Cit189.model(stack, model);
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/RenderItem;renderEffect(Lnet/minecraft/client/resources/model/IBakedModel;)V"))
    private void lads$citGlint(RenderItem renderer, IBakedModel model, Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        if (!Cit189.glint(renderer, stack, model)) original.call(renderer, model);
    }
}
