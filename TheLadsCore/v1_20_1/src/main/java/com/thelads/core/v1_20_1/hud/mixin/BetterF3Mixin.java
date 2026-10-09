package com.thelads.core.v1_20_1.hud.mixin;

import com.thelads.core.v1_20_1.hud.NativeBetterF3_1201;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Better F3: each finished column of debug text goes to NativeBetterF3_1201 instead of the game's plain grey lines. */
@Mixin(DebugScreenOverlay.class)
public abstract class BetterF3Mixin {
    @Shadow @Final private Font font;

    @Inject(method = "renderLines(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Z)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$betterF3(GuiGraphics graphics, List<String> lines, boolean left, CallbackInfo ci) {
        if (NativeBetterF3_1201.draw(graphics, font, lines, left)) ci.cancel();
    }
}
