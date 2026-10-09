// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen.manage_screenshots;

import java.io.File;
import java.util.Optional;

public interface ScreenshotImageList {
   ScreenshotImageHolder getScreenshot(int index);

   Optional<ScreenshotImageHolder> findByFileName(File file);

   int size();
}
