// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots;

import com.thelads.core.v26_2.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v26_2.feature.screenshots.screen.IconButtonWidget;
import com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotViewerTexts;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

class ScreenshotPropertiesMenu extends AbstractContainerEventHandler implements Renderable {
   private static final Identifier BACKGROUND_TEXTURE_ATLAS = Identifier.fromNamespaceAndPath("lads_screenshots", "screenshot_properties_background");
   static final Identifier OPEN_ICON = Identifier.fromNamespaceAndPath("lads_screenshots", "widget/icons/open_folder");
   static final Identifier COPY_ICON = Identifier.fromNamespaceAndPath("lads_screenshots", "widget/icons/copy");
   static final Identifier DELETE_ICON = Identifier.fromNamespaceAndPath("lads_screenshots", "widget/icons/delete");
   static final Identifier RENAME_ICON = Identifier.fromNamespaceAndPath("lads_screenshots", "widget/icons/rename");
   private static final Identifier CLOSE_ICON = Identifier.fromNamespaceAndPath("lads_screenshots", "widget/icons/close");
   private static final int BUTTON_SIZE = 19;
   private final Supplier<Minecraft> mcSupplier;
   private final List<AbstractWidget> buttons = new ArrayList<>();
   private int x;
   private int y;
   private int width;
   private int height;
   private ScreenshotImageHolder targetScreenshot;
   private boolean shouldRender;

   ScreenshotPropertiesMenu(Supplier<Minecraft> mcSupplier) {
      this.mcSupplier = mcSupplier;
      this.addButton(OPEN_ICON, ScreenshotViewerTexts.OPEN_FILE, ScreenshotImageHolder::openFile);
      this.addButton(COPY_ICON, ScreenshotViewerTexts.COPY, ScreenshotImageHolder::copyScreenshot);
      this.addButton(DELETE_ICON, ScreenshotViewerTexts.DELETE, ScreenshotImageHolder::requestFileDeletion);
      this.addButton(RENAME_ICON, ScreenshotViewerTexts.RENAME_FILE, ScreenshotImageHolder::renameFile);
      this.addButton(CLOSE_ICON, ScreenshotViewerTexts.CLOSE_PROPERTIES, null);
   }

   private void addButton(Identifier texture, Component description, @Nullable Consumer<ScreenshotImageHolder> action) {
      this.buttons.add(new ScreenshotPropertiesMenu.Button(texture, description, btn -> {
         if (action != null && this.targetScreenshot != null) {
            action.accept(this.targetScreenshot);
         }

         this.hide();
      }));
   }

   void show(int x, int y, int parentWidth, int parentHeight, ScreenshotImageHolder targetScreenshot) {
      this.targetScreenshot = targetScreenshot;
      boolean readOnly=com.thelads.core.v26_2.feature.GlobalScreenshots.readOnly(targetScreenshot.getScreenshotFile());
      this.buttons.get(2).active=!readOnly;this.buttons.get(3).active=!readOnly;
      int spacing = 2;
      Font font = this.mcSupplier.get().font;
      int largestTextWidth = this.buttons.stream().map(AbstractWidget::getMessage).mapToInt(font::width).max().orElse(0);
      this.width = 4 + Math.max(font.width(targetScreenshot.getScreenshotFile().getName()), 19 + largestTextWidth + 4);
      this.height = 6 + 9 + 19 * this.buttons.size();
      if (x + this.width > parentWidth) {
         this.x = x - this.width;
      } else {
         this.x = x;
      }

      if (y + this.height > parentHeight) {
         this.y = y - this.height;
      } else {
         this.y = y;
      }

      for (int i = 0; i < this.buttons.size(); i++) {
         this.buttons.get(i).setRectangle(this.width - 4, 19, this.x + 2, this.y + 4 + 9 + 19 * i);
      }

      this.shouldRender = true;
   }

   void hide() {
      this.shouldRender = false;
      this.x = this.y = this.width = this.height = 0;
   }

   boolean renders() {
      return this.shouldRender;
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.shouldRender) {
         int spacing = 2;
         context.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE_ATLAS, this.x, this.y, this.width, this.height);
         context.text(this.mcSupplier.get().font, this.targetScreenshot.getScreenshotFile().getName(), this.x + 2, this.y + 2, -1);

         for (AbstractWidget widget : this.buttons) {
            widget.extractRenderState(context, mouseX, mouseY, delta);
            context.text(
               this.mcSupplier.get().font, widget.getMessage(), widget.getX() + 19 + 2, (int)(widget.getY() + (widget.getHeight() - 9) / 2.0F + 2.0F), -1
            );
         }
      }
   }

   public List<? extends GuiEventListener> children() {
      return List.copyOf(this.buttons);
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (!(click.x() < this.x) && !(click.y() < this.y) && !(click.x() > this.x + this.width) && !(click.y() > this.y + this.height)) {
         return super.mouseClicked(click, doubled);
      } else {
         this.hide();
         return false;
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.key() == 256) {
         this.hide();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   private static final class Button extends IconButtonWidget {
      private static final WidgetSprites BUTTON_TEXTURES = new WidgetSprites(
         Identifier.fromNamespaceAndPath("lads_screenshots", "widget/properties_button_enabled"),
         Identifier.fromNamespaceAndPath("lads_screenshots", "widget/properties_button"),
         Identifier.fromNamespaceAndPath("lads_screenshots", "widget/properties_button_hovered")
      );
      private static final WidgetSprites TEXTURES_FOR_WIDE = new WidgetSprites(
         Identifier.fromNamespaceAndPath("lads_screenshots", "textures/gui/sprites/widget/properties_button_enabled.png"),
         Identifier.fromNamespaceAndPath("lads_screenshots", "textures/gui/sprites/widget/properties_button.png"),
         Identifier.fromNamespaceAndPath("lads_screenshots", "textures/gui/sprites/widget/properties_button_hovered.png")
      );
      private boolean renderWide = (Boolean)ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.RENDER_WIDE_PROPERTIES_BUTTON, true);

      public Button(Identifier texture, Component title, OnPress pressAction) {
         super(0, 0, 19, 19, title, texture, pressAction);
      }

      public void setRectangle(int width, int height, int x, int y) {
         this.renderWide = (Boolean)ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.RENDER_WIDE_PROPERTIES_BUTTON, true);
         super.setRectangle(this.renderWide ? width : this.getWidth(), height, x, y);
      }

      @Override
      protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
         Identifier backgroundTexture = this.getBackgroundTexture().get(this.active, this.isHoveredOrFocused());
         if (this.renderWide) {
            context.blit(
               RenderPipelines.GUI_TEXTURED, backgroundTexture, this.getX(), this.getY(), 0.0F, 0.0F, 1, this.getHeight(), 19, 19, ARGB.white(this.alpha)
            );
            context.blit(
               RenderPipelines.GUI_TEXTURED,
               backgroundTexture,
               this.getX() + 1,
               this.getY(),
               1.0F,
               0.0F,
               this.getWidth() - 2,
               this.getHeight(),
               17,
               19,
               19,
               19,
               ARGB.white(this.alpha)
            );
            context.blit(
               RenderPipelines.GUI_TEXTURED,
               backgroundTexture,
               this.getX() + this.getWidth() - 1,
               this.getY(),
               18.0F,
               0.0F,
               1,
               this.getHeight(),
               19,
               19,
               ARGB.white(this.alpha)
            );
         } else {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, backgroundTexture, this.getX(), this.getY(), 19, this.getHeight(), ARGB.white(this.alpha));
         }

         Identifier icon = this.getIconTexture();
         if (icon != null) {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, icon, this.getX(), this.getY(), 19, this.getHeight(), ARGB.white(this.alpha));
         }
      }

      public boolean isHoveredOrFocused() {
         return this.isHovered();
      }

      @Override
      public WidgetSprites getBackgroundTexture() {
         return this.renderWide ? TEXTURES_FOR_WIDE : BUTTON_TEXTURES;
      }
   }
}
