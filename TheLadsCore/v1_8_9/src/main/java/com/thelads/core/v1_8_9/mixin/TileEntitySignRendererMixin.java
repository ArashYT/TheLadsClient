package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.RenderTweaks189;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySignRenderer;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sign text drawn from each line's last wrap and formatting (RenderTweaks189) instead of redoing both per line per frame.
 * OptiFine M5 patches this class (sign text distance, custom colours) but keeps both calls; require = 0 leaves vanilla if one moves.
 */
@Mixin(TileEntitySignRenderer.class)
public abstract class TileEntitySignRendererMixin {
    @Unique private static final String RENDER = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntitySign;DDDFI)V";

    @Inject(method = RENDER, at = @At("HEAD"), require = 0)
    private void ladsSign(TileEntitySign te, double x, double y, double z, float partialTicks, int destroyStage, CallbackInfo ci) {
        RenderTweaks189.sign(te);
    }

    @Inject(method = RENDER, at = @At("RETURN"), require = 0)
    private void ladsSignDone(TileEntitySign te, double x, double y, double z, float partialTicks, int destroyStage, CallbackInfo ci) {
        RenderTweaks189.sign(null);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiUtilRenderComponents;splitText(Lnet/minecraft/util/IChatComponent;ILnet/minecraft/client/gui/FontRenderer;ZZ)Ljava/util/List;"), require = 0)
    private List<IChatComponent> ladsSplit(IChatComponent text, int width, FontRenderer font, boolean styles, boolean force) {
        return RenderTweaks189.splitSignLine(text, width, font, styles, force);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/IChatComponent;getFormattedText()Ljava/lang/String;"), require = 0)
    private String ladsFormatted(IChatComponent piece) {
        return RenderTweaks189.signLineText(piece);
    }
}
