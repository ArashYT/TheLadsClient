package com.thelads.core.features.auto.modernadvancements.client.screen.popup;

import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.client.screen.HudEditScreen;
import com.thelads.core.features.auto.modernadvancements.client.screen.ModernAdvancementsScreen;
import com.thelads.core.features.auto.modernadvancements.client.screen.ToastEditScreen;
import com.thelads.core.features.auto.modernadvancements.data.layout.LayoutMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public class ConfigPopupScreen {
   private static final int SETTINGS_PANEL_MIN_WIDTH = 260;
   private static final int SETTINGS_PANEL_HEIGHT = 200;
   private static final int ADVANCEMENT_AREA_TOP = 80;
   private static final int PADDING = 8;
   public boolean visible = false;
   private final Font font;
   private final ModernAdvancementsScreen parent;

   public ConfigPopupScreen(Font font, ModernAdvancementsScreen parent) {
      this.font = font;
      this.parent = parent;
   }

   private int getPanelWidth() {
      int textPad = 26;
      String[] toggleKeys = new String[]{
         "gui.advancements.config.hide_hud",
         "gui.advancements.config.auto_remove",
         "gui.advancements.config.auto_track",
         "gui.advancements.config.take_screenshots",
         "gui.advancements.config.match_screen"
      };
      int max = 260;

      for (String key : toggleKeys) {
         int w = this.font.width(Component.translatable(key, new Object[]{"☑"})) + textPad;
         max = Math.max(max, w);
      }

      String cycleValue = "< " + this.layoutModeLabel(ModernAdvancementsClient.CONFIG.layoutMode()) + " >";
      int cycleW = this.font.width(Component.translatable("gui.advancements.config.layout_mode"))
         + this.font.width(Component.literal(cycleValue))
         + 12
         + textPad;
      max = Math.max(max, cycleW);
      max = Math.max(max, this.font.width(Component.translatable("gui.advancements.text.tracker.edit")) + textPad);
      max = Math.max(max, this.font.width(Component.translatable("gui.advancements.text.toast.edit")) + textPad);
      return Math.max(max, this.font.width(Component.translatable("gui.advancements.config.title")) + textPad);
   }

   private void playClickSound() {
      AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
   }

   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, int screenWidth) {
      int panelWidth = this.getPanelWidth();
      int panelHeight = 200;
      int panelX = (screenWidth - panelWidth) / 2;
      int panelY = 80;
      context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, -267316975);
      context.outline(panelX, panelY, panelWidth, panelHeight, -8355712);
      context.centeredText(this.font, Component.translatable("gui.advancements.config.title"), panelX + panelWidth / 2, panelY + 6, -1);
      context.fill(panelX + 6, panelY + 20, panelX + panelWidth - 6, panelY + 21, -11513776);
      int rowY = panelY + 26;
      int rowH = 18;
      int padding = 8;
      this.renderToggle(
         context,
         mouseX,
         mouseY,
         panelX + padding,
         rowY,
         panelWidth - padding * 2,
         "gui.advancements.config.hide_hud",
         ModernAdvancementsClient.CONFIG.hideTracker()
      );
      rowY += rowH;
      this.renderToggle(
         context,
         mouseX,
         mouseY,
         panelX + padding,
         rowY,
         panelWidth - padding * 2,
         "gui.advancements.config.auto_remove",
         ModernAdvancementsClient.CONFIG.removeCompletedTracking()
      );
      rowY += rowH;
      this.renderToggle(
         context,
         mouseX,
         mouseY,
         panelX + padding,
         rowY,
         panelWidth - padding * 2,
         "gui.advancements.config.auto_track",
         ModernAdvancementsClient.CONFIG.trackPathways()
      );
      rowY += rowH;
      this.renderToggle(
         context,
         mouseX,
         mouseY,
         panelX + padding,
         rowY,
         panelWidth - padding * 2,
         "gui.advancements.config.take_screenshots",
         ModernAdvancementsClient.CONFIG.takeScreenshots()
      );
      rowY += rowH;
      this.renderToggle(
         context,
         mouseX,
         mouseY,
         panelX + padding,
         rowY,
         panelWidth - padding * 2,
         "gui.advancements.config.match_screen",
         ModernAdvancementsClient.CONFIG.matchWindowSize()
      );
      rowY += rowH;
      this.renderDropdownOption(
         context, mouseX, mouseY, panelX + padding, rowY, panelWidth - padding * 2, this.layoutModeLabel(ModernAdvancementsClient.CONFIG.layoutMode())
      );
      rowY += rowH + 6;
      boolean editHovered = mouseX >= panelX + padding && mouseX <= panelX + panelWidth - padding && mouseY >= rowY && mouseY <= rowY + 16;
      context.fill(panelX + padding, rowY, panelX + panelWidth - padding, rowY + 16, editHovered ? -13417387 : -14540237);
      context.outline(panelX + padding, rowY, panelWidth - padding * 2, 16, editHovered ? -10057558 : -12566443);
      context.centeredText(this.font, Component.translatable("gui.advancements.text.tracker.edit"), panelX + panelWidth / 2, rowY + 4, -7811841);
      boolean toastHovered = mouseX >= panelX + padding && mouseX <= panelX + panelWidth - padding && mouseY >= rowY + 22 && mouseY <= rowY + 38;
      context.fill(panelX + padding, rowY + 22, panelX + panelWidth - padding, rowY + 38, toastHovered ? -13417387 : -14540237);
      context.outline(panelX + padding, rowY + 22, panelWidth - padding * 2, 16, toastHovered ? -10057558 : -12566443);
      context.centeredText(this.font, Component.translatable("gui.advancements.text.toast.edit"), panelX + panelWidth / 2, rowY + 26, -7811841);
   }

   private void renderToggle(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width, String label, boolean value) {
      boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 14;
      if (hovered) {
         context.fill(x, y, x + width, y + 14, 822083583);
      }

      context.text(this.font, Component.translatable(label, new Object[]{value ? "☑" : "☐"}), x + 2, y + 3, value ? -7798904 : -3355444, false);
   }

   private void renderDropdownOption(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width, String valueText) {
      boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 14;
      if (hovered) {
         context.fill(x, y, x + width, y + 14, 822083583);
      }

      Component label = Component.translatable("gui.advancements.config.layout_mode");
      Component value = Component.literal("‹ " + valueText + " ›");
      context.text(this.font, label, x + 2, y + 3, -3355444, false);
      int valueX = x + width - this.font.width(value) - 2;
      context.text(this.font, value, valueX, y + 3, hovered ? -8892 : -5592508, false);
   }

   private String layoutModeLabel(LayoutMode mode) {
      return switch (mode) {
         case MODERN -> "Modern";
         case VANILLA -> "Vanilla";
         case CUSTOM -> "Custom";
      };
   }

   private LayoutMode nextLayoutMode(LayoutMode current) {
      LayoutMode[] values = LayoutMode.values();
      return values[(current.ordinal() + 1) % values.length];
   }

   public boolean mouseClicked(double mouseX, double mouseY, int screenWidth) {
      int panelWidth = this.getPanelWidth();
      int panelX = (screenWidth - panelWidth) / 2;
      int panelY = 80;
      if (!(mouseX < panelX) && !(mouseX > panelX + panelWidth) && !(mouseY < panelY) && !(mouseY > panelY + 200)) {
         int rowY = panelY + 26;
         int rowH = 18;
         int padding = 8;
         if (mouseX >= panelX + padding && mouseX <= panelX + panelWidth - padding && mouseY >= rowY && mouseY <= rowY + 14) {
            this.playClickSound();
            ModernAdvancementsClient.CONFIG.hideTracker(!ModernAdvancementsClient.CONFIG.hideTracker());
            ModernAdvancementsClient.CONFIG.save();
            return true;
         } else {
            rowY += rowH;
            if (mouseX >= panelX + padding && mouseX <= panelX + panelWidth - padding && mouseY >= rowY && mouseY <= rowY + 14) {
               this.playClickSound();
               ModernAdvancementsClient.CONFIG.removeCompletedTracking(!ModernAdvancementsClient.CONFIG.removeCompletedTracking());
               ModernAdvancementsClient.CONFIG.save();
               return true;
            } else {
               rowY += rowH;
               if (mouseX >= panelX + padding && mouseX <= panelX + panelWidth - padding && mouseY >= rowY && mouseY <= rowY + 14) {
                  this.playClickSound();
                  ModernAdvancementsClient.CONFIG.trackPathways(!ModernAdvancementsClient.CONFIG.trackPathways());
                  ModernAdvancementsClient.CONFIG.save();
                  return true;
               } else {
                  rowY += rowH;
                  if (mouseX >= panelX + padding && mouseX <= panelX + panelWidth - padding && mouseY >= rowY && mouseY <= rowY + 14) {
                     this.playClickSound();
                     ModernAdvancementsClient.CONFIG.takeScreenshots(!ModernAdvancementsClient.CONFIG.takeScreenshots());
                     ModernAdvancementsClient.CONFIG.save();
                     return true;
                  } else {
                     rowY += rowH;
                     if (mouseX >= panelX + padding && mouseX <= panelX + panelWidth - padding && mouseY >= rowY && mouseY <= rowY + 14) {
                        this.playClickSound();
                        ModernAdvancementsClient.CONFIG.matchWindowSize(!ModernAdvancementsClient.CONFIG.matchWindowSize());
                        ModernAdvancementsClient.CONFIG.save();
                        return true;
                     } else {
                        rowY += rowH;
                        if (mouseX >= panelX + padding && mouseX <= panelX + panelWidth - padding && mouseY >= rowY && mouseY <= rowY + 14) {
                           this.playClickSound();
                           LayoutMode next = this.nextLayoutMode(ModernAdvancementsClient.CONFIG.layoutMode());
                           ModernAdvancementsClient.CONFIG.layoutMode(next);
                           ModernAdvancementsClient.CONFIG.save();
                           this.parent.rebuildAllLayouts();
                           this.parent.syncEditButtonVisibility();
                           return true;
                        } else {
                           rowY += rowH + 6;
                           if (mouseX >= panelX + padding && mouseX <= panelX + panelWidth - padding && mouseY >= rowY && mouseY <= rowY + 16) {
                              this.playClickSound();
                              this.visible = false;
                              Minecraft.getInstance().setScreenAndShow(new HudEditScreen(this.parent));
                              return true;
                           } else if (mouseX >= panelX + padding && mouseX <= panelX + panelWidth - padding && mouseY >= rowY + 22 && mouseY <= rowY + 38) {
                              this.playClickSound();
                              this.visible = false;
                              Minecraft.getInstance().setScreenAndShow(new ToastEditScreen(this.parent));
                              return true;
                           } else {
                              return true;
                           }
                        }
                     }
                  }
               }
            }
         }
      } else {
         this.visible = false;
         return true;
      }
   }
}
