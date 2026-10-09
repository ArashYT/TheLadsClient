// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.config;

public enum ScreenshotListOrder {
   ASCENDING,
   DESCENDING;

   public boolean isInverted() {
      return this == DESCENDING;
   }
}
