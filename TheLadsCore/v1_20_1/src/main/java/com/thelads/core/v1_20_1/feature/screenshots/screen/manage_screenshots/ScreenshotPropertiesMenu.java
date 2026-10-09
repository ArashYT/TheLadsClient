// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen.manage_screenshots;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.v1_20_1.feature.GlobalScreenshots1201;
import com.thelads.core.v1_20_1.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v1_20_1.feature.screenshots.screen.IconButtonWidget;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotViewerTexts;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

class ScreenshotPropertiesMenu extends AbstractContainerEventHandler implements Renderable {
   private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation("lads_screenshots", "textures/gui/sprites/screenshot_properties_background.png");
   static final IconButtonWidget.Icon OPEN_ICON = IconButtonWidget.OPEN_FOLDER_ICON;
   static final IconButtonWidget.Icon COPY_ICON = IconButtonWidget.COPY_ICON;
   static final IconButtonWidget.Icon DELETE_ICON = IconButtonWidget.DELETE_ICON;
   static final IconButtonWidget.Icon RENAME_ICON = IconButtonWidget.RENAME_ICON;
   private static final IconButtonWidget.Icon CLOSE_ICON = IconButtonWidget.CLOSE_ICON;
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

   private void addButton(IconButtonWidget.Icon icon, Component description, @Nullable Consumer<ScreenshotImageHolder> action) {
      this.buttons.add(new ScreenshotPropertiesMenu.Button(icon, description, btn -> {
         if (action != null && this.targetScreenshot != null) {
            action.accept(this.targetScreenshot);
         }

         this.hide();
      }));
   }

   void show(int x, int y, int parentWidth, int parentHeight, ScreenshotImageHolder targetScreenshot) {
      this.targetScreenshot = targetScreenshot;
      boolean readOnly = GlobalScreenshots1201.readOnly(targetScreenshot.getScreenshotFile());
      this.buttons.get(2).active = !readOnly;
      this.buttons.get(3).active = !readOnly;
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
         ((Button) this.buttons.get(i)).setRectangle(this.width - 4, 19, this.x + 2, this.y + 4 + 9 + 19 * i);
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

   @Override
   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      if (this.shouldRender) {
         // The menu sits above everything the gallery draws.
         context.pose().pushPose();
         context.pose().translate(0.0F, 0.0F, 400.0F);
         renderNineSlice(context, BACKGROUND_TEXTURE, 6, 1, this.x, this.y, this.width, this.height);
         context.drawString(this.mcSupplier.get().font, this.targetScreenshot.getScreenshotFile().getName(), this.x + 2, this.y + 2, -1);

         for (AbstractWidget widget : this.buttons) {
            widget.render(context, mouseX, mouseY, delta);
            context.drawString(
               this.mcSupplier.get().font, widget.getMessage(), widget.getX() + 19 + 2, (int) (widget.getY() + (widget.getHeight() - 9) / 2.0F + 2.0F), -1
            );
         }
         context.pose().popPose();
      }
   }

   /** A square texture drawn as nine pieces: fixed corners and edges of the given border width, stretched middle. */
   private static void renderNineSlice(GuiGraphics context, ResourceLocation texture, int size, int border, int x, int y, int width, int height) {
      int inner = size - 2 * border;
      int right = x + width - border;
      int bottom = y + height - border;
      context.blit(texture, x, y, border, border, 0.0F, 0.0F, border, border, size, size);
      context.blit(texture, right, y, border, border, size - border, 0.0F, border, border, size, size);
      context.blit(texture, x, bottom, border, border, 0.0F, size - border, border, border, size, size);
      context.blit(texture, right, bottom, border, border, size - border, size - border, border, border, size, size);
      context.blit(texture, x + border, y, width - 2 * border, border, border, 0.0F, inner, border, size, size);
      context.blit(texture, x + border, bottom, width - 2 * border, border, border, size - border, inner, border, size, size);
      context.blit(texture, x, y + border, border, height - 2 * border, 0.0F, border, border, inner, size, size);
      context.blit(texture, right, y + border, border, height - 2 * border, size - border, border, border, inner, size, size);
      context.blit(texture, x + border, y + border, width - 2 * border, height - 2 * border, border, border, inner, inner, size, size);
   }

   @Override
   public List<? extends GuiEventListener> children() {
      return List.copyOf(this.buttons);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!(mouseX < this.x) && !(mouseY < this.y) && !(mouseX > this.x + this.width) && !(mouseY > this.y + this.height)) {
         return super.mouseClicked(mouseX, mouseY, button);
      } else {
         this.hide();
         return false;
      }
   }

   @Override
   public boolean keyPressed(int key, int scanCode, int modifiers) {
      if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
         this.hide();
         return true;
      } else {
         return super.keyPressed(key, scanCode, modifiers);
      }
   }

   private static final class Button extends IconButtonWidget {
      private static final ResourceLocation ENABLED = new ResourceLocation("lads_screenshots", "textures/gui/sprites/widget/properties_button_enabled.png");
      private static final ResourceLocation DISABLED = new ResourceLocation("lads_screenshots", "textures/gui/sprites/widget/properties_button.png");
      private static final ResourceLocation HOVERED = new ResourceLocation("lads_screenshots", "textures/gui/sprites/widget/properties_button_hovered.png");
      private boolean renderWide = ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.RENDER_WIDE_PROPERTIES_BUTTON, true);

      public Button(IconButtonWidget.Icon icon, Component title, OnPress pressAction) {
         super(0, 0, BUTTON_SIZE, BUTTON_SIZE, title, icon, pressAction);
      }

      void setRectangle(int width, int height, int x, int y) {
         this.renderWide = ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.RENDER_WIDE_PROPERTIES_BUTTON, true);
         this.setX(x);
         this.setY(y);
         this.setWidth(this.renderWide ? width : this.getWidth());
         this.height = height; // 1.20.1 widgets have no setHeight
      }

      @Override
      protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
         ResourceLocation backgroundTexture = !this.active ? DISABLED : this.isHoveredOrFocused() ? HOVERED : ENABLED;
         context.setColor(1.0F, 1.0F, 1.0F, this.alpha);
         RenderSystem.enableBlend();
         if (this.renderWide) {
            context.blit(backgroundTexture, this.getX(), this.getY(), 1, this.getHeight(), 0.0F, 0.0F, 1, 19, 19, 19);
            context.blit(backgroundTexture, this.getX() + 1, this.getY(), this.getWidth() - 2, this.getHeight(), 1.0F, 0.0F, 17, 19, 19, 19);
            context.blit(backgroundTexture, this.getX() + this.getWidth() - 1, this.getY(), 1, this.getHeight(), 18.0F, 0.0F, 1, 19, 19, 19);
         } else {
            context.blit(backgroundTexture, this.getX(), this.getY(), 19, this.getHeight(), 0.0F, 0.0F, 19, 19, 19, 19);
         }

         IconButtonWidget.Icon icon = this.getIconTexture();
         if (icon != null) {
            context.blit(icon.texture(), this.getX(), this.getY(), 19, this.getHeight(), 0.0F, 0.0F, icon.size(), icon.size(), icon.size(), icon.size());
         }
         context.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      }

      @Override
      public boolean isHoveredOrFocused() {
         return this.isHovered();
      }
   }
}
