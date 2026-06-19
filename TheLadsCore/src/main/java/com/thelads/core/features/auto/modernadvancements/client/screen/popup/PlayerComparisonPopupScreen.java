package com.thelads.core.features.auto.modernadvancements.client.screen.popup;

import java.util.List;
import com.thelads.core.features.auto.modernadvancements.data.api.TabDetail;
import com.thelads.core.features.auto.modernadvancements.network.ModernAdvancementsPackets;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class PlayerComparisonPopupScreen {
   private static final int PANEL_W = 420;
   private static final int TITLE_H = 32;
   private static final int ROW_H = 22;
   private static final int SCROLLBAR_W = 8;
   private static final int PAD = 8;
   private static final int ADV_TOP = 70;
   public boolean visible = false;
   private final Font font;
   private int scrollOffset = 0;
   private boolean draggingScroll = false;
   @Nullable
   private ModernAdvancementsPackets.PlayerDetailPacket data;

   public PlayerComparisonPopupScreen(Font font) {
      this.font = font;
   }

   public void open(ModernAdvancementsPackets.PlayerDetailPacket packet) {
      this.data = packet;
      this.scrollOffset = 0;
      this.visible = true;
   }

   public void close() {
      this.visible = false;
      this.data = null;
   }

   private int[] geo(int tabCount, int sw, int sh) {
      int px = (sw - 420) / 2;
      int py = 80;
      int contentH = sh - 70 - 60;
      int maxRows = Math.max(1, (contentH - 32 - 16) / 22);
      int ph = 32 + Math.clamp(tabCount == 0 ? 1L : tabCount, 1, maxRows) * 22 + 16;
      int ly = py + 32 + 8;
      int visH = ph - 32 - 16;
      int maxScroll = Math.max(0, tabCount * 22 - visH);
      return new int[]{px, py, ph, ly, visH, maxScroll};
   }

   public void render(GuiGraphicsExtractor ctx, int mx, int my, int sw, int sh) {
      if (this.data != null) {
         List<TabDetail> tabs = this.data.tabs();
         int[] g = this.geo(tabs.size(), sw, sh);
         int px = g[0];
         int py = g[1];
         int ph = g[2];
         int ly = g[3];
         int visH = g[4];
         int maxScroll = g[5];
         this.scrollOffset = Math.clamp((long)this.scrollOffset, 0, maxScroll);
         ctx.fill(px, py, px + 420, py + ph, -267316975);
         ctx.outline(px, py, 420, ph, -8355712);
         ctx.centeredText(this.font, Component.literal(this.data.playerName() + "'s Advancements"), px + 210, py + 5, -1);
         int totalCompleted = tabs.stream().mapToInt(t -> t.completedIds().size()).sum();
         int totalAll = tabs.stream().mapToInt(TabDetail::total).sum();
         String summary = totalCompleted + " / " + totalAll + " (" + Math.round(totalAll > 0 ? (float)totalCompleted / totalAll * 100.0F : 0.0F) + "%)";
         ctx.centeredText(this.font, Component.literal(summary), px + 210, py + 5 + 9 + 2, -5592406);
         ctx.fill(px + 8, py + 32 - 2, px + 420 - 8, py + 32 - 1, -11513776);
         ctx.enableScissor(px, ly, px + 420, ly + visH);
         if (tabs.isEmpty()) {
            ctx.centeredText(this.font, Component.literal("No data available"), px + 210, ly + 4, -7829368);
            ctx.disableScissor();
         } else {
            int sbX = px + 420 - 8 - 8;
            int ry = ly - this.scrollOffset;

            for (TabDetail tab : tabs) {
               int completed = tab.completedIds().size();
               float pct = tab.total() > 0 ? (float)completed / tab.total() : 0.0F;
               boolean done = completed >= tab.total() && tab.total() > 0;
               ctx.text(this.font, this.clip(tab.tabTitle(), 120), px + 8 + 4, ry + (22 - 9) / 2, done ? -16720640 : -3355444, false);
               int barX = px + 8 + 130;
               int barW = sbX - barX - 68;
               int barY = ry + 11 - 3;
               ctx.fill(barX, barY, barX + barW, barY + 6, -13421773);
               if (pct > 0.0F) {
                  int filled = (int)(pct * barW);
                  ctx.fill(barX, barY, barX + Math.max(1, filled), barY + 6, done ? -16729344 : -14527011);
               }

               ctx.outline(barX, barY, barW, 6, -11184811);
               String stat = completed + "/" + tab.total();
               ctx.text(this.font, stat, sbX - 4 - this.font.width(stat), ry + (22 - 9) / 2, done ? -16733696 : -7829368, false);
               ry += 22;
            }

            ctx.disableScissor();
            if (maxScroll > 0) {
               int totalH = Math.max(1, tabs.size() * 22);
               int handleH = Math.max(20, visH * visH / totalH);
               int handleY = ly + 2 + (int)((float)(visH - handleH) * this.scrollOffset / maxScroll);
               ctx.fill(sbX, ly + 2, sbX + 8, ly + visH - 2, Integer.MIN_VALUE);
               ctx.fill(sbX, handleY, sbX + 8, handleY + handleH, -5592406);
               ctx.outline(sbX, handleY, 8, handleH, -3355444);
            }

            int closeBtnX = px + 420 - 18;
            boolean closeHovered = mx >= closeBtnX && mx <= closeBtnX + 14 && my >= py + 5 && my <= py + 19;
            ctx.fill(closeBtnX, py + 5, closeBtnX + 14, py + 19, closeHovered ? -6737101 : -10079437);
            ctx.outline(closeBtnX, py + 5, 14, 14, -5618620);
            ctx.centeredText(this.font, "X", closeBtnX + 7, py + 8, -21846);
         }
      }
   }

   public boolean mouseClicked(double mx, double my, int sw, int sh) {
      if (this.data == null) {
         return false;
      } else {
         List<TabDetail> tabs = this.data.tabs();
         int[] g = this.geo(tabs.size(), sw, sh);
         int px = g[0];
         int py = g[1];
         int ph = g[2];
         if (!(mx < px) && !(mx > px + 420) && !(my < py) && !(my > py + ph)) {
            int closeBtnX = px + 420 - 18;
            if (mx >= closeBtnX && mx <= closeBtnX + 14 && my >= py + 5 && my <= py + 19) {
               this.close();
               return true;
            } else {
               int sbX = px + 420 - 8 - 8;
               if (mx >= sbX && mx <= sbX + 8) {
                  this.draggingScroll = true;
                  return true;
               } else {
                  return true;
               }
            }
         } else {
            this.close();
            return true;
         }
      }
   }

   public boolean mouseDragged(double my, int sw, int sh) {
      if (this.draggingScroll && this.data != null) {
         int[] g = this.geo(this.data.tabs().size(), sw, sh);
         this.scrollOffset = Math.clamp((long)((int)((float)Math.clamp((my - g[3]) / g[4], 0.0, 1.0) * g[5])), 0, g[5]);
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseScrolled(double mx, double my, double amount, int sw, int sh) {
      if (this.data == null) {
         return false;
      } else {
         int[] g = this.geo(this.data.tabs().size(), sw, sh);
         if (mx >= g[0] && mx <= g[0] + 420 && my >= g[1] && my <= g[1] + g[2]) {
            this.scrollOffset = Math.clamp((long)(this.scrollOffset - (int)(amount * 20.0)), 0, g[5]);
         }

         return true;
      }
   }

   public void mouseReleased() {
      this.draggingScroll = false;
   }

   private String clip(String s, int maxW) {
      return this.font.width(s) <= maxW ? s : this.font.plainSubstrByWidth(s, maxW - this.font.width("...")) + "...";
   }
}
