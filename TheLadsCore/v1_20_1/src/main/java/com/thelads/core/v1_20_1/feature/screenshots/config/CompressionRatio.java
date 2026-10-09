// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.config;

public enum CompressionRatio {
   NONE,
   HALF,
   QUARTER,
   EIGHTH;

   public int scale(int size) {
      return switch (this) {
         case HALF -> size / 2;
         case QUARTER -> size / 4;
         case EIGHTH -> size / 8;
         default -> size;
      };
   }
}
