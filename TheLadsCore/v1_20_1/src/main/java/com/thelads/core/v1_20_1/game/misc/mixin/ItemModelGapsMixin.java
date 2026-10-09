package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.client.ItemModelGaps;
import java.util.List;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No see-through seams in generated item models (ItemModelGaps): each edge quad of a "builtin/generated" item (sword, stick, bow)
 * is moved a hair inside its pixel and lengthened a hair, so it overlaps the front and back faces and its neighbours.
 * Vanilla and Sodium/Embeddium alike bake these elements through FaceBakery, so one hook covers all (neither has its own baker).
 */
@Mixin(ItemModelGenerator.class)
public abstract class ItemModelGapsMixin {
    @Inject(method = "createSideElements", at = @At("RETURN"), require = 1)
    private void lads$overlap(SpriteContents sprite, String texture, int tintIndex, CallbackInfoReturnable<List<BlockElement>> callback) {
        for (BlockElement element : callback.getReturnValue()) {
            if (element.faces.size() != 1) continue;
            Direction facing = element.faces.keySet().iterator().next();
            float[] a = {element.from.x(), element.from.y(), element.from.z()}, b = {element.to.x(), element.to.y(), element.to.z()};
            // A left or right edge quad is labelled with the opposite side (EAST on a pixel's left edge); its corners make it face out.
            ItemModelGaps.overlap(a, b, -facing.getStepX(), facing.getStepY());
            element.from.set(new Vector3f(a[0], a[1], a[2]));
            element.to.set(new Vector3f(b[0], b[1], b[2]));
        }
    }
}
