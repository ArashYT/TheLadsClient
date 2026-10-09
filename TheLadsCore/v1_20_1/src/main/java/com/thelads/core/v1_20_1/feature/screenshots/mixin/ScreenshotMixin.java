// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.mixin;

import com.thelads.core.v1_20_1.feature.screenshots.NativeScreenshotToast;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewer1201;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v1_20_1.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotClickEvent;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotViewerTexts;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Wraps the callback Screenshot.grab reports its result to. When the result is the vanilla "Saved screenshot as ..." message it
 * shows the screenshot toast and gives the file name the gallery link and (if the Chat module wants them) the [Copy] / [Open File] /
 * [Open Folder] / [Upload to Imgur] buttons. Wrapping the callback instead of the lambda inside _grab keeps this independent of
 * the compiler's lambda names, which differ between the dev and the production mappings and under Forge.
 */
@Mixin(Screenshot.class)
public final class ScreenshotMixin {
    @ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Consumer<Component> ladsScreenshotLink(Consumer<Component> original) {
        return message -> original.accept(decorate(message));
    }

    private static Component decorate(Component message) {
        try {
            if (!ScreenshotViewer1201.available()
                || !(message.getContents() instanceof TranslatableContents contents) || !"screenshot.success".equals(contents.getKey())
                || contents.getArgs().length != 1 || !(contents.getArgs()[0] instanceof Component link)
                || link.getStyle().getClickEvent() == null || link.getStyle().getClickEvent().getAction() != ClickEvent.Action.OPEN_FILE) {
                return message;
            }
            File file = new File(link.getStyle().getClickEvent().getValue());
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.getToasts().addToast(new NativeScreenshotToast(file)));
            if (!ScreenshotViewer1201.active()) return message;
            MutableComponent styled = link.copy().withStyle(style -> style.withClickEvent(new ScreenshotClickEvent(file)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                ScreenshotViewerUtils.ofSupplied(() -> ScreenshotViewer1201.active() && ScreenshotViewer1201.getInstance().getConfig().getOrFallback(
                    ScreenshotViewerOptions.REDIRECT_SCREENSHOT_CHAT_LINKS, false) ? ScreenshotViewerTexts.REDIRECT_TO_SCREENSHOT_MANAGER : null))));
            if (ScreenshotViewer1201.chatLinkButtons()) {
                styled = ScreenshotViewerUtils.appendScreenshotButtons(styled, file);
            }
            MutableComponent result = Component.translatable("screenshot.success", styled);
            result.setStyle(message.getStyle());
            message.getSiblings().forEach(result::append);
            return result;
        } catch (Throwable failure) {
            return message; // a decoration problem must never hide the vanilla message
        }
    }
}
