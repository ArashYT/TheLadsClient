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
 * 1.9.12: drawn at the HUD FPS cap rate through HudCache1201's second cache (mapBegin / mapEnd).
 */
@Pseudo
@Mixin(targets = "xaero.common.events.ModClientEvents", remap = false)
public abstract class XaeroHudMixin {
    @Inject(method = "handleRenderModOverlay", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lads$covered(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        if (HudManager.getInstance().isCovered("Minimap")) {
            com.thelads.core.v1_20_1.hud.HudCache1201.mapHidden();
            ci.cancel();
        } else if (com.thelads.core.v1_20_1.hud.HudCache1201.mapBegin(graphics)) {
            ci.cancel(); // the HUD cap's cached image of it was drawn instead
        }
    }

    @Inject(method = "handleRenderModOverlay", at = @At("RETURN"), remap = false, require = 0)
    private void lads$cached(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        com.thelads.core.v1_20_1.hud.HudCache1201.mapEnd(graphics);
    }
}
