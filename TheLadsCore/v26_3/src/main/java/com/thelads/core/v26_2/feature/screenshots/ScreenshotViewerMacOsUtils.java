// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots;

import ca.weblite.objc.Client;
import ca.weblite.objc.Proxy;
import com.mojang.logging.LogUtils;
import net.minecraft.util.Util;
import net.minecraft.util.Util.OS;

public class ScreenshotViewerMacOsUtils {
   public static void doCopyMacOS(String path) {
      if (Util.getPlatform() == OS.OSX) {
         Client client = Client.getInstance();
         Proxy url = client.sendProxy("NSURL", "fileURLWithPath:", new Object[]{path});
         Proxy image = client.sendProxy("NSImage", "alloc", new Object[0]);
         image.send("initWithContentsOfURL:", new Object[]{url});
         Proxy array = client.sendProxy("NSArray", "array", new Object[0]);
         array = array.sendProxy("arrayByAddingObject:", new Object[]{image});
         Proxy pasteboard = client.sendProxy("NSPasteboard", "generalPasteboard", new Object[0]);
         pasteboard.send("clearContents", new Object[0]);
         boolean wasSuccessful = pasteboard.sendBoolean("writeObjects:", new Object[]{array});
         if (!wasSuccessful) {
            LogUtils.getLogger().error("Failed to write image to pasteboard!");
         }
      }
   }
}
