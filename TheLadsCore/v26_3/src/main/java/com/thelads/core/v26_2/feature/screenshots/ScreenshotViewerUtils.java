// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots;

import com.mojang.logging.LogUtils;
import com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotViewerTexts;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FormattedText.ContentConsumer;
import net.minecraft.network.chat.FormattedText.StyledContentConsumer;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.util.Util.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2ic;
import org.slf4j.Logger;

public class ScreenshotViewerUtils {
   private static final Logger LOGGER = LogUtils.getLogger();
   @Nullable
   private static final Clipboard AWT_CLIPBOARD = tryGetAWTClipboard();
   private static final SystemToastId COPY_SCREENSHOT = new SystemToastId(3000L);
   private static Field TOOLTIP_DRAWER_FIELD;
   private static final Identifier DEFAULT_TOOLTIP_BACKGROUND_TEXTURE = Identifier.withDefaultNamespace("tooltip/background");
   private static final Identifier DEFAULT_TOOLTIP_FRAME_TEXTURE = Identifier.withDefaultNamespace("tooltip/frame");

   public static com.mojang.blaze3d.platform.NativeImage readNative(File file, boolean preview) throws java.io.IOException {
      BufferedImage source = ScreenshotFileIO.read(file.toPath(), preview ? 1024 : 16384);
      var image = new com.mojang.blaze3d.platform.NativeImage(source.getWidth(), source.getHeight(), false);
      try {
         for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) image.setPixel(x, y, source.getRGB(x, y));
         return image;
      } catch (Throwable failure) { image.close(); throw failure; }
      finally { source.flush(); }
   }
   public static void fileError(String action, File file, Throwable failure) {
      LOGGER.warn("{} failed for {}", action, file.getName(), failure);
      Minecraft client = Minecraft.getInstance();
      client.execute(() -> SystemToast.addOrUpdate(client.gui.toastManager(), COPY_SCREENSHOT,
         Component.literal(action + " failed"), Component.literal(file.getName() + ": " + failure.getMessage())));
   }

   public static File getVanillaScreenshotsFolder() {
      return com.thelads.core.v26_2.feature.GlobalScreenshots.directory();
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

   public static void drawTexture(
      GuiGraphicsExtractor context,
      Identifier texture,
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
      context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
   }

   @Nullable
   private static Clipboard tryGetAWTClipboard() {
      if (Util.getPlatform() == OS.OSX) {
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
         try { ScreenshotViewerMacOsUtils.doCopyMacOS(screenshotFile.getAbsolutePath()); }
         catch (Throwable failure) { fileError("Copy", screenshotFile, failure); }
         return;
      }
      if (AWT_CLIPBOARD == null) { fileError("Copy", screenshotFile, new IllegalStateException("System image clipboard unavailable")); return; }
      CompletableFuture.runAsync(() -> {
         try {
            BufferedImage image = ScreenshotFileIO.read(screenshotFile.toPath());
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = rgb.createGraphics();
            try { graphics.drawImage(image, 0, 0, null); } finally { graphics.dispose(); image.flush(); }
            AWT_CLIPBOARD.setContents(new ImageTransferable(rgb), null);
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> SystemToast.addOrUpdate(client.gui.toastManager(), COPY_SCREENSHOT,
               ScreenshotViewerTexts.TOAST_COPY_SUCCESS, Component.literal(screenshotFile.getName())));
         } catch (Throwable failure) { fileError("Copy", screenshotFile, failure); }
      }, com.thelads.core.v26_2.feature.screenshots.ScreenshotFileIO.IMAGE_EXECUTOR);
   }

   public static List<ClientTooltipComponent> toColoredComponents(Minecraft client, Component text) {
      return Tooltip.splitTooltip(client, text).stream().map(ScreenshotViewerUtils.ColoredTooltipComponents::new).collect(Collectors.toList());
   }

   public static void renderCustomTooltip(GuiGraphicsExtractor context, Font textRenderer, List<ClientTooltipComponent> text, int posX, int posY, int color) {
      if (TOOLTIP_DRAWER_FIELD == null) {
         try {
            TOOLTIP_DRAWER_FIELD = GuiGraphicsExtractor.class.getDeclaredField("deferredTooltip");
            TOOLTIP_DRAWER_FIELD.setAccessible(true);
         } catch (NoSuchFieldException var8) {
            throw new RuntimeException(var8);
         }
      }

      try {
         Object tooltipDrawer = TOOLTIP_DRAWER_FIELD.get(context);
         if (tooltipDrawer == null) {
            TOOLTIP_DRAWER_FIELD.set(context, (Runnable)() -> drawCustomTooltip(context, textRenderer, text, posX, posY, color));
         }
      } catch (IllegalAccessException var7) {
         throw new RuntimeException(var7);
      }
   }

   private static void drawCustomTooltip(GuiGraphicsExtractor context, Font textRenderer, List<ClientTooltipComponent> text, int posX, int posY, int color) {
      int totWidth = 0;
      int totHeight = text.size() == 1 ? -2 : 0;

      for (ClientTooltipComponent comp : text) {
         int compWidth = comp.getWidth(textRenderer);
         if (compWidth > totWidth) {
            totWidth = compWidth;
         }

         totHeight += comp.getHeight(textRenderer);
      }

      ClientTooltipPositioner positioner = DefaultTooltipPositioner.INSTANCE;
      Vector2ic vector2ic = positioner.positionTooltip(context.guiWidth(), context.guiHeight(), posX, posY, totWidth, totHeight);
      int x = vector2ic.x();
      int y = vector2ic.y();
      context.pose().pushMatrix();
      int bgX = x - 3 - 9;
      int bgY = y - 3 - 9;
      int bgWidth = totWidth + 3 + 3 + 18;
      int bgHeight = totHeight + 3 + 3 + 18;
      context.blitSprite(RenderPipelines.GUI_TEXTURED, DEFAULT_TOOLTIP_BACKGROUND_TEXTURE, bgX, bgY, bgWidth, bgHeight, color);
      context.blitSprite(RenderPipelines.GUI_TEXTURED, DEFAULT_TOOLTIP_FRAME_TEXTURE, bgX, bgY, bgWidth, bgHeight, color);
      int drawY = y;

      for (int q = 0; q < text.size(); q++) {
         ClientTooltipComponent comp = text.get(q);
         if (comp instanceof ScreenshotViewerUtils.ColoredTooltipComponents alpha) {
            alpha.drawColoredText(context, textRenderer, x, drawY, color);
         } else {
            comp.extractText(context, textRenderer, x, drawY);
         }

         drawY += comp.getHeight(textRenderer) + (q == 0 ? 2 : 0);
      }

      drawY = y;

      for (int q = 0; q < text.size(); q++) {
         ClientTooltipComponent comp = text.get(q);
         comp.extractImage(textRenderer, x, drawY, totWidth, totHeight, context);
         drawY += comp.getHeight(textRenderer) + (q == 0 ? 2 : 0);
      }

      context.pose().popMatrix();
   }

   public static void renderWidget(AbstractWidget widget, GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      widget.extractRenderState(context, mouseX, mouseY, delta);
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

   record ClientSideSuppliedTextContent(@NotNull Supplier<Component> s) implements PlainTextContents {
      public String text() {
         return "";
      }

      public <T> Optional<T> visit(ContentConsumer<T> visitor) {
         Component r = this.s.get();
         return r == null ? Optional.empty() : r.visit(visitor);
      }

      public <T> Optional<T> visit(StyledContentConsumer<T> visitor, Style style) {
         Component r = this.s.get();
         return r == null ? Optional.empty() : r.visit(visitor, style);
      }

      @Override
      public String toString() {
         return "clientsideSupplied{}";
      }
   }

   static class ColoredTooltipComponents implements ClientTooltipComponent {
      private final FormattedCharSequence text;

      public ColoredTooltipComponents(FormattedCharSequence text) {
         this.text = text;
      }

      public int getWidth(Font textRenderer) {
         return textRenderer.width(this.text);
      }

      public int getHeight(Font textRenderer) {
         return 10;
      }

      public void extractText(GuiGraphicsExtractor context, Font textRenderer, int x, int y) {
         context.text(textRenderer, this.text, x, y, -1, true);
      }

      public void drawColoredText(GuiGraphicsExtractor context, Font textRenderer, int x, int y, int color) {
         context.text(textRenderer, this.text, x, y, color, true);
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
                     client.gui.hud.setOverlayMessage(Component.literal("Imgur link copied: " + link), false);
                  });
               }
            } else {
               client.execute(() -> client.gui.hud.setOverlayMessage(Component.literal("Imgur upload failed: HTTP " + code), false));
            }
         } catch (Throwable t) {
            client.execute(() -> client.gui.hud.setOverlayMessage(Component.literal("Imgur upload failed: " + t.getMessage()), false));
         }
      }, ScreenshotFileIO.IMAGE_EXECUTOR);
   }

   public static MutableComponent appendScreenshotButtons(MutableComponent base, File file) {
      var copyBtn = Component.literal(" [Copy]")
         .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GOLD)
            .withClickEvent(new com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotChatActionClickEvent(file, com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotChatActionClickEvent.ActionType.COPY))
            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("Copy image to clipboard"))));

      var openFileBtn = Component.literal(" [Open File]")
         .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN)
            .withClickEvent(new com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotChatActionClickEvent(file, com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotChatActionClickEvent.ActionType.OPEN_FILE))
            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("Open screenshot file"))));

      var openFolderBtn = Component.literal(" [Open Folder]")
         .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.AQUA)
            .withClickEvent(new com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotChatActionClickEvent(file, com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotChatActionClickEvent.ActionType.OPEN_FOLDER))
            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("Open screenshots folder"))));

      var imgurBtn = Component.literal(" [Upload to Imgur]")
         .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.LIGHT_PURPLE)
            .withClickEvent(new com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotChatActionClickEvent(file, com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotChatActionClickEvent.ActionType.UPLOAD_IMGUR))
            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("Upload to Imgur and copy link"))));

      return base.append(copyBtn).append(openFileBtn).append(openFolderBtn).append(imgurBtn);
   }
}
