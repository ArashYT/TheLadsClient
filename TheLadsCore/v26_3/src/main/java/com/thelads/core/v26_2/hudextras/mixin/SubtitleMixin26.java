package com.thelads.core.v26_2.hudextras.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.modules.SubtitlesModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Subtitles: vanilla's subtitles moved and scaled from the bottom right corner, recoloured. */
@Mixin(SubtitleOverlay.class)
public class SubtitleMixin26 {
    private static final String EXTRACT = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V";

    @WrapMethod(method = EXTRACT)
    private void lads$move(GuiGraphicsExtractor graphics, Operation<Void> original) {
        SubtitlesModule subtitles = HudExtras.SUBTITLES;
        if (!subtitles.on || subtitles.dx == 0 && subtitles.dy == 0 && subtitles.size == 1) {
            original.call(graphics);
            return;
        }
        float px = graphics.guiWidth(), py = graphics.guiHeight();
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(px + subtitles.dx, py + subtitles.dy);
            graphics.pose().scale(subtitles.size, subtitles.size);
            graphics.pose().translate(-px, -py);
            original.call(graphics);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    @ModifyArg(method = EXTRACT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), index = 4)
    private int lads$background(int color) {
        return HudExtras.SUBTITLES.on ? HudExtras.SUBTITLES.backgroundArgb : color;
    }

    // The arrows and the text: vanilla's grey (dimming as the subtitle fades) becomes the chosen colour at the same brightness.
    @ModifyArg(method = EXTRACT, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"), index = 4)
    private int lads$arrows(int color) {
        return HudExtras.SUBTITLES.on ? SubtitlesModule.fade(HudExtras.SUBTITLES.textRgb, color & 0xFF) : color;
    }

    @ModifyArg(method = EXTRACT, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), index = 4)
    private int lads$text(int color) {
        return HudExtras.SUBTITLES.on ? SubtitlesModule.fade(HudExtras.SUBTITLES.textRgb, color & 0xFF) : color;
    }
}
