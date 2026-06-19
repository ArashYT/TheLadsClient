package com.thelads.core.mixin;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.BetterF3Module;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {

    @Unique
    private BetterF3Module thelads$betterF3() {
        Module m = ModuleManager.getInstance().getModule("BetterF3");
        return (m instanceof BetterF3Module bf && bf.isEnabled()) ? bf : null;
    }

    @ModifyArg(
        method = "extractLines",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"),
        index = 4,
        require = 0
    )
    private int thelads$bgColor(int original) {
        BetterF3Module m = thelads$betterF3();
        return m != null ? m.getBackgroundColor() : original;
    }

    @ModifyArg(
        method = "extractLines",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"),
        index = 5,
        require = 0
    )
    private boolean thelads$textShadow(boolean original) {
        BetterF3Module m = thelads$betterF3();
        return m != null ? m.isTextShadow() : original;
    }
}
