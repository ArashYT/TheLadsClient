package com.thelads.core.v1_20_1.hudextras.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.modules.SubtitlesModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Subtitles: vanilla's subtitles (from Gui on Fabric, Forge's subtitles overlay) moved and scaled from the bottom right corner, recoloured. */
@Mixin(SubtitleOverlay.class)
public class SubtitleMixin1201 {
    private static final String RENDER = "render(Lnet/minecraft/client/gui/GuiGraphics;)V";

    @WrapMethod(method = RENDER)
    private void lads$move(GuiGraphics graphics, Operation<Void> original) {
        SubtitlesModule subtitles = HudExtras.SUBTITLES;
        if (!subtitles.on || subtitles.dx == 0 && subtitles.dy == 0 && subtitles.size == 1) {
            original.call(graphics);
            return;
        }
        float px = graphics.guiWidth(), py = graphics.guiHeight();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(px + subtitles.dx, py + subtitles.dy, 0);
            graphics.pose().scale(subtitles.size, subtitles.size, 1);
            graphics.pose().translate(-px, -py, 0);
            original.call(graphics);
        } finally {
            graphics.pose().popPose();
        }
    }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"), index = 4)
    private int lads$background(int color) {
        return HudExtras.SUBTITLES.on ? HudExtras.SUBTITLES.backgroundArgb : color;
    }

    // The arrows and the text: vanilla's grey (dimming as the subtitle fades) becomes the chosen colour at the same brightness.
    @ModifyArg(method = RENDER, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"), index = 4)
    private int lads$arrows(int color) {
        return HudExtras.SUBTITLES.on ? SubtitlesModule.fade(HudExtras.SUBTITLES.textRgb, color & 0xFF) : color;
    }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"), index = 4)
    private int lads$text(int color) {
        return HudExtras.SUBTITLES.on ? SubtitlesModule.fade(HudExtras.SUBTITLES.textRgb, color & 0xFF) : color;
    }
}
