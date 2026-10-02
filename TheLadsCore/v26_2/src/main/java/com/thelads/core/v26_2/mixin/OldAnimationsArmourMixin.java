package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.feature.NativeOldAnimations;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EquipmentLayerRenderer.class)
public class OldAnimationsArmourMixin {
    // 1.7 Animations' Red armour on hurt: humanoid armour takes the body's hurt overlay.
    @ModifyExpressionValue(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;"
        + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/texture/OverlayTexture;NO_OVERLAY:I"), require = 1)
    private int lads$redArmour(int overlay, @Local(argsOnly = true) EquipmentClientInfo.LayerType type, @Local(argsOnly = true) Object state) {
        return NativeOldAnimations.armourOverlay(type, state, overlay);
    }
}
