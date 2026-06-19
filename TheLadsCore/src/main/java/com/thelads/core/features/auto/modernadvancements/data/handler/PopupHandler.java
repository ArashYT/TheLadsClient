package com.thelads.core.features.auto.modernadvancements.data.handler;

import java.util.List;
import java.util.function.Supplier;
import com.thelads.core.features.auto.modernadvancements.client.screen.ModernAdvancementsScreen;
import com.thelads.core.features.auto.modernadvancements.client.screen.popup.ActivityFeedPopupScreen;
import com.thelads.core.features.auto.modernadvancements.client.screen.popup.ConfigPopupScreen;
import com.thelads.core.features.auto.modernadvancements.client.screen.popup.LeaderboardPopupScreen;
import com.thelads.core.features.auto.modernadvancements.client.screen.popup.PlayerComparisonPopupScreen;
import com.thelads.core.features.auto.modernadvancements.client.screen.popup.ScreenshotPopupScreen;
import com.thelads.core.features.auto.modernadvancements.client.screen.popup.StatsPopupScreen;
import com.thelads.core.features.auto.modernadvancements.client.screen.popup.TrackingPopupScreen;
import com.thelads.core.features.auto.modernadvancements.data.api.TabCompletionStat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class PopupHandler {
   private final Font font;
   public final TrackingPopupScreen tracking;
   public final ConfigPopupScreen config;
   public final StatsPopupScreen stats;
   public final LeaderboardPopupScreen leaderboard;
   public final ActivityFeedPopupScreen feed;
   public final PlayerComparisonPopupScreen comparison;
   public final ScreenshotPopupScreen screenshot;

   public PopupHandler(
      Font font, ModernAdvancementsScreen parent, TrackingPopupScreen.Listener trackingListener, LeaderboardPopupScreen.Listener leaderboardListener
   ) {
      this.font = font;
      this.tracking = new TrackingPopupScreen(font, trackingListener);
      this.config = new ConfigPopupScreen(font, parent);
      this.stats = new StatsPopupScreen(font);
      this.leaderboard = new LeaderboardPopupScreen(font, leaderboardListener);
      this.feed = new ActivityFeedPopupScreen(font);
      this.comparison = new PlayerComparisonPopupScreen(font);
      this.screenshot = new ScreenshotPopupScreen();
   }

   public void toggle(PopupHandler.Type type) {
      boolean wasOpen = this.isOpen(type);
      this.closeAll();
      if (!wasOpen) {
         this.setOpen(type);
      }
   }

   public void closeAll() {
      this.tracking.visible = false;
      this.config.visible = false;
      this.stats.visible = false;
      this.leaderboard.visible = false;
      this.feed.visible = false;
      this.comparison.visible = false;
      this.screenshot.close();
   }

   public boolean anyOpen() {
      return this.tracking.visible
         || this.config.visible
         || this.stats.visible
         || this.leaderboard.visible
         || this.feed.visible
         || this.comparison.visible
         || this.screenshot.visible;
   }

   public boolean mouseClicked(double mx, double my, int w, int h) {
      if (this.screenshot.visible) {
         this.screenshot.mouseClicked();
         return true;
      } else if (this.tracking.visible) {
         this.tracking.mouseClicked(mx, my, w, h);
         return true;
      } else if (this.config.visible) {
         this.config.mouseClicked(mx, my, w);
         return true;
      } else if (this.stats.visible) {
         if (!this.stats.mouseClicked(mx, my)) {
            this.stats.visible = false;
         }

         return true;
      } else if (this.leaderboard.visible) {
         this.leaderboard.mouseClicked(mx, my, w, h);
         return true;
      } else if (this.feed.visible) {
         this.feed.mouseClicked(mx, my, w, h);
         return true;
      } else if (this.comparison.visible) {
         this.comparison.mouseClicked(mx, my, w, h);
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseDragged(double mx, double my, int w, int h) {
      if (this.screenshot.visible) {
         return true;
      } else if (this.tracking.mouseDragged(mx, my, w, h)) {
         return true;
      } else if (this.tracking.visible || this.config.visible) {
         return true;
      } else if (this.stats.visible) {
         this.stats.mouseDragged(my);
         return true;
      } else if (this.leaderboard.mouseDragged(mx, my, w, h)) {
         return true;
      } else {
         return this.feed.mouseDragged(mx, my, w, h) ? true : this.comparison.mouseDragged(my, w, h);
      }
   }

   public boolean mouseScrolled(double mx, double my, double amount, int w, int h) {
      if (this.screenshot.visible) {
         return true;
      } else if (this.tracking.visible) {
         return this.tracking.mouseScrolled(mx, my, amount, w, h);
      } else if (this.config.visible) {
         return true;
      } else if (this.stats.visible) {
         return this.stats.mouseScrolled(mx, amount);
      } else if (this.leaderboard.visible) {
         return this.leaderboard.mouseScrolled(mx, my, amount, w, h);
      } else if (this.feed.visible) {
         return this.feed.mouseScrolled(mx, my, amount, w, h);
      } else {
         return this.comparison.visible ? this.comparison.mouseScrolled(mx, my, amount, w, h) : false;
      }
   }

   public void mouseReleased() {
      this.tracking.mouseReleased();
      this.stats.mouseReleased();
      this.leaderboard.mouseReleased();
      this.feed.mouseReleased();
      this.comparison.mouseReleased();
   }

   public boolean keyPressed(int keyCode) {
      if (keyCode != 256) {
         return false;
      } else if (this.screenshot.visible) {
         this.screenshot.close();
         return true;
      } else if (this.stats.visible) {
         this.stats.visible = false;
         return true;
      } else if (this.tracking.visible) {
         this.tracking.visible = false;
         return true;
      } else if (this.config.visible) {
         this.config.visible = false;
         return true;
      } else if (this.leaderboard.visible) {
         this.leaderboard.visible = false;
         return true;
      } else if (this.feed.visible) {
         this.feed.visible = false;
         return true;
      } else if (this.comparison.visible) {
         this.comparison.close();
         return true;
      } else {
         return false;
      }
   }

   public void renderAll(GuiGraphicsExtractor graphics, int mx, int my, int w, int h, Supplier<List<TabCompletionStat>> statsSupplier, boolean hasServerData) {
      if (this.tracking.visible) {
         graphics.fill(0, 0, w, h, 1610612736);
         this.tracking.render(graphics, mx, my, w, h);
      }

      if (this.config.visible) {
         graphics.fill(0, 0, w, h, 1610612736);
         this.config.render(graphics, mx, my, w);
      }

      if (this.stats.visible) {
         graphics.fill(0, 0, w, h, 1610612736);
         this.stats.render(graphics, w, h, statsSupplier.get(), hasServerData);
      }

      if (this.leaderboard.visible) {
         graphics.fill(0, 0, w, h, 1610612736);
         this.leaderboard.render(graphics, mx, my, w, h);
      }

      if (this.feed.visible) {
         graphics.fill(0, 0, w, h, 1610612736);
         this.feed.render(graphics, mx, my, w, h);
      }

      if (this.comparison.visible) {
         graphics.fill(0, 0, w, h, 1610612736);
         this.comparison.render(graphics, mx, my, w, h);
      }

      if (this.screenshot.visible) {
         graphics.fill(0, 0, w, h, -1879048192);
         this.screenshot.render(graphics, w, h);
      }

      if (!this.screenshot.visible) {
         Component hint = Component.translatable("gui.advancements.text.popup.close_hint");
         int textWidth = this.font.width(hint);
         int textX = w / 2 - textWidth / 2;
         int textY = h - 12;
         int padding = 4;
         graphics.fill(textX - padding, textY - padding, textX + textWidth + padding, textY + 9 + padding, -1879048192);
         graphics.centeredText(this.font, hint, w / 2, textY, -1);
      }
   }

   private boolean isOpen(PopupHandler.Type type) {
      return switch (type) {
         case TRACKING -> this.tracking.visible;
         case CONFIG -> this.config.visible;
         case STATS -> this.stats.visible;
         case LEADERBOARD -> this.leaderboard.visible;
         case FEED -> this.feed.visible;
      };
   }

   private void setOpen(PopupHandler.Type type) {
      switch (type) {
         case TRACKING:
            this.tracking.visible = true;
            break;
         case CONFIG:
            this.config.visible = true;
            break;
         case STATS:
            this.stats.visible = true;
            break;
         case LEADERBOARD:
            this.leaderboard.visible = true;
            break;
         case FEED:
            this.feed.visible = true;
      }
   }

   public static enum Type {
      TRACKING,
      CONFIG,
      STATS,
      LEADERBOARD,
      FEED;
   }
}
