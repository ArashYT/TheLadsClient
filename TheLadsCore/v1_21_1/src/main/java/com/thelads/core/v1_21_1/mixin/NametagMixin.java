package com.thelads.core.v1_21_1.mixin;

import com.thelads.core.v1_21_1.feature.NativeNicknames;
import com.thelads.core.v1_21_1.feature.NativeQualityOfLife;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Nametags, as 26.x: renames, background and text shadow on 1.21.1's direct name-tag draws. Essential wraps the same draws
 * and reads the background argument for its icon padding, so it follows the background option.
 */
@Mixin(EntityRenderer.class)
public class NametagMixin {
    @Unique private static final String DRAW = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I";

    @ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true, require = 1)
    private Component lads$rename(Component name) {
        return NativeNicknames.rename(name);
    }

    @ModifyArg(method = "renderNameTag", at = @At(value = "INVOKE", target = DRAW), index = 8, require = 1)
    private int lads$background(int vanillaColor) {
        return NativeQualityOfLife.enabled("Nametags") && !NativeQualityOfLife.bool("Nametags", "Render Background", true) ? 0 : vanillaColor;
    }

    /** Shadow on the normal pass only; the see-through pass behind walls stays flat and readable. */
    @ModifyArg(method = "renderNameTag", at = @At(value = "INVOKE", target = DRAW), index = 4, require = 1)
    private boolean lads$shadow(Component text, float x, float y, int color, boolean shadow, Matrix4f pose, MultiBufferSource buffers,
                                Font.DisplayMode mode, int background, int light) {
        return shadow || mode == Font.DisplayMode.NORMAL && NativeQualityOfLife.enabled("Nametags") && NativeQualityOfLife.bool("Nametags", "Text Shadow", true);
    }
}
