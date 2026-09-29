// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.v26_2.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v26_2.feature.screenshots.config.ARGBColor;
import com.thelads.core.v26_2.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v26_2.feature.screenshots.config.VisibilityState;
import com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotViewerTexts;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

final class ScreenshotWidget extends AbstractWidget implements AutoCloseable, ScreenshotImageHolder {
   private final ManageScreenshotsScreen mainScreen;
   private final Minecraft client;
   private final ScreenshotWidget.Context ctx;
   private VisibilityState textVisibility;
   private ARGBColor backgroundColor;
   private ARGBColor textColor;
   private boolean renderTextShadow;
   private boolean promptOnDelete;
   private List<ClientTooltipComponent> hintTooltip;
   private final ScreenshotWidget.ImageLoader screenshotImage = new ScreenshotWidget.ImageLoader("screenshot");
   private final ScreenshotWidget.ImageLoader thumbnailImage = new ScreenshotWidget.ImageLoader("thumbnail");
   private File screenshotFile;
   private boolean selectedForDeletion;
   private float hoverTime;
   private long hoverClock = System.nanoTime();
   private boolean imagesStarted;
   private int baseY;

   public ScreenshotWidget(ManageScreenshotsScreen mainScreen, int x, int y, int width, int height, ScreenshotWidget.Context ctx, File screenshotFile) {
      super(x, y, width, height, Component.literal(screenshotFile.getName()));
      this.mainScreen = mainScreen;
      this.client = mainScreen.client();
      this.baseY = y;
      this.ctx = ctx;
      this.screenshotFile = screenshotFile;
      this.screenshotImage.file = CompletableFuture.completedFuture(screenshotFile);
      this.onConfigUpdate();
   }

   void updateBaseY(int baseY) {
      this.setY(this.baseY = baseY);
   }

   void updateY(int scrollY) {
      this.setY(this.baseY - scrollY);
   }

   void deleteScreenshot() {
      try {
         if (com.thelads.core.v26_2.feature.screenshots.ScreenshotFileIO.delete(screenshotFile.toPath())) {
            close(); ManageScreenshotsScreen.THUMBNAILS.removeThumbnail(screenshotFile); ctx.removeEntry(this);
         }
      } catch (IOException failure) { ScreenshotViewerUtils.fileError("Delete", screenshotFile, failure); }
   }

   void onConfigUpdate() {
      this.textVisibility = (VisibilityState)ManageScreenshotsScreen.CONFIG
         .getOrFallback(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_TEXT_VISIBILITY, VisibilityState.VISIBLE);
      this.backgroundColor = (ARGBColor)ManageScreenshotsScreen.CONFIG
         .getOrFallback(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_BACKGROUND_COLOR, ARGBColor.WHITE);
      this.textColor = (ARGBColor)ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_TEXT_COLOR, ARGBColor.WHITE);
      this.renderTextShadow = (Boolean)ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.RENDER_SCREENSHOT_ELEMENT_FONT_SHADOW, true);
      this.promptOnDelete = (Boolean)ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.PROMPT_WHEN_DELETING_SCREENSHOT, true);
      this.hintTooltip = ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.DISPLAY_HINT_TOOLTIP, false)
         ? ScreenshotViewerUtils.toColoredComponents(this.client, ScreenshotViewerTexts.translatable("tooltip", "menu_hint").withStyle(ChatFormatting.GRAY))
         : List.of();
   }

   void updateHoverState(int mouseX, int mouseY, int viewportY, int viewportBottom, boolean updateHoverState) {
      this.isHovered = updateHoverState
         && mouseX >= this.getX()
         && mouseY >= Math.max(this.getY(), viewportY)
         && mouseX < this.getX() + this.width
         && mouseY < Math.min(this.getY() + this.height, viewportBottom);
   }

   boolean isSelectedForDeletion() {
      return this.selectedForDeletion;
   }

   void deselectForDeletion() {
      this.selectedForDeletion = false;
   }

   void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick, int viewportY, int viewportBottom) {
      long now = System.nanoTime();
      this.hoverTime = this.isHovered ? this.hoverTime + Math.min(.1f, (now - hoverClock) / 1_000_000_000f) * 20 : 0;
      hoverClock = now;
      if (!imagesStarted) {
          imagesStarted = true;
          thumbnailImage.load(ManageScreenshotsScreen.THUMBNAILS.getThumbnail(screenshotFile).orElseGet(() -> CompletableFuture.completedFuture(screenshotFile)));
      }
      this.renderBackground(context, viewportY, viewportBottom);
      int spacing = 2;
      NativeImage image = this.thumbnailImage.image();


      if (image != null) {
         int renderY = Math.max(this.getY() + 2, viewportY);
         int imgHeight = (int)(this.height / (VisibilityState.HIDDEN.equals(this.textVisibility) ? 1.0 : 1.08) - 6.0);
         int topOffset = Math.max(0, viewportY - this.getY() - 2);
         int bottomOffset = Math.max(0, this.getY() + 2 + imgHeight - viewportBottom);
         int topV = topOffset * image.getHeight() / imgHeight;
         int bottomV = bottomOffset * image.getHeight() / imgHeight;
         Identifier texture = this.thumbnailTextureId();
         if (texture != null) {
            ScreenshotViewerUtils.drawTexture(
               context,
               texture,
               this.getX() + 2,
               renderY,
               this.width - 4,
               imgHeight - topOffset - bottomOffset,
               0,
               topV,
               image.getWidth(),
               image.getHeight() - topV - bottomV,
               image.getWidth(),
               image.getHeight()
            );
         }

         if (this.mainScreen.isFastDeleteToggled() && this.selectedForDeletion) {
            context.fill(this.getX() + 2, renderY, this.getX() + this.width - 2, renderY + imgHeight - topOffset - bottomOffset, 1358888960);
         }
      }

      if (VisibilityState.VISIBLE.equals(this.textVisibility) || VisibilityState.SHOW_ON_HOVER.equals(this.textVisibility) && this.isHovered) {
         float scaleFactor = (float)(this.client.getWindow().getGuiScaledHeight() / 96) / this.ctx.screenshotsPerRow();
         int textY = this.getY() + (int)(this.height / 1.08) - 2;
         if (textY > viewportY && textY + scaleFactor * 9.0F < viewportBottom) {
            Matrix3x2fStack matrices = context.pose();
            matrices.pushMatrix();
            matrices.translate(this.getX() + this.width / 2.0F, textY);
            matrices.scale(scaleFactor, scaleFactor);
            Component message = this.getMessage();
            float centerX = -this.client.font.width(this.getMessage()) / 2;
            context.text(this.client.font, message, (int)centerX, 0, this.textColor.value(), this.renderTextShadow);
            matrices.popMatrix();
         }
      }

      if (!this.mainScreen.isFastDeleteToggled() && !this.hintTooltip.isEmpty() && this.hoverTime > 20.0F) {
         ScreenshotViewerUtils.renderCustomTooltip(
            context, this.client.font, this.hintTooltip, mouseX, mouseY, ARGB.white(Math.min(this.hoverTime - 20.0F, 10.0F) / 10.0F * 0.7F)
         );
      }
   }

   public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
   }

   private void renderBackground(GuiGraphicsExtractor context, int viewportY, int viewportBottom) {
      int renderY = Math.max(this.getY(), viewportY);
      int renderHeight = Math.min(this.getY() + this.height, viewportBottom);
      context.fill(
         this.getX(),
         renderY,
         this.getX() + this.width,
         renderHeight,
         ARGB.color(
            (int)(Math.min(this.hoverTime, 10.0F) / 10.0F * this.backgroundColor.alpha()),
            this.backgroundColor.red(),
            this.backgroundColor.green(),
            this.backgroundColor.blue()
         )
      );
   }

   private void onClick() {
      if (this.mainScreen.isFastDeleteToggled()) {
         this.selectedForDeletion = !this.selectedForDeletion;
      } else {
         this.mainScreen.enlargeScreenshot(this);
      }
   }

   private void onRightClick(double mouseX, double mouseY) {
      this.mainScreen.showScreenshotProperties(mouseX, mouseY, this);
   }

   private void updateScreenshotFile(File screenshotFile) {
      close();
      ManageScreenshotsScreen.THUMBNAILS.removeThumbnail(this.screenshotFile);
      this.screenshotFile = screenshotFile;
      this.screenshotImage.file = CompletableFuture.completedFuture(screenshotFile);
      this.imagesStarted = false;
   }

   @Nullable
   private Identifier thumbnailTextureId() {
      if (!this.thumbnailImage.file.isDone()) {
         return this.screenshotImage.textureId();
      } else {
         Identifier texture = this.thumbnailImage.textureId();
         return texture == null ? this.screenshotImage.textureId() : texture;
      }
   }

   @Override
   public File getScreenshotFile() {
      return this.screenshotFile;
   }

   @Override
   public void openFile() {
      com.mojang.blaze3d.Blaze3D.openPath(this.screenshotFile.toPath());
   }

   @Override
   public void copyScreenshot() {
      ScreenshotViewerUtils.copyImageToClipboard(this.screenshotFile);
   }

   @Override
   public void requestFileDeletion() {
      BooleanConsumer deleteAction = value -> {
         if (value) {
            this.deleteScreenshot();
            this.mainScreen.enlargeScreenshot(null);
         }

         this.mainScreen.setDialogScreen(null);
      };
      if (this.promptOnDelete) {
         this.mainScreen
            .setDialogScreen(
               new ConfirmDeletionScreen(
                  deleteAction,
                  Component.translatable("screen.lads_screenshots.delete_prompt", new Object[]{this.screenshotFile.getName()}),
                  ScreenshotViewerTexts.DELETE_WARNING_MESSAGE
               )
            );
      } else {
         deleteAction.accept(true);
      }
   }

   @Override
   public void renameFile() {
      String fileName = this.screenshotFile.getName();
      this.mainScreen
         .setDialogScreen(
            new RenameScreenshotScreen(
               fileName.substring(0, fileName.lastIndexOf(46)),
               s -> {
                  try {
                     Path moved = com.thelads.core.v26_2.feature.screenshots.ScreenshotFileIO.rename(this.screenshotFile.toPath(), s);
                     this.updateScreenshotFile(moved.toFile());
                  } catch (IOException failure) {
                     ScreenshotViewerUtils.fileError("Rename", screenshotFile, failure);
                  }
               },
               () -> this.mainScreen.setDialogScreen(null)
            )
         );
   }

   @Override
   public int indexInList() {
      return this.ctx.currentIndex(this);
   }

   @Nullable
   @Override
   public Identifier textureId() {
      return this.screenshotImage.textureId();
   }

   @Nullable
   @Override
   public NativeImage image() {
      return this.screenshotImage.image();
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.isHovered && input.key() == com.mojang.blaze3d.platform.InputConstants.KEY_C && (input.modifiers() & com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL) != 0) {
         this.copyScreenshot();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   public Component getMessage() {
      return (Component)(this.screenshotFile == null ? super.getMessage() : Component.literal(this.screenshotFile.getName()));
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (this.isHovered()) {
         this.playDownSound(this.client.getSoundManager());
         if (click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            this.onClick();
         }

         if (click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
            this.onRightClick(click.x(), click.y());
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean isMouseOver(double mouseX, double mouseY) {
      return this.isHovered();
   }

   protected void updateWidgetNarration(NarrationElementOutput builder) {
   }

   @Override
   public void close() {
      this.screenshotImage.close();
      this.thumbnailImage.close();
   }

   interface Context {
      int screenshotsPerRow();

      int currentIndex(ScreenshotWidget var1);

      void removeEntry(ScreenshotWidget var1);
   }

   class ImageLoader implements AutoCloseable {
      private final String imageType;
      private CompletableFuture<File> file;
      private CompletableFuture<NativeImage> image;
      @Nullable
      private Identifier textureId;

      ImageLoader(String imageType) {
         Objects.requireNonNull(ScreenshotWidget.this);
         super();
         this.file = new CompletableFuture<>();
         this.imageType = imageType;
      }

      public void load(CompletableFuture<File> file) {
         this.file = file;
         this.image = null;
      }

      public void setImage(CompletableFuture<File> file) {
         if (!this.file.isDone()) {
            this.file.cancel(true);
         }

         this.file = file;
         if (this.textureId != null) {
            ScreenshotWidget.this.client.getTextureManager().release(this.textureId);
            this.textureId = null;
         } else if (this.image != null) {
            this.image.thenAcceptAsync(image -> {
               if (image != null) {
                  image.close();
               }
            }, ScreenshotWidget.this.client);
         }

         this.image = null;
      }

      public void deleteFile() {
         File f = this.file.getNow(null);
         if (f != null && f.exists() && !f.delete()) {
            ManageScreenshotsScreen.LOGGER.error("Failed to delete '{}' file at location '{}'", this.imageType, f.toPath().toAbsolutePath());
         }
      }

      private CompletableFuture<NativeImage> getImage() {
         return file.handleAsync((source, failed) -> {
            if (source == null || failed != null) return null;
            try { return ScreenshotViewerUtils.readNative(source, imageType.equals("thumbnail")); }
            catch (Exception failure) { ManageScreenshotsScreen.LOGGER.warn("Cannot read screenshot {}", source.getName(), failure); return null; }
         }, com.thelads.core.v26_2.feature.screenshots.ScreenshotFileIO.IMAGE_EXECUTOR);
      }

      @Nullable
      public Identifier textureId() {
         if (this.textureId != null) {
            return this.textureId;
         } else {
            if (this.image == null) {
               this.image = this.getImage();
            }

            NativeImage nativeImage;
            if (this.image.isDone() && (nativeImage = this.image.join()) != null) {
               File f = this.file.getNow(null);
               String hash = java.util.UUID.randomUUID().toString();
               this.textureId = Identifier.fromNamespaceAndPath(
                  "lads_screenshots", "dynamic/" + this.imageType.toLowerCase() + "/" + hash
               );
               ScreenshotWidget.this.client.getTextureManager().register(this.textureId, new DynamicTexture(this.textureId::toString, nativeImage));
               return this.textureId;
            } else {
               return null;
            }
         }
      }

      @Nullable
      public NativeImage image() {
         if (this.image == null) {
            this.image = this.getImage();
         }

         return this.image.getNow(null);
      }

      @Override
      public void close() {
         if (this.textureId != null) {
            ScreenshotWidget.this.client.getTextureManager().release(this.textureId);
            this.textureId = null;
         } else if (this.image != null) {
            this.image.thenAcceptAsync(image -> {
               if (image != null) {
                  image.close();
               }
            }, ScreenshotWidget.this.client);
         }

         this.image = null;
      }
   }
}
