// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots;

import java.io.File;
import java.util.Optional;

public interface ScreenshotImageList {
   ScreenshotImageHolder getScreenshot(int var1);

   Optional<ScreenshotImageHolder> findByFileName(File var1);

   int size();
}
