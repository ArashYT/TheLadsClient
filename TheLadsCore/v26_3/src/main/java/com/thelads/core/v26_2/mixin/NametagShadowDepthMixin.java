package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.client.NametagDepth;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Text with a shadow in the world (nametags with Nametags' Text Shadow): the text sits in front of its shadow by a gap the depth
 * buffer holds at that distance (NametagDepth), so the shadow no longer flickers through it as black lines. GUI text (flat) keeps 0.
 */
@Mixin(BakedSheetGlyph.class)
abstract class NametagShadowDepthMixin {
    @ModifyConstant(method = "renderChar", constant = @Constant(floatValue = 0.03F), require = 1)
    private float lads$textInFront(float vanilla, @Local(argsOnly = true) Matrix4fc pose) {
        return NametagDepth.textOffset(pose.m20(), pose.m21(), pose.m22(), pose.m30(), pose.m31(), pose.m32());
    }
}
