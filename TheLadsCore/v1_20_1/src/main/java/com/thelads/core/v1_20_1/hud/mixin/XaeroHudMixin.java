package com.thelads.core.v1_20_1.hud.mixin;

import com.thelads.core.client.hud.HudManager;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Xaero's minimap on plain Fabric steps aside with the Lads Minimap widget under the tab list or F3 (HudCover): its HUD modules are
 * drawn here (in Zero SMP, ForgeOverlays1201 skips its Forge overlay instead). Pseudo: skipped without Xaero's Minimap.
 */
@Pseudo
@Mixin(targets = "xaero.common.events.ModClientEvents", remap = false)
public abstract class XaeroHudMixin {
    @Inject(method = "handleRenderModOverlay", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lads$covered(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        if (HudManager.getInstance().isCovered("Minimap")) ci.cancel();
    }
}
