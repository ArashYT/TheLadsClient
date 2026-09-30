// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.v26_2.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotViewerTexts;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

   private AbstractWidget makeIconWidget(Identifier texture, Component description, Consumer<ScreenshotImageHolder> action) {
      return new ManageScreenshotsScreen.ExtendedTexturedButtonWidget(0, 0, 20, 20, texture, btn -> {
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
         boolean readOnly=com.thelads.core.v26_2.feature.GlobalScreenshots.readOnly(this.showing.getScreenshotFile());
         this.deleteBtn.active=!readOnly;this.renameBtn.active=!readOnly;
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

   public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
   }

   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks, boolean updateHoverState) {
      this.children().forEach(element -> {
         if (element instanceof Renderable drawable) {
            drawable.extractRenderState(context, mouseX, mouseY, partialTicks);
         }

         if (element instanceof ManageScreenshotsScreen.CustomHoverState state) {
            int mul = updateHoverState ? 1 : -1;
            state.updateHoveredState(mouseX * mul, mouseY * mul);
         }
      });
   }

   public void renderImage(GuiGraphicsExtractor context) {
      if (this.showing != null) {
         int spacing = 8;
         NativeImage image = this.showing.image();
         if (image != null) {
            float imgRatio = (float)image.getWidth() / image.getHeight();
            float fit = Math.min((this.width - 16f) / image.getWidth(), (this.height - 44f) / image.getHeight());
            int texHeight = Math.max(1, (int)(image.getHeight() * fit * zoom));
            int texWidth = Math.max(1, (int)(image.getWidth() * fit * zoom));
            Identifier texture = this.showing.textureId();
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

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (minecraft.hasControlDown()) {
         boolean inverted = ManageScreenshotsScreen.CONFIG.getOrFallback(com.thelads.core.v26_2.feature.screenshots.config.ScreenshotViewerOptions.INVERT_ZOOM_DIRECTION, false);
         zoom = Math.clamp(zoom * (float)Math.pow(1.15, inverted ? -verticalAmount : verticalAmount), 1, 8);
         if (zoom == 1) { panX = 0; panY = 0; }
         return true;
      }
      if (verticalAmount > 0.0) {
         this.nextScreenshot();
      }

      if (verticalAmount < 0.0) {
         this.previousScreenshot();
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.key() == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT) {
         this.previousScreenshot();
         return true;
      } else if (input.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) {
         this.nextScreenshot();
         return true;
      } else if (this.showing != null && input.key() == com.mojang.blaze3d.platform.InputConstants.KEY_C && (input.modifiers() & com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL) != 0) {
         this.showing.copyScreenshot();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
      if (click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && zoom > 1) { panX += (float)dx; panY += (float)dy; return true; }
      return super.mouseDragged(click, dx, dy);
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (this.showing != null && click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
         this.properties.showProperties(click.x(), click.y(), this.showing);
         return true;
      } else {
         return super.mouseClicked(click, doubled);
      }
   }

   public void onClose() {
      this.showing = null;
      this.imageList = null;
   }

   @FunctionalInterface
   interface PropertiesDisplay {
      void showProperties(double var1, double var3, ScreenshotImageHolder var5);
   }
}
