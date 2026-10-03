package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.OldAnimations;
import com.thelads.core.client.OldAnimations.Held;
import com.thelads.core.client.OldAnimations.Use;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.OldAnimations189.Hook;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.7 Animations, 1.7 third-person items (players and other bipeds; OptiFine leaves the layer alone). The layer's arm and hand
 * offsets are 1.7's already; the item draw goes to 1.7's placement (with the sword block's turn while blocking) and no 1.8
 * display transform.
 */
@Mixin(LayerHeldItem.class)
public abstract class LayerHeldItemMixin {
    @Redirect(method = "doRenderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItem("
        + "Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V"),
        require = 1, allow = 1)
    private void ladsDrawItem(ItemRenderer renderer, EntityLivingBase entity, ItemStack stack, TransformType type) {
        Held held = OldAnimations189.active(Feature.THIRD_PERSON) ? OldAnimations189.held(stack) : null;
        if (held == null) {
            renderer.renderItem(entity, stack, type);
            return;
        }
        boolean blocking = entity instanceof EntityPlayer && OldAnimations189.use((EntityPlayer) entity, stack) == Use.BLOCK;
        OldAnimations.thirdPersonItem(OldAnimations189.GL, 1, held, blocking); // full size: RenderItem.preTransform undoes its 0.5
        renderer.renderItem(entity, stack, TransformType.NONE);
        OldAnimations189.hit(Hook.TP_ITEM);
    }
}
