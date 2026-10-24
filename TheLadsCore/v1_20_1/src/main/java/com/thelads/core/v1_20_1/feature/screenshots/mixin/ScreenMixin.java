// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.mixin;

import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewer1201;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v1_20_1.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotChatActionClickEvent;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotClickEvent;
import com.thelads.core.v1_20_1.feature.screenshots.screen.manage_screenshots.ManageScreenshotsScreen;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {
@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
   private void lads_toast(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
      if (com.thelads.core.client.hud.ServerRulesNotice.mouseClicked((int) mouseX, (int) mouseY, button)) {
         cir.setReturnValue(true);
      }
   }
   @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
   private void lads_screenshots$inject_handleClickEvent(Style style, CallbackInfoReturnable<Boolean> cir) {
      ClickEvent clickEvent = style == null ? null : style.getClickEvent();
      if (clickEvent instanceof ScreenshotChatActionClickEvent ae) {
         File file = ae.screenshotFile();
         switch (ae.actionType()) {
            case COPY -> ScreenshotViewerUtils.copyImageToClipboard(file);
            case OPEN_FILE -> ScreenshotViewerUtils.openPath(file);
            case OPEN_FOLDER -> ScreenshotViewerUtils.openPath(file.getParentFile());
            case UPLOAD_IMGUR -> ScreenshotViewerUtils.uploadToImgur(file);
         }
         cir.setReturnValue(true);
         return;
      }
      if (clickEvent instanceof ScreenshotClickEvent ce) {
         Minecraft client = Minecraft.getInstance();
         if (ScreenshotViewer1201.active() && !Screen.hasShiftDown()
            && ScreenshotViewer1201.getInstance().getConfig().getOrFallback(ScreenshotViewerOptions.REDIRECT_SCREENSHOT_CHAT_LINKS, false)) {
            client.setScreen(new ManageScreenshotsScreen(client.screen, ce.screenshotFile()));
         } else {
            ScreenshotViewerUtils.openPath(ce.screenshotFile());
         }
         cir.setReturnValue(true);
      }
   }
}
