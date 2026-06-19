package com.thelads.core.features.auto.modernadvancements.client.screen.popup;

import java.util.List;
import java.util.UUID;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.data.api.PlayerSummary;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class LeaderboardPopupScreen {
   private static final int PANEL_W = 400;
   private static final int TITLE_H = 24;
   private static final int ROW_H = 24;
   private static final int SCROLLBAR_W = 8;
   private static final int PAD = 8;
   private static final int ADV_TOP = 70;
   public boolean visible = false;
   private final Font font;
   private final LeaderboardPopupScreen.Listener listener;
   private int scrollOffset = 0;
   private boolean draggingScroll = false;

   public LeaderboardPopupScreen(Font font, LeaderboardPopupScreen.Listener listener) {
      this.font = font;
      this.listener = listener;
   }

   private int[] geo(int count, int sw, int sh) {
      int px = (sw - 400) / 2;
      int py = 80;
      int contentH = sh - 70 - 60;
      int maxRows = Math.max(1, (contentH - 24 - 16) / 24);
      int ph = 24 + Math.clamp(count == 0 ? 1L : count, 1, maxRows) * 24 + 16;
      int ly = py + 24 + 8;
      int visH = ph - 24 - 16;
      int maxScroll = Math.max(0, count * 24 - visH);
      return new int[]{px, py, ph, ly, visH, maxScroll};
   }

   public void render(GuiGraphicsExtractor ctx, int mx, int my, int sw, int sh) {
      List<PlayerSummary> players = ModernAdvancementsClient.serverLeaderboard;
      int[] g = this.geo(players.size(), sw, sh);
      int px = g[0];
      int py = g[1];
      int ph = g[2];
      int ly = g[3];
      int visH = g[4];
      int maxScroll = g[5];
      this.scrollOffset = Math.clamp((long)this.scrollOffset, 0, maxScroll);
      ctx.fill(px, py, px + 400, py + ph, -267316975);
      ctx.outline(px, py, 400, ph, -8355712);
      ctx.centeredText(this.font, Component.literal("Server Leaderboard"), px + 200, py + 6, -1);
      ctx.fill(px + 8, py + 24 - 2, px + 400 - 8, py + 24 - 1, -11513776);
      ctx.enableScissor(px, ly, px + 400, ly + visH);
      if (players.isEmpty()) {
         ctx.centeredText(this.font, Component.literal("No players online"), px + 200, ly + 4, -7829368);
         ctx.disableScissor();
      } else {
         int sbX = px + 400 - 8 - 8;
         int ry = ly - this.scrollOffset;

         for (int i = 0; i < players.size(); i++) {
            PlayerSummary p = players.get(i);
            boolean hovered = mx >= px + 8 && mx < sbX - 2 && my >= ry && my < ry + 24;
            if (hovered) {
               ctx.fill(px + 8, ry, sbX - 2, ry + 24 - 1, 822083583);
            }

            int rankColor = i == 0 ? -10496 : (i == 1 ? -4144960 : (i == 2 ? -3309774 : -7829368));
            ctx.text(this.font, "#" + (i + 1), px + 8 + 2, ry + (24 - 9) / 2, rankColor, false);
            int nameX = px + 8 + 24;
            int barX = nameX + 90;
            int barW = sbX - barX - 68;
            ctx.text(this.font, this.clip(p.name(), 85), nameX, ry + (24 - 9) / 2, -3355444, false);
            if (p.total() > 0) {
               int barY = ry + 12 - 3;
               ctx.fill(barX, barY, barX + barW, barY + 6, -13421773);
               int filled = (int)(p.percentage() * barW);
               if (filled > 0) {
                  ctx.fill(barX, barY, barX + filled, barY + 6, barColor(p.percentage()));
               }

               ctx.outline(barX, barY, barW, 6, -11184811);
            }

            String stat = p.completed() + "/" + p.total() + " (" + Math.round(p.percentage() * 100.0F) + "%)";
            ctx.text(this.font, stat, sbX - 4 - this.font.width(stat), ry + (24 - 9) / 2, -6710887, false);
            ry += 24;
         }

         ctx.disableScissor();
         if (maxScroll > 0) {
            this.renderScrollbar(ctx, sbX, ly + 2, visH - 4, players.size());
         }
      }
   }

   private void renderScrollbar(GuiGraphicsExtractor ctx, int x, int y, int h, int count) {
      ctx.fill(x, y, x + 8, y + h, Integer.MIN_VALUE);
      int totalH = Math.max(1, count * 24);
      int handleH = Math.max(20, h * h / totalH);
      int maxScroll = Math.max(1, totalH - h);
      int handleY = y + (int)((float)(h - handleH) * this.scrollOffset / maxScroll);
      ctx.fill(x, handleY, x + 8, handleY + handleH, -5592406);
      ctx.outline(x, handleY, 8, handleH, -3355444);
   }

   public boolean mouseClicked(double mx, double my, int sw, int sh) {
      List<PlayerSummary> players = ModernAdvancementsClient.serverLeaderboard;
      int[] g = this.geo(players.size(), sw, sh);
      int px = g[0];
      int py = g[1];
      int ph = g[2];
      int ly = g[3];
      if (!(mx < px) && !(mx > px + 400) && !(my < py) && !(my > py + ph)) {
         int sbX = px + 400 - 8 - 8;
         if (mx >= sbX && mx <= sbX + 8 && my >= ly && my <= ly + g[4]) {
            this.draggingScroll = true;
            return true;
         } else {
            int ry = ly - this.scrollOffset;

            for (PlayerSummary p : players) {
               if (mx >= px + 8 && mx < sbX - 2 && my >= ry && my < ry + 24) {
                  this.listener.onPlayerSelected(p.uuid(), p.name());
                  return true;
               }

               ry += 24;
            }

            return true;
         }
      } else {
         this.visible = false;
         return true;
      }
   }

   public boolean mouseDragged(double mx, double my, int sw, int sh) {
      if (!this.draggingScroll) {
         return false;
      } else {
         int[] g = this.geo(ModernAdvancementsClient.serverLeaderboard.size(), sw, sh);
         this.scrollOffset = Math.clamp((long)((int)((float)Math.clamp((my - g[3]) / g[4], 0.0, 1.0) * g[5])), 0, g[5]);
         return true;
      }
   }

   public boolean mouseScrolled(double mx, double my, double amount, int sw, int sh) {
      int[] g = this.geo(ModernAdvancementsClient.serverLeaderboard.size(), sw, sh);
      if (mx >= g[0] && mx <= g[0] + 400 && my >= g[1] && my <= g[1] + g[2]) {
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

   private static int barColor(float pct) {
      if (pct >= 1.0F) {
         return -16729344;
      } else if (pct >= 0.75F) {
         return -7816448;
      } else if (pct >= 0.5F) {
         return -4478464;
      } else {
         return pct >= 0.25F ? -4495872 : -4513280;
      }
   }

   public interface Listener {
      void onPlayerSelected(UUID var1, String var2);
   }
}
