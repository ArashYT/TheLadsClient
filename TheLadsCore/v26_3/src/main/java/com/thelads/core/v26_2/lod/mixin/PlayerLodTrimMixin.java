package com.thelads.core.v26_2.lod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.perf.PlayerLod;
import com.thelads.core.v26_2.lod.LodFlags26;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Player LOD: armor looks trimless beyond the trim distance (the trim component read returns null). */
@Mixin(EquipmentLayerRenderer.class)
abstract class PlayerLodTrimMixin {
    @ModifyExpressionValue(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object lads$lodTrim(Object trim, @Local(argsOnly = true, ordinal = 0) Object state) {
        return trim instanceof ArmorTrim && LodFlags26.has(state, PlayerLod.TRIM) ? null : trim;
    }
}
