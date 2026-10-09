// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen.manage_screenshots;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.v1_20_1.feature.GlobalScreenshots1201;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v1_20_1.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v1_20_1.feature.screenshots.screen.IconButtonWidget;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotViewerTexts;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

class EnlargedScreenshotScreen extends Screen {
   @Nullable
   private ScreenshotImageHolder showing;
   @Nullable
   private ScreenshotImageList imageList;
   private final EnlargedScreenshotScreen.PropertiesDisplay properties;
   private final AbstractWidget doneBtn;
   private final AbstractWidget nextBtn;
   private final AbstractWidget prevBtn;
   private final AbstractWidget openBtn;
   private final AbstractWidget copyBtn;
   private final AbstractWidget deleteBtn;
   private final AbstractWidget renameBtn;

   EnlargedScreenshotScreen(EnlargedScreenshotScreen.PropertiesDisplay properties) {
      super(Component.empty());
      this.properties = properties;
      this.doneBtn = new ManageScreenshotsScreen.ExtendedButtonWidget(0, 0, 52, 20, CommonComponents.GUI_DONE, btn -> this.onClose());
      this.prevBtn = new ManageScreenshotsScreen.ExtendedButtonWidget(0, 0, 20, 20, Component.literal("<"), btn -> this.previousScreenshot());
      this.nextBtn = new ManageScreenshotsScreen.ExtendedButtonWidget(0, 0, 20, 20, Component.literal(">"), btn -> this.nextScreenshot());
      this.openBtn = this.makeIconWidget(ScreenshotPropertiesMenu.OPEN_ICON, ScreenshotViewerTexts.OPEN_FILE, ScreenshotImageHolder::openFile);
      this.copyBtn = this.makeIconWidget(ScreenshotPropertiesMenu.COPY_ICON, ScreenshotViewerTexts.COPY, ScreenshotImageHolder::copyScreenshot);
      this.deleteBtn = this.makeIconWidget(ScreenshotPropertiesMenu.DELETE_ICON, ScreenshotViewerTexts.DELETE, ScreenshotImageHolder::requestFileDeletion);
      this.renameBtn = this.makeIconWidget(ScreenshotPropertiesMenu.RENAME_ICON, ScreenshotViewerTexts.RENAME_FILE, ScreenshotImageHolder::renameFile);
   }

   private AbstractWidget makeIconWidget(IconButtonWidget.Icon icon, Component description, Consumer<ScreenshotImageHolder> action) {
      return new ManageScreenshotsScreen.ExtendedTexturedButtonWidget(0, 0, 20, 20, icon, btn -> {
         if (this.showing != null) {
            action.accept(this.showing);
         }
      }, description, description);
   }

   void show(ScreenshotImageHolder showing, ScreenshotImageList imageList) {
      this.showing = showing;
      this.imageList = imageList;
      this.updateButtonsState();
   }

   @Override
   protected void init() {
      super.init();
      this.clearWidgets();
      int spacing = 4;
      this.addPositioned(this.doneBtn, (this.width - 52) / 2, this.height - 20 - spacing * 2);
      this.addPositioned(this.prevBtn, spacing * 2, (this.height - 20) / 2);
      int rightButtonsX = this.width - spacing * 2 - 20;
      this.addPositioned(this.nextBtn, this.width - spacing * 2 - 20, (this.height - 20) / 2);
      this.addPositioned(this.openBtn, rightButtonsX, this.height - 100 - spacing * 6);
      this.addPositioned(this.copyBtn, rightButtonsX, this.height - 80 - spacing * 5);
      this.addPositioned(this.deleteBtn, rightButtonsX, this.height - 60 - spacing * 4);
      this.addPositioned(this.renameBtn, rightButtonsX, this.height - 40 - spacing * 3);
   }

   private void addPositioned(AbstractWidget button, int x, int y) {
      button.setX(x);
      button.setY(y);
      this.addRenderableWidget(button);
   }

   private void nextScreenshot() {
      if (this.hasInfo()) {
         int i = this.showing.indexInList() + 1;
         if (i < this.imageList.size()) {
            this.showing = this.imageList.getScreenshot(i);
            this.updateButtonsState();
         }
      }
   }

   private void previousScreenshot() {
      if (this.hasInfo()) {
         int i = this.showing.indexInList() - 1;
         if (i >= 0) {
            this.showing = this.imageList.getScreenshot(i);
            this.updateButtonsState();
         }
      }
   }

   private void updateButtonsState() {
      zoom = 1; panX = panY = 0;
      if (this.hasInfo()) {
         boolean readOnly = GlobalScreenshots1201.readOnly(this.showing.getScreenshotFile());
         this.deleteBtn.active = !readOnly;
         this.renameBtn.active = !readOnly;
         int i = this.showing.indexInList();
         this.prevBtn.active = i > 0;
         this.nextBtn.active = i < this.imageList.size() - 1;
      }
   }

   boolean isShowing(ScreenshotImageHolder image) { return showing == image; }
   @Nullable ScreenshotImageHolder showing() { return showing; }
   private float zoom = 1f, panX, panY;
   private boolean hasInfo() {
      return this.showing != null && this.imageList != null;
   }

   boolean renders() {
      return this.hasInfo();
   }

   @Override
   public void renderBackground(GuiGraphics context) {
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
   }

   @Override
   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
   }

   public void render(GuiGraphics context, int mouseX, int mouseY, float partialTicks, boolean updateHoverState) {
      this.children().forEach(element -> {
         if (element instanceof Renderable drawable) {
            drawable.render(context, mouseX, mouseY, partialTicks);
         }

         if (element instanceof ManageScreenshotsScreen.CustomHoverState state) {
            int mul = updateHoverState ? 1 : -1;
            state.updateHoveredState(mouseX * mul, mouseY * mul);
         }
      });
   }

   public void renderImage(GuiGraphics context) {
      if (this.showing != null) {
         NativeImage image = this.showing.image();
         if (image != null) {
            float fit = Math.min((this.width - 16f) / image.getWidth(), (this.height - 44f) / image.getHeight());
            int texHeight = Math.max(1, (int)(image.getHeight() * fit * zoom));
            int texWidth = Math.max(1, (int)(image.getWidth() * fit * zoom));
            ResourceLocation texture = this.showing.textureId();
            if (texture != null) {
               ScreenshotViewerUtils.drawTexture(
                  context,
                  texture,
                  (int)((this.width - texWidth) / 2 + panX),
                  (int)((this.height - 36 - texHeight) / 2 + panY),
                  texWidth,
                  texHeight,
                  0,
                  0,
                  image.getWidth(),
                  image.getHeight(),
                  image.getWidth(),
                  image.getHeight()
               );
            }
         }
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
      if (Screen.hasControlDown()) {
         boolean inverted = ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.INVERT_ZOOM_DIRECTION, false);
         zoom = Mth.clamp(zoom * (float)Math.pow(1.15, inverted ? -verticalAmount : verticalAmount), 1, 8);
         if (zoom == 1) { panX = 0; panY = 0; }
         return true;
      }
      if (verticalAmount > 0.0) {
         this.nextScreenshot();
      }

      if (verticalAmount < 0.0) {
         this.previousScreenshot();
      }

      return super.mouseScrolled(mouseX, mouseY, verticalAmount);
   }

   @Override
   public boolean keyPressed(int key, int scanCode, int modifiers) {
      if (key == InputConstants.KEY_LEFT) {
         this.previousScreenshot();
         return true;
      } else if (key == InputConstants.KEY_RIGHT) {
         this.nextScreenshot();
         return true;
      } else if (this.showing != null && key == InputConstants.KEY_C && (modifiers & InputConstants.MOD_CONTROL) != 0) {
         this.showing.copyScreenshot();
         return true;
      } else {
         return super.keyPressed(key, scanCode, modifiers);
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
      if (button == InputConstants.MOUSE_BUTTON_LEFT && zoom > 1) { panX += (float)dx; panY += (float)dy; return true; }
      return super.mouseDragged(mouseX, mouseY, button, dx, dy);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.showing != null && button == InputConstants.MOUSE_BUTTON_RIGHT) {
         this.properties.showProperties(mouseX, mouseY, this.showing);
         return true;
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   @Override
   public void onClose() {
      this.showing = null;
      this.imageList = null;
   }

   @FunctionalInterface
   interface PropertiesDisplay {
      void showProperties(double x, double y, ScreenshotImageHolder image);
   }
}
