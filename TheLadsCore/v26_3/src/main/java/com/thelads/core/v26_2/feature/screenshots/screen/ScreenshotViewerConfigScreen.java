// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen;

import io.github.lgatodu47.catconfigmc.screen.ModConfigScreen;
import com.thelads.core.v26_2.feature.screenshots.ScreenshotViewer;
import com.thelads.core.v26_2.feature.screenshots.config.ScreenshotViewerRenderedOptions;
import net.minecraft.client.gui.screens.Screen;

public class ScreenshotViewerConfigScreen extends ModConfigScreen {
   public ScreenshotViewerConfigScreen(Screen parent) {
      super(
         ScreenshotViewerTexts.translatable("screen", "config"), parent, ScreenshotViewer.getInstance().getConfig(), ScreenshotViewerRenderedOptions.access()
      );
      this.listeners = ScreenshotViewer.getInstance().getThumbnailManager();
   }
}
