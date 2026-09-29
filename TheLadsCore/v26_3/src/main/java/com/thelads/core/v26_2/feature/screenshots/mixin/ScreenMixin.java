// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.mixin;

import com.thelads.core.v26_2.feature.screenshots.ScreenshotViewer;
import com.thelads.core.v26_2.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotClickEvent;
import com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots.ManageScreenshotsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.OpenFile;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Screen.class})
public abstract class ScreenMixin {
   @Shadow
   protected static void defaultHandleClickEvent(ClickEvent clickEvent, Minecraft client, @Nullable Screen screenAfterRun) {
   }

   @Inject(
      method = {"defaultHandleGameClickEvent"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void lads_screenshots$inject_handleClickEvent(
      ClickEvent clickEvent, Minecraft client, @org.jetbrains.annotations.Nullable Screen screenAfterRun, CallbackInfo ci
   ) {
      if (clickEvent instanceof ScreenshotClickEvent ce) {
         if (ScreenshotViewer.active() && !client.hasShiftDown()
            && (Boolean)ScreenshotViewer.getInstance().getConfig().getOrFallback(ScreenshotViewerOptions.REDIRECT_SCREENSHOT_CHAT_LINKS, false)) {
            client.gui.setScreen(new ManageScreenshotsScreen(client.gui.screen(), ce.screenshotFile()));
            ci.cancel();
            return;
         }

         defaultHandleClickEvent(new OpenFile(ce.screenshotFile()), client, screenAfterRun);
         ci.cancel();
      }
   }
}
