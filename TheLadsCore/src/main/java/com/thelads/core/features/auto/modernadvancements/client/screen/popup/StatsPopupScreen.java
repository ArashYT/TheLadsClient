package com.thelads.core.features.auto.modernadvancements.client.screen.popup;

import java.util.List;
import com.thelads.core.features.auto.modernadvancements.data.api.TabCompletionStat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class StatsPopupScreen {
   public static final int POPUP_WIDTH = 280;
   private static final int POPUP_Y = 80;
   private static final int TITLE_H = 24;
   private static final int OVERALL_BAR_H = 18;
   private static final int ROW_BAR_H = 5;
   private static final int SCROLLBAR_W = 5;
   public boolean visible = false;
   private final Font font;
   private int scrollOffset = 0;
   private int maxScroll = 0;
   private boolean isDraggingScroll = false;
   private int cachedListTop = 0;
   private int cachedListBottom = 0;
   private int cachedPopupX = 0;
   private int cachedPopupBottom = 0;

   public StatsPopupScreen(Font font) {
      this.font = font;
   }

   public boolean contains(double mouseX, double mouseY) {
      return mouseX >= this.cachedPopupX && mouseX <= this.cachedPopupX + 280 && mouseY >= 80.0 && mouseY <= this.cachedPopupBottom;
   }

   public void render(GuiGraphicsExtractor context, int screenWidth, int screenHeight, List<TabCompletionStat> stats, boolean hasServerData) {
      if (this.visible) {
         int totalC = 0;
         int totalT = 0;

         for (TabCompletionStat s : stats) {
            totalC += s.completed();
            totalT += s.total();
         }

         int headerH = 35 + (hasServerData ? 0 : 9 + 2) + 6;
         int contentH = headerH + stats.size() * 24 + 4;
         int popupX = (screenWidth - 280) / 2;
         int availH = screenHeight - 80 - 50;
         int popupH = Math.min(contentH, availH);
         this.cachedPopupX = popupX;
         this.cachedPopupBottom = 80 + popupH;
         this.maxScroll = Math.max(0, contentH - popupH);
         this.scrollOffset = Math.clamp((long)this.scrollOffset, 0, this.maxScroll);
         this.cachedListTop = 80 + headerH;
         this.cachedListBottom = 80 + popupH - 2;
         context.fill(popupX, 80, popupX + 280, 80 + popupH, -267316975);
         context.outline(popupX, 80, 280, popupH, -8355712);
         context.centeredText(this.font, Component.translatable("gui.advancements.stats.title"), popupX + 140, 84, -1);
         context.fill(popupX + 8, 102, popupX + 280 - 8, 103, -11513776);
         int barX = popupX + 8;
         int barY = 97;
         int barW = 264;
         context.fill(barX, barY, barX + barW, barY + 18, -14540254);
         if (totalT > 0 && totalC > 0) {
            int filled = totalC >= totalT ? barW : Math.min(barW - 1, (int)((float)totalC / totalT * barW));
            context.fill(barX, barY, barX + filled, barY + 18, totalC >= totalT ? -16729344 : -14527011);
         }

         context.outline(barX, barY, barW, 18, -12566464);
         String overallLabel = totalC + " / " + totalT + (totalT > 0 ? "  (" + totalC * 100 / totalT + "%)" : "");
         int textY = barY + (18 - 9) / 2;
         context.centeredText(this.font, overallLabel, barX + barW / 2, textY, -1);
         if (!hasServerData) {
            context.text(this.font, Component.translatable("gui.advancements.stats.unknown"), barX, barY + 18 + 2, -7829368, false);
         }

         context.enableScissor(popupX + 2, this.cachedListTop, popupX + 280 - 2, this.cachedListBottom);
         int y = this.cachedListTop - this.scrollOffset;

         for (TabCompletionStat stat : stats) {
            this.renderRow(context, popupX + 8, y, 260, stat);
            y += 24;
         }

         context.disableScissor();
         if (this.maxScroll > 0) {
            int sbH = this.cachedListBottom - this.cachedListTop;
            int handleH = Math.max(12, sbH * popupH / contentH);
            int handleY = this.cachedListTop + (int)((sbH - handleH) * ((float)this.scrollOffset / this.maxScroll));
            int sbX = popupX + 280 - 5 - 3;
            context.fill(sbX, this.cachedListTop, sbX + 5, this.cachedListBottom, 1090519039);
            context.fill(sbX, handleY, sbX + 5, handleY + handleH, -5592406);
         }
      }
   }

   private void renderRow(GuiGraphicsExtractor context, int x, int y, int w, TabCompletionStat stat) {
      if (!stat.icon().isEmpty()) {
         context.pose().pushMatrix();
         context.pose().translate(x, y + 1);
         context.pose().scale(0.7F, 0.7F);
         context.fakeItem(stat.icon(), 0, 0);
         context.pose().popMatrix();
         x += 13;
         w -= 13;
      }

      String count = stat.countLabel();
      int countW = this.font.width(count);
      int maxTitleW = w - countW - 6;
      String title = stat.title().getString();
      if (this.font.width(title) > maxTitleW) {
         while (this.font.width(title + "…") > maxTitleW && title.length() > 1) {
            title = title.substring(0, title.length() - 1);
         }

         title = title + "…";
      }

      context.text(this.font, title, x, y + 1, stat.isDone() ? -7798904 : -3355444, false);
      context.text(this.font, count, x + w - countW, y + 1, stat.isDone() ? -16720640 : -5592406, false);
      int barY = y + 13;
      context.fill(x, barY, x + w, barY + 5, -14540254);
      if (stat.total() > 0 && stat.completed() > 0) {
         int filled = stat.completed() >= stat.total() ? w : Math.min(w - 1, (int)((float)stat.completed() / stat.total() * w));
         context.fill(x, barY, x + filled, barY + 5, stat.isDone() ? -16729344 : -14527011);
      }

      context.outline(x, barY, w, 5, -12566464);
   }

   public boolean mouseClicked(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else if (!this.contains(mouseX, mouseY)) {
         return false;
      } else {
         if (this.maxScroll > 0 && mouseY >= this.cachedListTop && mouseY <= this.cachedListBottom) {
            int sbX = this.cachedPopupX + 280 - 5 - 3;
            if (mouseX >= sbX && mouseX <= sbX + 5) {
               this.isDraggingScroll = true;
            }
         }

         return true;
      }
   }

   public void mouseDragged(double mouseY) {
      if (this.isDraggingScroll && this.maxScroll != 0 && this.cachedListBottom > this.cachedListTop) {
         float pct = (float)Math.clamp((mouseY - this.cachedListTop) / (this.cachedListBottom - this.cachedListTop), 0.0, 1.0);
         this.scrollOffset = Math.clamp((long)((int)(pct * this.maxScroll)), 0, this.maxScroll);
      }
   }

   public boolean mouseScrolled(double mouseX, double verticalAmount) {
      if (!this.visible) {
         return false;
      } else {
         if (mouseX >= this.cachedPopupX && mouseX <= this.cachedPopupX + 280 && this.maxScroll > 0) {
            this.scrollOffset = Math.clamp((long)(this.scrollOffset - (int)(verticalAmount * 12.0)), 0, this.maxScroll);
         }

         return true;
      }
   }

   public void mouseReleased() {
      this.isDraggingScroll = false;
   }
}
