/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * See assets/theladscore/licenses/PaperDoll-MPL-2.0.txt in the distributed Core JAR.
 * Adapted from Fuzss Paper Doll 26.2.3, commit 5968f6f523a2ddc46e5890bd47dc6a5d9bf48b29.
 */
package com.thelads.core.v26_2.feature.paperdoll.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.feature.paperdoll.PaperDollRenderState;
import com.thelads.core.v26_2.feature.paperdoll.PaperDollTexture;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SubmitNodeCollection.class)
public abstract class PaperDollSubmitMixin {
    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true)
    private <S> RenderType ladsPaperDollBlend(RenderType type, @Local(argsOnly = true) S state) {
        if (state instanceof PaperDollRenderState doll && doll.ladsPaperDollAlpha() < 255
                && !type.hasBlending()) {
            var texture = ((PaperDollTexture) type).ladsPaperDollTexture();
            if (texture != null) return RenderTypes.entityTranslucent(texture);
        }
        return type;
    }

    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private <S> int ladsPaperDollColor(int color, @Local(argsOnly = true) S state) {
        return state instanceof PaperDollRenderState doll && doll.ladsPaperDollAlpha() < 255
                ? ARGB.color(doll.ladsPaperDollAlpha(), color) : color;
    }
}
