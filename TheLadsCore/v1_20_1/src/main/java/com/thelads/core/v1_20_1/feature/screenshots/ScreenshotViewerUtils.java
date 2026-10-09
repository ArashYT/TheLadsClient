// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.thelads.core.v1_20_1.feature.GlobalScreenshots1201;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotChatActionClickEvent;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotViewerTexts;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2ic;
import org.slf4j.Logger;

public class ScreenshotViewerUtils {
   private static final Logger LOGGER = LogUtils.getLogger();
   /** The AWT clipboard is looked up on first use, so starting the game never initialises the AWT toolkit. */
   private static final class AwtClipboard {
      @Nullable
      static final Clipboard INSTANCE = tryGetAWTClipboard();
   }
   private static final SystemToast.SystemToastIds COPY_SCREENSHOT = SystemToast.SystemToastIds.PERIODIC_NOTIFICATION;
   /** The hint tooltip is drawn after everything else of the frame, so no later widget covers it. */
   @Nullable
   private static Runnable deferredTooltip;

   /** Decodes an image file into a texture image (at most 1024 on its longer edge for a preview), converting to the ABGR NativeImage uses on 1.20.1. */
   public static NativeImage readNative(File file, boolean preview) throws java.io.IOException {
      BufferedImage source = ScreenshotFileIO.read(file.toPath(), preview ? 1024 : 16384);
      NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), false);
      try {
         int[] row = new int[source.getWidth()];
         for (int y = 0; y < source.getHeight(); y++) {
            source.getRGB(0, y, source.getWidth(), 1, row, 0, source.getWidth());
            for (int x = 0; x < row.length; x++) {
               int argb = row[x];
               image.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb >> 16) & 0xFF | (argb & 0xFF) << 16);
            }
         }
         return image;
      } catch (Throwable failure) { image.close(); throw failure; }
      finally { source.flush(); }
   }
   public static void fileError(String action, File file, Throwable failure) {
      LOGGER.warn("{} failed for {}", action, file.getName(), failure);
      Minecraft client = Minecraft.getInstance();
      client.execute(() -> SystemToast.addOrUpdate(client.getToasts(), COPY_SCREENSHOT,
         Component.literal(action + " failed"), Component.literal(file.getName() + ": " + failure.getMessage())));
   }

   public static File getVanillaScreenshotsFolder() {
      return GlobalScreenshots1201.directory();
   }

   public static File getDefaultThumbnailFolder() {
      return new File(getVanillaScreenshotsFolder(), "thumbnails");
   }

   public static List<File> getScreenshotFiles(File screenshotsFolder) {
      File[] files = screenshotsFolder.listFiles();
      return files == null
         ? List.of()
         : Arrays.stream(files)
            .filter(file -> ScreenshotFileIO.imageFile(file.toPath()))
            .collect(Collectors.toList());
   }

   /** Opens a file with the system's default program (screenshots) or a folder in the file manager. */
   public static void openPath(File path) {
      Util.getPlatform().openFile(path);
   }

   public static void drawTexture(
      GuiGraphics context,
      ResourceLocation texture,
      int x,
      int y,
      int width,
      int height,
      int u,
      int v,
      int regionWidth,
      int regionHeight,
      int textureWidth,
      int textureHeight
   ) {
      context.blit(texture, x, y, width, height, (float) u, (float) v, regionWidth, regionHeight, textureWidth, textureHeight);
   }

   @Nullable
   private static Clipboard tryGetAWTClipboard() {
      if (Util.getPlatform() == Util.OS.OSX) {
         return null;
      } else {
         try {
            return Toolkit.getDefaultToolkit().getSystemClipboard();
         } catch (Throwable var1) {
            LOGGER.error("Unable to retrieve Java AWT Clipboard instance!", var1);
            return null;
         }
      }
   }

   public static void copyImageToClipboard(File screenshotFile) {
      if (Util.getPlatform() == Util.OS.OSX) {
         // The Objective-C bridge the 26.3 gallery uses for the macOS pasteboard is not part of the 1.20.1 build.
         fileError("Copy", screenshotFile, new IllegalStateException("Copying an image is not supported on macOS in this version"));
         return;
      }
      Clipboard clipboard = AwtClipboard.INSTANCE;
      if (clipboard == null) { fileError("Copy", screenshotFile, new IllegalStateException("System image clipboard unavailable")); return; }
      CompletableFuture.runAsync(() -> {
         try {
            BufferedImage image = ScreenshotFileIO.read(screenshotFile.toPath());
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = rgb.createGraphics();
            try { graphics.drawImage(image, 0, 0, null); } finally { graphics.dispose(); image.flush(); }
            clipboard.setContents(new ImageTransferable(rgb), null);
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> SystemToast.addOrUpdate(client.getToasts(), COPY_SCREENSHOT,
               ScreenshotViewerTexts.TOAST_COPY_SUCCESS, Component.literal(screenshotFile.getName())));
         } catch (Throwable failure) { fileError("Copy", screenshotFile, failure); }
      }, ScreenshotFileIO.IMAGE_EXECUTOR);
   }

   /** The hover hint, split into lines like a vanilla tooltip. */
   public static List<FormattedCharSequence> toTooltipLines(Minecraft client, Component text) {
      return Tooltip.splitTooltip(client, text);
   }

   /** Asks for a fading hint tooltip at the end of the frame; {@link #flushCustomTooltip()} draws it. color carries the fade as its alpha. */
   public static void renderCustomTooltip(GuiGraphics context, Font textRenderer, List<FormattedCharSequence> text, int posX, int posY, int color) {
      if (deferredTooltip == null) {
         deferredTooltip = () -> drawCustomTooltip(context, textRenderer, text, posX, posY, color);
      }
   }

   /** Draws the tooltip asked for during this frame, if any. Call once, after everything else of the screen. */
   public static void flushCustomTooltip() {
      Runnable tooltip = deferredTooltip;
      deferredTooltip = null;
      if (tooltip != null) tooltip.run();
   }

   private static void drawCustomTooltip(GuiGraphics context, Font textRenderer, List<FormattedCharSequence> text, int posX, int posY, int color) {
      int totWidth = 0;
      int totHeight = text.size() == 1 ? -2 : 0;

      for (FormattedCharSequence line : text) {
         totWidth = Math.max(totWidth, textRenderer.width(line));
         totHeight += 10;
      }

      ClientTooltipPositioner positioner = DefaultTooltipPositioner.INSTANCE;
      Vector2ic position = positioner.positionTooltip(context.guiWidth(), context.guiHeight(), posX, posY, totWidth, totHeight);
      int x = position.x();
      int y = position.y();
      float fade = (color >>> 24) / 255.0F;
      context.pose().pushPose();
      context.pose().translate(0.0F, 0.0F, 400.0F);
      // The vanilla tooltip box, faded with the same alpha the 26.3 sprite tint has.
      int background = (int) (0xF0 * fade) << 24 | 0x100010;
      int borderTop = (int) (0x50 * fade) << 24 | 0x5000FF;
      int borderBottom = (int) (0x50 * fade) << 24 | 0x28007F;
      int left = x - 3;
      int top = y - 3;
      int width = totWidth + 6;
      int height = totHeight + 6;
      context.fill(left, top - 1, left + width, top, background);
      context.fill(left, top + height, left + width, top + height + 1, background);
      context.fill(left, top, left + width, top + height, background);
      context.fill(left - 1, top, left, top + height, background);
      context.fill(left + width, top, left + width + 1, top + height, background);
      context.fillGradient(left, top + 1, left + 1, top + height - 1, borderTop, borderBottom);
      context.fillGradient(left + width - 1, top + 1, left + width, top + height - 1, borderTop, borderBottom);
      context.fill(left, top, left + width, top + 1, borderTop);
      context.fill(left, top + height - 1, left + width, top + height, borderBottom);
      int drawY = y;

      if ((color >>> 24) >= 5) { // Font treats a nearly clear alpha as opaque
         for (int q = 0; q < text.size(); q++) {
            context.drawString(textRenderer, text.get(q), x, drawY, color, true);
            drawY += 10 + (q == 0 ? 2 : 0);
         }
      }

      context.pose().popPose();
   }

   public static void renderWidget(AbstractWidget widget, GuiGraphics context, int mouseX, int mouseY, float delta) {
      widget.render(context, mouseX, mouseY, delta);
   }

   public static void forEachDrawable(Screen screen, Consumer<Renderable> renderer) {
      forEachOfType(screen, Renderable.class, renderer);
   }

   public static <T> void forEachOfType(Screen screen, Class<T> type, Consumer<T> action) {
      screen.children().stream().filter(type::isInstance).map(type::cast).forEachOrdered(action);
   }

   public static Component ofSupplied(Supplier<Component> textSupplier) {
      return MutableComponent.create(new ScreenshotViewerUtils.ClientSideSuppliedTextContent(textSupplier));
   }

   record ClientSideSuppliedTextContent(@NotNull Supplier<Component> s) implements ComponentContents {
      @Override
      public <T> Optional<T> visit(FormattedText.ContentConsumer<T> visitor) {
         Component r = this.s.get();
         return r == null ? Optional.empty() : r.visit(visitor);
      }

      @Override
      public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> visitor, Style style) {
         Component r = this.s.get();
         return r == null ? Optional.empty() : r.visit(visitor, style);
      }

      @Override
      public String toString() {
         return "clientsideSupplied{}";
      }
   }

   record ImageTransferable(Image image) implements Transferable {
      @Override
      public DataFlavor[] getTransferDataFlavors() {
         return new DataFlavor[]{DataFlavor.imageFlavor};
      }

      @Override
      public boolean isDataFlavorSupported(DataFlavor flavor) {
         return DataFlavor.imageFlavor.equals(flavor);
      }

      @NotNull
      @Override
      public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
         if (!this.isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
         } else {
            return this.image();
         }
      }
   }

   public static void uploadToImgur(File screenshotFile) {
      Minecraft client = Minecraft.getInstance();
      CompletableFuture.runAsync(() -> {
         try {
            byte[] bytes = java.nio.file.Files.readAllBytes(screenshotFile.toPath());
            java.net.URL url = new java.net.URI("https://api.imgur.com/3/image").toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Client-ID 546c25a59c58ad7");
            conn.setDoOutput(true);
            conn.getOutputStream().write(bytes);
            conn.getOutputStream().flush();
            int code = conn.getResponseCode();
            if (code == 200) {
               try (var reader = new java.io.InputStreamReader(conn.getInputStream())) {
                  com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                  String link = json.getAsJsonObject("data").get("link").getAsString();
                  client.execute(() -> {
                     client.keyboardHandler.setClipboard(link);
                     client.gui.setOverlayMessage(Component.literal("Imgur link copied: " + link), false);
                  });
               }
            } else {
               client.execute(() -> client.gui.setOverlayMessage(Component.literal("Imgur upload failed: HTTP " + code), false));
            }
         } catch (Throwable t) {
            client.execute(() -> client.gui.setOverlayMessage(Component.literal("Imgur upload failed: " + t.getMessage()), false));
         }
      }, ScreenshotFileIO.IMAGE_EXECUTOR);
   }

   public static MutableComponent appendScreenshotButtons(MutableComponent base, File file) {
      var copyBtn = Component.literal(" [Copy]")
         .withStyle(s -> s.withColor(ChatFormatting.GOLD)
            .withClickEvent(new ScreenshotChatActionClickEvent(file, ScreenshotChatActionClickEvent.ActionType.COPY))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Copy image to clipboard"))));

      var openFileBtn = Component.literal(" [Open File]")
         .withStyle(s -> s.withColor(ChatFormatting.GREEN)
            .withClickEvent(new ScreenshotChatActionClickEvent(file, ScreenshotChatActionClickEvent.ActionType.OPEN_FILE))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Open screenshot file"))));

      var openFolderBtn = Component.literal(" [Open Folder]")
         .withStyle(s -> s.withColor(ChatFormatting.AQUA)
            .withClickEvent(new ScreenshotChatActionClickEvent(file, ScreenshotChatActionClickEvent.ActionType.OPEN_FOLDER))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Open screenshots folder"))));

      var imgurBtn = Component.literal(" [Upload to Imgur]")
         .withStyle(s -> s.withColor(ChatFormatting.LIGHT_PURPLE)
            .withClickEvent(new ScreenshotChatActionClickEvent(file, ScreenshotChatActionClickEvent.ActionType.UPLOAD_IMGUR))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Upload to Imgur and copy link"))));

      return base.append(copyBtn).append(openFileBtn).append(openFolderBtn).append(imgurBtn);
   }
}
