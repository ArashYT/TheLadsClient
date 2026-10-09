// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen;

import java.io.File;
import net.minecraft.network.chat.ClickEvent;

/** The screenshot name in the chat message: opens the file, or the gallery when "Redirect Screenshot Chat Links" is on. */
public final class ScreenshotClickEvent extends ClickEvent {
   private final File screenshotFile;

   public ScreenshotClickEvent(File screenshotFile) {
      super(Action.OPEN_FILE, screenshotFile.getAbsolutePath());
      this.screenshotFile = screenshotFile;
   }

   public File screenshotFile() {
      return screenshotFile;
   }
}
