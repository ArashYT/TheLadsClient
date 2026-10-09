// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen.manage_screenshots;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

final class ConfirmDeletionScreen extends ConfirmScreen {
   public ConfirmDeletionScreen(BooleanConsumer callback, Component title, Component message) {
      super(callback, title, message);
   }

   @Override
   public void renderBackground(GuiGraphics context) {
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
   }
}
