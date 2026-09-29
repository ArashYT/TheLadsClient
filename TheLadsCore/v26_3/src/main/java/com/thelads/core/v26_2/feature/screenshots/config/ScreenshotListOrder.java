// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.config;

public enum ScreenshotListOrder {
   ASCENDING,
   DESCENDING;

   public boolean isInverted() {
      return this == DESCENDING;
   }
}
