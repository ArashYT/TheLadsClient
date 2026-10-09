package com.thelads.core.v1_20_1.hud.overlay.mixin;

import com.thelads.core.v1_20_1.hud.overlay.Nametags1201;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Nametags: renames, background and text shadow on every name tag (players' too: PlayerRenderer draws through this method). */
@Mixin(EntityRenderer.class)
public class NametagRenderMixin {
    private static final String DRAW = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I";

    @ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
    private Component lads$rename(Component name) {
        return Nametags1201.rename(name);
    }

    @ModifyArg(method = "renderNameTag", at = @At(value = "INVOKE", target = DRAW), index = 8)
    private int lads$background(int vanillaColor) {
        return Nametags1201.background(vanillaColor);
    }

    @ModifyArg(method = "renderNameTag", at = @At(value = "INVOKE", target = DRAW), index = 4)
    private boolean lads$shadow(Component text, float x, float y, int color, boolean shadow, Matrix4f matrix, MultiBufferSource buffers,
                                Font.DisplayMode mode, int background, int light) {
        return Nametags1201.shadow(shadow, mode == Font.DisplayMode.NORMAL);
    }
}
