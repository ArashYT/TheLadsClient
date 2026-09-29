// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;

public interface OldParentElementMethods extends ContainerEventHandler {
   default boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      for (GuiEventListener element : this.children()) {
         if (element.mouseClicked(click, doubled)) {
            this.setFocused(element);
            if (click.button() == 0) {
               this.setDragging(true);
            }

            return true;
         }
      }

      return false;
   }
}
