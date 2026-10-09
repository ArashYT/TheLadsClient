// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen.manage_screenshots;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface ScreenshotImageHolder {
   File getScreenshotFile();

   void openFile();

   void copyScreenshot();

   void requestFileDeletion();

   void renameFile();

   int indexInList();

   @Nullable
   ResourceLocation textureId();

   @Nullable
   NativeImage image();
}
