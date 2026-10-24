package com.thelads.core.v1_8_9.cit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v1_8_9.cit.Cit189;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Custom Item Textures: the glint an enchanted armor piece gets becomes the pack's enchantment effects when one matches. */
@Mixin(LayerArmorBase.class)
public abstract class LayerArmorBaseCitMixin189 {
    @WrapOperation(method = "renderLayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/layers/LayerArmorBase;renderGlint(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/model/ModelBase;FFFFFFF)V"))
    private void lads$citArmorGlint(LayerArmorBase<?> layer, EntityLivingBase entity, ModelBase model, float limbSwing, float limbSwingAmount,
                                    float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale, Operation<Void> original,
                                    @Local ItemStack stack) {
        if (!Cit189.armorGlint(stack, entity, model, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale)) {
            original.call(layer, entity, model, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale);
        }
    }
}
