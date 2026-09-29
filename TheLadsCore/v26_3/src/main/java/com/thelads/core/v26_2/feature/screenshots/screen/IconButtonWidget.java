// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen;

import com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots.ManageScreenshotsScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public class IconButtonWidget extends Button {
   @Nullable
   private final Identifier iconTexture;

   public IconButtonWidget(int x, int y, int width, int height, Component message, @Nullable Identifier iconTexture, OnPress pressAction) {
      super(x, y, width, height, message, pressAction, DEFAULT_NARRATION);
      this.iconTexture = iconTexture;
   }

   @Nullable
   public Identifier getIconTexture() {
      return this.iconTexture;
   }

   public WidgetSprites getBackgroundTexture() {
      return ManageScreenshotsScreen.DEFAULT_BUTTON_TEXTURES;
   }

   protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      context.blitSprite(
         RenderPipelines.GUI_TEXTURED,
         this.getBackgroundTexture().get(this.active, this.isHoveredOrFocused()),
         this.getX(),
         this.getY(),
         this.getWidth(),
         this.getHeight(),
         this.getAlpha()
      );
      Identifier icon = this.getIconTexture();
      if (icon != null) {
         context.blitSprite(RenderPipelines.GUI_TEXTURED, icon, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.getAlpha());
      }
   }
}
