package com.thelads.core.features.auto.modernadvancements.data.handler;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.system.MemoryUtil;

public class AdvancementScreenshotManager {
   private static final long DELAY_MS = 100L;
   private static final String FOLDER = "advancements";
   private static final Deque<AdvancementScreenshotManager.PendingCapture> pendingQueue = new ArrayDeque<>();
   private static final Map<Identifier, Identifier> registeredTextures = new ConcurrentHashMap<>();
   private static final Map<Identifier, int[]> textureDimensions = new ConcurrentHashMap<>();
   private static final Set<Identifier> pendingLoad = ConcurrentHashMap.newKeySet();
   private static final int TARGET_WIDTH = 1920;
   private static final int TARGET_HEIGHT = 1080;
   public static boolean fixedSize = ModernAdvancementsClient.CONFIG.matchWindowSize();

   public static void schedule(AdvancementHolder holder) {
      pendingQueue.add(new AdvancementScreenshotManager.PendingCapture(holder, Util.getMillis() + 100L));
   }

   public static void checkAndCapture(Minecraft mc) {
      if (ModernAdvancementsClient.CONFIG.takeScreenshots()) {
         if (pendingQueue.isEmpty()) {
            return;
         }

         long now = Util.getMillis();

         while (!pendingQueue.isEmpty() && now >= pendingQueue.peek().captureTime()) {
            capture(mc, pendingQueue.poll().holder());
         }
      }
   }

   private static void capture(Minecraft mc, AdvancementHolder holder) {
      Identifier advId = holder.id();
      File outputFile = getFile(advId, mc);
      boolean var4 = outputFile.getParentFile().mkdirs();
      Screenshot.takeScreenshot(
         mc.getMainRenderTarget(),
         image -> Util.ioPool()
            .execute(
               () -> {
                  try {
                     if (!fixedSize) {
                        NativeImage rgb = toRgb(image);

                        try {
                           rgb.writeToFile(outputFile);
                        } catch (Throwable var20) {
                           if (rgb != null) {
                              try {
                                 rgb.close();
                              } catch (Throwable var18) {
                                 var20.addSuppressed(var18);
                              }
                           }

                           throw var20;
                        }

                        if (rgb != null) {
                           rgb.close();
                        }
                     } else {
                        NativeImage scaled = new NativeImage(1920, 1080, false);

                        try {
                           STBImageResize.nstbir_resize_uint8_srgb(
                              image.getPointer(), image.getWidth(), image.getHeight(), image.getWidth() * 4, scaled.getPointer(), 1920, 1080, 7680, 4
                           );
                           NativeImage rgb = toRgb(scaled);

                           try {
                              rgb.writeToFile(outputFile);
                           } catch (Throwable var19) {
                              if (rgb != null) {
                                 try {
                                    rgb.close();
                                 } catch (Throwable var17) {
                                    var19.addSuppressed(var17);
                                 }
                              }

                              throw var19;
                           }

                           if (rgb != null) {
                              rgb.close();
                           }
                        } catch (Throwable var21) {
                           try {
                              scaled.close();
                           } catch (Throwable var16) {
                              var21.addSuppressed(var16);
                           }

                           throw var21;
                        }

                        scaled.close();
                     }

                     registeredTextures.remove(advId);
                     pendingLoad.remove(advId);
                  } catch (Exception var22) {
                  } finally {
                     image.close();
                  }
               }
            )
      );
   }

   private static NativeImage toRgb(NativeImage rgba) {
      int w = rgba.getWidth();
      int h = rgba.getHeight();
      NativeImage rgb = new NativeImage(Format.RGB, w, h, false);
      long src = rgba.getPointer();
      long dst = rgb.getPointer();
      int pixels = w * h;

      for (int i = 0; i < pixels; i++) {
         MemoryUtil.memPutByte(dst + i * 3L, MemoryUtil.memGetByte(src + i * 4L));
         MemoryUtil.memPutByte(dst + i * 3L + 1L, MemoryUtil.memGetByte(src + i * 4L + 1L));
         MemoryUtil.memPutByte(dst + i * 3L + 2L, MemoryUtil.memGetByte(src + i * 4L + 2L));
      }

      return rgb;
   }

   public static void ensureLoaded(Identifier advId, Minecraft mc) {
      if (!registeredTextures.containsKey(advId)) {
         if (!pendingLoad.contains(advId)) {
            File file = getFile(advId, mc);
            if (file.exists()) {
               pendingLoad.add(advId);
               Identifier texId = Identifier.fromNamespaceAndPath("modern-advancements", "screenshots/" + advId.getNamespace() + "/" + advId.getPath());
               Util.ioPool().execute(() -> {
                  try {
                     NativeImage img = NativeImage.read(Files.newInputStream(file.toPath()));
                     int w = img.getWidth();
                     int h = img.getHeight();
                     mc.execute(() -> {
                        DynamicTexture tex = new DynamicTexture(() -> "advancement_screenshot_" + advId, img);
                        mc.getTextureManager().register(texId, tex);
                        registeredTextures.put(advId, texId);
                        textureDimensions.put(advId, new int[]{w, h});
                        pendingLoad.remove(advId);
                     });
                  } catch (Exception var7) {
                     pendingLoad.remove(advId);
                  }
               });
            }
         }
      }
   }

   @Nullable
   public static Identifier getTextureId(Identifier advId) {
      return registeredTextures.get(advId);
   }

   @Nullable
   public static int[] getTextureDimensions(Identifier advId) {
      return textureDimensions.get(advId);
   }

   public static File getFile(Identifier advId, Minecraft mc) {
      String relativePath = advId.getNamespace() + File.separator + advId.getPath().replace("/", File.separator) + ".png";
      return new File(new File(new File(mc.gameDirectory, "screenshots"), "advancements"), relativePath);
   }

   public static void clearForDisconnect() {
      pendingQueue.clear();
   }

   private record PendingCapture(AdvancementHolder holder, long captureTime) {
   }
}
