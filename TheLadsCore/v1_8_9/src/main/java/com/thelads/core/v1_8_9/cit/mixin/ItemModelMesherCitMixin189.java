package com.thelads.core.v1_8_9.cit.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.v1_8_9.cit.Cit189;
import net.minecraft.client.renderer.ItemModelMesher;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Custom Item Textures: every stack's model (hand, GUI, ground, item frames, other players) passes Cit189. No allocation per call. */
@Mixin(ItemModelMesher.class)
public abstract class ItemModelMesherCitMixin189 {
    @ModifyReturnValue(method = "getItemModel(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/client/resources/model/IBakedModel;", at = @At("RETURN"))
    private IBakedModel lads$cit(IBakedModel model, ItemStack stack) {
        return Cit189.model(stack, model);
    }
}
