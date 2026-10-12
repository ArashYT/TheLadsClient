package com.thelads.core.v1_20_1.hud.overlay.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.client.NametagDepth;
import com.thelads.core.v1_20_1.hud.overlay.Nametags1201;
import net.minecraft.client.gui.Font;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Nametags' Text Shadow: Font moves the text 0.03 along z past its shadow, which in a name tag points away from the camera, so the
 * shadow covered the text in black lines that flickered as the camera moved. In a name tag the text goes in front by a gap the depth
 * buffer holds at that distance (NametagDepth); every other text keeps vanilla's offset.
 */
@Mixin(Font.class)
abstract class NametagShadowDepthMixin {
    // Name tags draw a Component (its FormattedCharSequence overload); a bare name would map to the String one alone.
    @ModifyArg(method = "drawInternal(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;translate(Lorg/joml/Vector3fc;)Lorg/joml/Matrix4f;"))
    private Vector3fc lads$textInFront(Vector3fc offset, @Local(argsOnly = true) Matrix4f pose) {
        if (!Nametags1201.drawing) return offset;
        return new Vector3f(0, 0, NametagDepth.textOffset(pose.m20(), pose.m21(), pose.m22(), pose.m30(), pose.m31(), pose.m32()));
    }
}
