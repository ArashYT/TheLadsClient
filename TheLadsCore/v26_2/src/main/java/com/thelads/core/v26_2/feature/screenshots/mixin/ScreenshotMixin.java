// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.feature.screenshots.*;
import com.thelads.core.v26_2.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v26_2.feature.screenshots.screen.*;
import java.io.File;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Screenshot.class)
public final class ScreenshotMixin {
    @ModifyExpressionValue(method = "lambda$grab$3", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/MutableComponent;withStyle(Ljava/util/function/UnaryOperator;)Lnet/minecraft/network/chat/MutableComponent;"))
    private static MutableComponent ladsScreenshotLink(MutableComponent original, @Local(argsOnly = true) File file) {
        if (!ScreenshotViewer.active()) return original;
        var styled = original.withStyle(style -> style.withClickEvent(new ScreenshotClickEvent(file)).withHoverEvent(new HoverEvent.ShowText(
            ScreenshotViewerUtils.ofSupplied(() -> ScreenshotViewer.active() && ScreenshotViewer.getInstance().getConfig().getOrFallback(
                ScreenshotViewerOptions.REDIRECT_SCREENSHOT_CHAT_LINKS, false) ? ScreenshotViewerTexts.REDIRECT_TO_SCREENSHOT_MANAGER : null))));
        if (com.thelads.core.v26_2.feature.NativeQualityOfLife.bool("Chat", "Screenshot Link Buttons", true)) {
            return ScreenshotViewerUtils.appendScreenshotButtons(styled, file);
        }
        return styled;
    }
}
