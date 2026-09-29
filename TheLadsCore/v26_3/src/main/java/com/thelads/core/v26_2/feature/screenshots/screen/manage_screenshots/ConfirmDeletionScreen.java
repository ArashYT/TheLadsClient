// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

final class ConfirmDeletionScreen extends ConfirmScreen {
   public ConfirmDeletionScreen(BooleanConsumer callback, Component title, Component message) {
      super(callback, title, message);
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      this.extractBackground(context, mouseX, mouseY, deltaTicks);
      super.extractRenderState(context, mouseX, mouseY, deltaTicks);
   }

   public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
   }
}
