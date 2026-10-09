// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen.manage_screenshots;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.feature.GlobalScreenshots1201;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotFileIO;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v1_20_1.feature.screenshots.config.ARGBColor;
import com.thelads.core.v1_20_1.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v1_20_1.feature.screenshots.config.VisibilityState;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotViewerTexts;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

final class ScreenshotWidget extends AbstractWidget implements AutoCloseable, ScreenshotImageHolder {
   private final ManageScreenshotsScreen mainScreen;
   private final Minecraft client;
   private final ScreenshotWidget.Context ctx;
   private VisibilityState textVisibility;
   private ARGBColor backgroundColor;
   private ARGBColor textColor;
   private boolean renderTextShadow;
   private boolean promptOnDelete;
   private List<FormattedCharSequence> hintTooltip;
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

   /** 1.20.1 widgets only have setWidth; the list resizes every card. */
   void setHeight(int height) {
      this.height = height;
   }

   void updateBaseY(int baseY) {
      this.setY(this.baseY = baseY);
   }

   void updateY(int scrollY) {
      this.setY(this.baseY - scrollY);
   }

   void deleteScreenshot() {
      if (GlobalScreenshots1201.readOnly(screenshotFile)) return;
      try {
         var copies = GlobalScreenshots1201.syncedCopies(screenshotFile);
         if (ScreenshotFileIO.delete(screenshotFile.toPath())) {
            GlobalScreenshots1201.deleteCopies(copies); close(); ManageScreenshotsScreen.THUMBNAILS.removeThumbnail(screenshotFile); ctx.removeEntry(this);
         }
      } catch (IOException failure) { ScreenshotViewerUtils.fileError("Delete", screenshotFile, failure); }
   }

   void onConfigUpdate() {
      this.textVisibility = ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_TEXT_VISIBILITY, VisibilityState.VISIBLE);
      this.backgroundColor = ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_BACKGROUND_COLOR, ARGBColor.WHITE);
      this.textColor = ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_TEXT_COLOR, ARGBColor.WHITE);
      this.renderTextShadow = ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.RENDER_SCREENSHOT_ELEMENT_FONT_SHADOW, true);
      this.promptOnDelete = ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.PROMPT_WHEN_DELETING_SCREENSHOT, true);
      this.hintTooltip = ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.DISPLAY_HINT_TOOLTIP, false)
         ? ScreenshotViewerUtils.toTooltipLines(this.client, ScreenshotViewerTexts.translatable("tooltip", "menu_hint").withStyle(ChatFormatting.GRAY))
         : List.of();
      if (GlobalScreenshots1201.readOnly(screenshotFile))
         this.hintTooltip = ScreenshotViewerUtils.toTooltipLines(this.client, Component.literal(
            GlobalScreenshots1201.source(screenshotFile) + "\nExternal original | read-only").withStyle(ChatFormatting.GRAY));
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

   /** A white with the given opacity (0 to 1) in its alpha byte, the tint of the fading hint tooltip. */
   private static int white(float alpha) {
      return (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24 | 0xFFFFFF;
   }

   void render(GuiGraphics context, int mouseX, int mouseY, float partialTick, int viewportY, int viewportBottom) {
      long now = System.nanoTime();
      this.hoverTime = this.isHovered ? this.hoverTime + Math.min(.1f, (now - hoverClock) / 1_000_000_000f) * 20 : 0;
      hoverClock = now;
      if (!imagesStarted) {
          imagesStarted = true;
          thumbnailImage.load(ManageScreenshotsScreen.THUMBNAILS.getThumbnail(screenshotFile).orElseGet(() -> CompletableFuture.completedFuture(screenshotFile)));
      }
      this.renderBackground(context, viewportY, viewportBottom);
      NativeImage image = this.thumbnailImage.image();


      if (image != null) {
         int renderY = Math.max(this.getY() + 2, viewportY);
         int imgHeight = (int)(this.height / (VisibilityState.HIDDEN.equals(this.textVisibility) ? 1.0 : 1.08) - 6.0);
         int topOffset = Math.max(0, viewportY - this.getY() - 2);
         int bottomOffset = Math.max(0, this.getY() + 2 + imgHeight - viewportBottom);
         int topV = topOffset * image.getHeight() / imgHeight;
         int bottomV = bottomOffset * image.getHeight() / imgHeight;
         ResourceLocation texture = this.thumbnailTextureId();
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
            PoseStack matrices = context.pose();
            matrices.pushPose();
            matrices.translate(this.getX() + this.width / 2.0F, textY, 0.0F);
            matrices.scale(scaleFactor, scaleFactor, 1.0F);
            Component message = this.getMessage();
            float centerX = -this.client.font.width(this.getMessage()) / 2;
            context.drawString(this.client.font, message, (int)centerX, 0, this.textColor.value(), this.renderTextShadow);
            matrices.popPose();
         }
      }

      if (!this.mainScreen.isFastDeleteToggled() && !this.hintTooltip.isEmpty() && this.hoverTime > 20.0F) {
         ScreenshotViewerUtils.renderCustomTooltip(
            context, this.client.font, this.hintTooltip, mouseX, mouseY, white(Math.min(this.hoverTime - 20.0F, 10.0F) / 10.0F * 0.7F)
         );
      }
   }

   @Override
   public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
   }

   private void renderBackground(GuiGraphics context, int viewportY, int viewportBottom) {
      int renderY = Math.max(this.getY(), viewportY);
      int renderHeight = Math.min(this.getY() + this.height, viewportBottom);
      context.fill(
         this.getX(),
         renderY,
         this.getX() + this.width,
         renderHeight,
         FastColor.ARGB32.color(
            (int)(Math.min(this.hoverTime, 10.0F) / 10.0F * this.backgroundColor.alpha()),
            this.backgroundColor.red(),
            this.backgroundColor.green(),
            this.backgroundColor.blue()
         )
      );
   }

   private void onClick() {
      if (this.mainScreen.isFastDeleteToggled()) {
         this.selectedForDeletion = !GlobalScreenshots1201.readOnly(screenshotFile) && !this.selectedForDeletion;
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
   private ResourceLocation thumbnailTextureId() {
      if (!this.thumbnailImage.file.isDone()) {
         return this.screenshotImage.textureId();
      } else {
         ResourceLocation texture = this.thumbnailImage.textureId();
         return texture == null ? this.screenshotImage.textureId() : texture;
      }
   }

   @Override
   public File getScreenshotFile() {
      return this.screenshotFile;
   }

   @Override
   public void openFile() {
      ScreenshotViewerUtils.openPath(this.screenshotFile);
   }

   @Override
   public void copyScreenshot() {
      ScreenshotViewerUtils.copyImageToClipboard(this.screenshotFile);
   }

   @Override
   public void requestFileDeletion() {
      if (GlobalScreenshots1201.readOnly(screenshotFile)) return;
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
                  Component.translatable("screen.lads_screenshots.delete_prompt", this.screenshotFile.getName()),
                  ScreenshotViewerTexts.DELETE_WARNING_MESSAGE
               )
            );
      } else {
         deleteAction.accept(true);
      }
   }

   @Override
   public void renameFile() {
      if (GlobalScreenshots1201.readOnly(screenshotFile)) return;
      String fileName = this.screenshotFile.getName();
      this.mainScreen
         .setDialogScreen(
            new RenameScreenshotScreen(
               fileName.substring(0, fileName.lastIndexOf(46)),
               s -> {
                  try {
                     Path moved = ScreenshotFileIO.rename(this.screenshotFile.toPath(), s);
                     this.ctx.renamed(this.screenshotFile, moved.toFile());
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
   public ResourceLocation textureId() {
      return this.screenshotImage.textureId();
   }

   @Nullable
   @Override
   public NativeImage image() {
      return this.screenshotImage.image();
   }

   @Override
   public boolean keyPressed(int key, int scanCode, int modifiers) {
      if (this.isHovered && key == InputConstants.KEY_C && (modifiers & InputConstants.MOD_CONTROL) != 0) {
         this.copyScreenshot();
         return true;
      } else {
         return super.keyPressed(key, scanCode, modifiers);
      }
   }

   @Override
   public Component getMessage() {
      return this.screenshotFile == null ? super.getMessage() : Component.literal(this.screenshotFile.getName());
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.isHovered()) {
         this.playDownSound(this.client.getSoundManager());
         if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            this.onClick();
         }

         if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.onRightClick(mouseX, mouseY);
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return this.isHovered();
   }

   @Override
   protected void updateWidgetNarration(NarrationElementOutput builder) {
   }

   @Override
   public void close() {
      this.screenshotImage.close();
      this.thumbnailImage.close();
   }

   interface Context {
      int screenshotsPerRow();

      int currentIndex(ScreenshotWidget widget);

      void removeEntry(ScreenshotWidget widget);

      /** A scan still running must list the new name, not bring back the old one. */
      void renamed(File from, File to);
   }

   class ImageLoader implements AutoCloseable {
      private final String imageType;
      private CompletableFuture<File> file;
      private CompletableFuture<NativeImage> image;
      @Nullable
      private ResourceLocation textureId;

      ImageLoader(String imageType) {
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
         }, ScreenshotFileIO.IMAGE_EXECUTOR);
      }

      @Nullable
      public ResourceLocation textureId() {
         if (this.textureId != null) {
            return this.textureId;
         } else {
            if (this.image == null) {
               this.image = this.getImage();
            }

            NativeImage nativeImage;
            if (this.image.isDone() && (nativeImage = this.image.join()) != null) {
               String hash = java.util.UUID.randomUUID().toString();
               this.textureId = new ResourceLocation("lads_screenshots", "dynamic/" + this.imageType.toLowerCase() + "/" + hash);
               DynamicTexture texture = new DynamicTexture(nativeImage);
               ScreenshotWidget.this.client.getTextureManager().register(this.textureId, texture);
               texture.setFilter(true, false); // smooth scaling: the cards show the picture far smaller than it is
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
