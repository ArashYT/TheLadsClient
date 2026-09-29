// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public interface ScreenshotImageHolder {
   File getScreenshotFile();

   void openFile();

   void copyScreenshot();

   void requestFileDeletion();

   void renameFile();

   int indexInList();

   @Nullable
   Identifier textureId();

   @Nullable
   NativeImage image();
}
