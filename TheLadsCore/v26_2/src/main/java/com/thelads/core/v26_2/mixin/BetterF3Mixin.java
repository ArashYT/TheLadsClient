package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeBetterF3;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Better F3: each finished column of debug text goes to NativeBetterF3 instead of the game's plain grey lines. */
@Mixin(DebugScreenOverlay.class)
public class BetterF3Mixin {
    @Shadow @Final private Font font;

    @Inject(method = "extractLines", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$betterF3(GuiGraphicsExtractor graphics, List<String> lines, boolean left, CallbackInfo ci) {
        if (NativeBetterF3.draw(graphics, font, lines, left, graphics.guiWidth())) ci.cancel();
    }
}
