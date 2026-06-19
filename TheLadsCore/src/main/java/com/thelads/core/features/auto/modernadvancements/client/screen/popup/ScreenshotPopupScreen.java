package com.thelads.core.features.auto.modernadvancements.client.screen.popup;

import com.thelads.core.features.auto.modernadvancements.data.handler.AdvancementScreenshotManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class ScreenshotPopupScreen {
   public boolean visible = false;
   @Nullable
   private Identifier advId = null;

   public void open(Identifier advId) {
      this.advId = advId;
      this.visible = true;
   }

   public void close() {
      this.visible = false;
      this.advId = null;
   }

   public void render(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
      if (this.visible && this.advId != null) {
         Identifier texId = AdvancementScreenshotManager.getTextureId(this.advId);
         if (texId == null) {
            this.close();
         } else {
            int[] dims = AdvancementScreenshotManager.getTextureDimensions(this.advId);
            if (dims != null && dims[0] > 0) {
               int maxW = (int)(screenWidth * 0.9F);
               int maxH = (int)(screenHeight * 0.9F);
               float imgRatio = (float)dims[0] / dims[1];
               int displayW;
               int displayH;
               if ((float)maxW / maxH > imgRatio) {
                  displayH = maxH;
                  displayW = (int)(maxH * imgRatio);
               } else {
                  displayW = maxW;
                  displayH = (int)(maxW / imgRatio);
               }

               int x = (screenWidth - displayW) / 2;
               int y = (screenHeight - displayH) / 2;
               context.fill(x - 3, y - 3, x + displayW + 3, y + displayH + 3, -14671840);
               context.blit(texId, x, y, x + displayW, y + displayH, 0.0F, 1.0F, 0.0F, 1.0F);
               context.outline(x - 3, y - 3, displayW + 6, displayH + 6, -10461088);
            } else {
               this.close();
            }
         }
      }
   }

   public boolean mouseClicked() {
      if (!this.visible) {
         return false;
      } else {
         this.close();
         return true;
      }
   }
}
