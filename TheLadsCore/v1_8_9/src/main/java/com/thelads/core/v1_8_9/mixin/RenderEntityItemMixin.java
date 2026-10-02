package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.OldAnimations;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.OldAnimations189.Hook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.7 Animations, 2D dropped items (OptiFine leaves RenderEntityItem alone). A flat item's placement (func_177077_a's first
 * translate) becomes 1.7's camera-facing icon, its spin (the method's one rotate) is dropped and doRender's GROUND display
 * transform becomes none. Blocks and built-in models keep 1.8.9's.
 */
@Mixin(RenderEntityItem.class)
public abstract class RenderEntityItemMixin {
    /** The item being drawn is a flat icon placed by the recipe; set per entity in func_177077_a, which doRender calls first. */
    @Unique private boolean ladsFlat;

    @Redirect(method = "func_177077_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V",
        ordinal = 0), require = 1, allow = 1)
    private void ladsPlace(float x, float y, float z, EntityItem item, double posX, double posY, double posZ, float partialTicks,
                           IBakedModel model) {
        ladsFlat = !model.isGui3d() && !model.isBuiltInRenderer() && OldAnimations189.active(Feature.DROPPED_2D);
        if (!ladsFlat) {
            GlStateManager.translate(x, y, z);
            return;
        }
        GlStateManager.translate((float) posX, (float) posY, (float) posZ);
        OldAnimations.droppedItem(OldAnimations189.GL, item.getAge() + partialTicks, item.hoverStart,
            Minecraft.getMinecraft().getRenderManager().playerViewY);
        OldAnimations189.fullSizeMesh();
        OldAnimations189.hit(Hook.DROP);
    }

    @Redirect(method = "func_177077_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V"),
        require = 1, allow = 1)
    private void ladsSpin(float angle, float x, float y, float z) {
        if (!ladsFlat) GlStateManager.rotate(angle, x, y, z);
    }

    @ModifyArg(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At(value = "INVOKE", remap = false,
        target = "Lnet/minecraftforge/client/ForgeHooksClient;handleCameraTransforms(Lnet/minecraft/client/resources/model/IBakedModel;"
            + "Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)Lnet/minecraft/client/resources/model/IBakedModel;"),
        index = 1, require = 1, allow = 1)
    private TransformType ladsNoGroundTransform(TransformType type) {
        return ladsFlat ? TransformType.NONE : type;
    }
}
