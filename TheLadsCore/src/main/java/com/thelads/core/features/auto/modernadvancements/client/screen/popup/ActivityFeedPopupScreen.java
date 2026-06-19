package com.thelads.core.features.auto.modernadvancements.client.screen.popup;

import java.util.List;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.data.api.FeedEvent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class ActivityFeedPopupScreen {
   private static final int PANEL_W = 380;
   private static final int TITLE_H = 24;
   private static final int ROW_H = 30;
   private static final int SCROLLBAR_W = 8;
   private static final int PAD = 8;
   private static final int ADV_TOP = 70;
   public boolean visible = false;
   private final Font font;
   private int scrollOffset = 0;
   private boolean draggingScroll = false;

   public ActivityFeedPopupScreen(Font font) {
      this.font = font;
   }

   private int[] geo(int count, int sw, int sh) {
      int px = (sw - 380) / 2;
      int py = 80;
      int contentH = sh - 70 - 60;
      int maxRows = Math.max(1, (contentH - 24 - 16) / 30);
      int ph = 24 + Math.clamp(count == 0 ? 1L : count, 1, maxRows) * 30 + 16;
      int ly = py + 24 + 8;
      int visH = ph - 24 - 16;
      int maxScroll = Math.max(0, count * 30 - visH);
      return new int[]{px, py, ph, ly, visH, maxScroll};
   }

   public void render(GuiGraphicsExtractor ctx, int mx, int my, int sw, int sh) {
      List<FeedEvent> feed = ModernAdvancementsClient.serverFeed;
      int[] g = this.geo(feed.size(), sw, sh);
      int px = g[0];
      int py = g[1];
      int ph = g[2];
      int ly = g[3];
      int visH = g[4];
      int maxScroll = g[5];
      this.scrollOffset = Math.clamp((long)this.scrollOffset, 0, maxScroll);
      ctx.fill(px, py, px + 380, py + ph, -267316975);
      ctx.outline(px, py, 380, ph, -8355712);
      ctx.centeredText(this.font, Component.literal("Recent Activity"), px + 190, py + 6, -1);
      ctx.fill(px + 8, py + 24 - 2, px + 380 - 8, py + 24 - 1, -11513776);
      ctx.enableScissor(px, ly, px + 380, ly + visH);
      if (feed.isEmpty()) {
         ctx.centeredText(this.font, Component.literal("No recent activity"), px + 190, ly + 4, -7829368);
         ctx.disableScissor();
      } else {
         int sbX = px + 380 - 8 - 8;
         int ry = ly - this.scrollOffset;

         for (FeedEvent e : feed) {
            int textX = px + 8 + 4;
            int line1Y = ry + 4;
            int line2Y = ry + 4 + 9 + 2;
            int maxW = sbX - textX - 56;
            String time = timeAgo(e.timestamp());
            String nameChunk = this.clip(e.playerName(), 70);
            ctx.text(this.font, nameChunk, textX, line1Y, -13227, false);
            int afterName = textX + this.font.width(nameChunk);
            ctx.text(this.font, " earned ", afterName, line1Y, -7829368, false);
            int afterEarned = afterName + this.font.width(" earned ");
            ctx.text(
               this.font, this.clip(e.advancementTitle(), maxW - this.font.width(nameChunk) - this.font.width(" earned ")), afterEarned, line1Y, -1, false
            );
            if (!e.tabTitle().isEmpty()) {
               ctx.text(this.font, e.tabTitle(), textX, line2Y, -10066279, false);
            }

            ctx.text(this.font, time, sbX - 4 - this.font.width(time), line1Y, -11184811, false);
            ry += 30;
         }

         ctx.disableScissor();
         if (maxScroll > 0) {
            int totalH = Math.max(1, feed.size() * 30);
            int handleH = Math.max(20, visH * visH / totalH);
            int handleY = ly + 2 + (int)((float)(visH - handleH) * this.scrollOffset / maxScroll);
            ctx.fill(sbX, ly + 2, sbX + 8, ly + visH - 2, Integer.MIN_VALUE);
            ctx.fill(sbX, handleY, sbX + 8, handleY + handleH, -5592406);
         }
      }
   }

   public boolean mouseClicked(double mx, double my, int sw, int sh) {
      int[] g = this.geo(ModernAdvancementsClient.serverFeed.size(), sw, sh);
      if (!(mx < g[0]) && !(mx > g[0] + 380) && !(my < g[1]) && !(my > g[1] + g[2])) {
         int sbX = g[0] + 380 - 8 - 8;
         if (mx >= sbX && mx <= sbX + 8) {
            this.draggingScroll = true;
         }

         return true;
      } else {
         this.visible = false;
         return true;
      }
   }

   public boolean mouseDragged(double mx, double my, int sw, int sh) {
      if (!this.draggingScroll) {
         return false;
      } else {
         int[] g = this.geo(ModernAdvancementsClient.serverFeed.size(), sw, sh);
         this.scrollOffset = Math.clamp((long)((int)((float)Math.clamp((my - g[3]) / g[4], 0.0, 1.0) * g[5])), 0, g[5]);
         return true;
      }
   }

   public boolean mouseScrolled(double mx, double my, double amount, int sw, int sh) {
      int[] g = this.geo(ModernAdvancementsClient.serverFeed.size(), sw, sh);
      if (mx >= g[0] && mx <= g[0] + 380 && my >= g[1] && my <= g[1] + g[2]) {
         this.scrollOffset = Math.clamp((long)(this.scrollOffset - (int)(amount * 20.0)), 0, g[5]);
      }

      return true;
   }

   public void mouseReleased() {
      this.draggingScroll = false;
   }

   private String clip(String s, int maxW) {
      return this.font.width(s) <= maxW ? s : this.font.plainSubstrByWidth(s, maxW - this.font.width("...")) + "...";
   }

   private static String timeAgo(long ts) {
      long sec = (System.currentTimeMillis() - ts) / 1000L;
      if (sec < 60L) {
         return "just now";
      } else if (sec < 3600L) {
         return sec / 60L + "m ago";
      } else {
         return sec < 86400L ? sec / 3600L + "h ago" : sec / 86400L + "d ago";
      }
   }
}
