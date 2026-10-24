package com.thelads.core.v1_20_1.qol.mixin;

import com.thelads.core.client.TrueTypeFont;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TrueType Font: replaces string width calculation when Custom Font is active. */
@Mixin(Font.class)
public abstract class FontMixin1201 {
    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void lads$trueTypeWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (TrueTypeFont.isEnabled()) {
            cir.setReturnValue(TrueTypeFont.getStringWidth(text));
        }
    }
}
