// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.mixin;

import java.util.Locale;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Main.class})
public class MainMixin {
   @Inject(
      method = {"main"},
      at = {@At("HEAD")},
      remap = false
   )
   private static void lads_screenshots$inject_main(String[] args, CallbackInfo ci) {
      if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")
         && !System.getProperties().containsKey("screenshot_viewer.debug.disable_headless_hook")) {
         System.out.println("Screenshot Viewer sets 'java.awt.headless' to false!");
         System.setProperty("java.awt.headless", "false");
      }
   }
}
