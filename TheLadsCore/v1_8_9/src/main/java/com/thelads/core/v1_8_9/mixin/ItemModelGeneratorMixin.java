package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ItemModelGaps;
import java.util.List;
import net.minecraft.client.renderer.block.model.BlockPart;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.lwjgl.util.vector.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No see-through seams in item models Minecraft's own generator makes (ItemModelGaps): OptiFine's custom item textures
 * and any model not baked by Forge's ItemLayerModel, which overlaps its edges the same way already.
 */
@Mixin(ItemModelGenerator.class)
public abstract class ItemModelGeneratorMixin {
    /** The edge quads of one layer, before they are baked. */
    @Inject(method = "func_178397_a", at = @At("RETURN"), require = 1)
    private void ladsOverlap(TextureAtlasSprite sprite, String texture, int layer, CallbackInfoReturnable<List<BlockPart>> cir) {
        for (BlockPart part : cir.getReturnValue()) {
            EnumFacing facing = part.mapFaces.keySet().iterator().next();
            float[] from = {part.positionFrom.x, part.positionFrom.y, part.positionFrom.z};
            float[] to = {part.positionTo.x, part.positionTo.y, part.positionTo.z};
            // A left or right edge is labelled with the opposite side (EAST on a pixel's left edge); its corners make it face out.
            ItemModelGaps.overlap(from, to, -facing.getFrontOffsetX(), facing.getFrontOffsetY());
            set(part.positionFrom, from);
            set(part.positionTo, to);
        }
    }

    private static void set(Vector3f vector, float[] value) {
        vector.set(value[0], value[1], value[2]);
    }
}
