// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen;

import java.io.File;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.Action;

public record ScreenshotClickEvent(File screenshotFile) implements ClickEvent {
   public Action action() {
      return Action.OPEN_URL;
   }
}
