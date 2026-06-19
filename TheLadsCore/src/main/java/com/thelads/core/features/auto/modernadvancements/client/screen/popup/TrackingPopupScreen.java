package com.thelads.core.features.auto.modernadvancements.client.screen.popup;

import java.util.ArrayList;
import java.util.List;
import com.thelads.core.features.auto.modernadvancements.data.tracker.TrackingManager;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class TrackingPopupScreen {
   private static final int ADVANCEMENT_AREA_TOP = 70;
   private static final int TRACKED_PANEL_WIDTH = 320;
   private static final int TRACKED_ROW_HEIGHT = 26;
   private static final int TRACKED_TITLE_HEIGHT = 22;
   private static final int TRACKED_PADDING = 6;
   private static final int SCROLLBAR_WIDTH = 8;
   public boolean visible = false;
   private final Font font;
   private final TrackingPopupScreen.Listener listener;
   private int scrollOffset = 0;
   private boolean isDraggingScroll = false;

   public TrackingPopupScreen(Font font, TrackingPopupScreen.Listener listener) {
      this.font = font;
      this.listener = listener;
   }

   private int[] getGeometry(int count, int screenWidth, int screenHeight) {
      int panelX = (screenWidth - 320) / 2;
      int panelY = 80;
      int contentAreaHeight = screenHeight - 70 - 60;
      int maxVisibleRows = Math.max(1, (contentAreaHeight - 22 - 12) / 26);
      int panelHeight = 22 + Math.clamp((long)count, 1, maxVisibleRows) * 26 + 12;
      int listY = panelY + 22 + 6;
      int visibleContentHeight = panelHeight - 22 - 12;
      int totalContentHeight = Math.max(1, count) * 26;
      int maxScroll = Math.max(0, totalContentHeight - visibleContentHeight);
      return new int[]{panelX, panelY, panelHeight, listY, visibleContentHeight, maxScroll};
   }

   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, int screenWidth, int screenHeight) {
      List<Identifier> tracked = TrackingManager.getInstance().getTracked();
      int[] geo = this.getGeometry(tracked.size(), screenWidth, screenHeight);
      int panelX = geo[0];
      int panelY = geo[1];
      int panelHeight = geo[2];
      int listY = geo[3];
      int visibleContentHeight = geo[4];
      int maxScroll = geo[5];
      this.scrollOffset = Math.clamp((long)this.scrollOffset, 0, maxScroll);
      context.fill(panelX, panelY, panelX + 320, panelY + panelHeight, -267316975);
      context.outline(panelX, panelY, 320, panelHeight, -8355712);
      context.centeredText(this.font, Component.translatable("gui.advancements.text.tracked.title"), panelX + 160, panelY + 6, -1);
      context.fill(panelX + 6, panelY + 22 - 2, panelX + 320 - 6, panelY + 22 - 1, -11513776);
      context.enableScissor(panelX, listY, panelX + 320, listY + visibleContentHeight);
      int rowY = listY - this.scrollOffset;
      if (tracked.isEmpty()) {
         context.centeredText(this.font, Component.translatable("gui.advancements.text.no_tracked"), panelX + 160, rowY + 4, -7829368);
         context.disableScissor();
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() == null) {
            context.disableScissor();
         } else {
            for (Identifier id : tracked) {
               AdvancementNode node = mc.getConnection().getAdvancements().getTree().get(id);
               String title = id.toString();
               AdvancementProgress progress = null;
               boolean completed = false;
               if (node != null && node.advancement().display().isPresent()) {
                  title = ((DisplayInfo)node.advancement().display().get()).getTitle().getString();
                  progress = TrackingManager.getInstance().getProgress(node.holder());
                  completed = progress != null && progress.isDone();
               }

               int scrollbarX = panelX + 320 - 6 - 8;
               int xBtnX = scrollbarX - 4 - 14;
               int xBtnY = rowY + 6 - 1;
               int textRowY = rowY + (26 - 9) / 2;
               int iconY = rowY + 5;
               boolean rowHovered = mouseX >= panelX + 6 && mouseX < xBtnX - 2 && mouseY >= rowY && mouseY < rowY + 26;
               boolean xHovered = mouseX >= xBtnX && mouseX <= xBtnX + 14 && mouseY >= xBtnY && mouseY <= xBtnY + 14;
               if (rowHovered) {
                  context.fill(panelX + 6, rowY, xBtnX - 2, rowY + 26 - 2, 822083583);
               }

               if (node != null && node.advancement().display().isPresent()) {
                  ItemStack iconStack = ((DisplayInfo)node.advancement().display().get()).getIcon().create();
                  if (!iconStack.isEmpty()) {
                     context.item(iconStack, panelX + 6, iconY);
                  }
               }

               int textX = panelX + 6 + 20;
               int maxTitleWidth = xBtnX - 4 - textX - this.font.width("Done") - 4;
               String displayTitle = title;
               if (this.font.width(title) > maxTitleWidth) {
                  displayTitle = this.font.plainSubstrByWidth(title, maxTitleWidth - this.font.width("...")) + "...";
               }

               context.text(this.font, displayTitle, textX, textRowY, completed ? -16711936 : -3355444, false);
               String progressText = completed ? "Done" : (progress != null ? Math.round(progress.getPercent() * 100.0F) + "%" : "?");
               context.text(this.font, progressText, xBtnX - 4 - this.font.width(progressText), textRowY, completed ? -16733696 : -7829368, false);
               context.fill(xBtnX, xBtnY, xBtnX + 14, xBtnY + 14, xHovered ? -6737101 : -10079437);
               context.outline(xBtnX, xBtnY, 14, 14, -5618620);
               context.centeredText(this.font, "X", xBtnX + 7, xBtnY + 3, -21846);
               rowY += 26;
            }

            context.disableScissor();
            if (maxScroll > 0) {
               this.renderScrollbar(context, panelX + 320 - 6 - 8, listY + 2, visibleContentHeight - 4, this.scrollOffset, maxScroll);
            }
         }
      }
   }

   private void renderScrollbar(GuiGraphicsExtractor context, int x, int y, int height, int scrollOffset, int maxScroll) {
      context.fill(x, y, x + 8, y + height, Integer.MIN_VALUE);
      int handleHeight = Math.max(20, (int)(height * ((float)height / (height + maxScroll))));
      int handleY = y + (int)((height - handleHeight) * ((float)scrollOffset / maxScroll));
      context.fill(x, handleY, x + 8, handleY + handleHeight, -5592406);
      context.outline(x, handleY, 8, handleHeight, -3355444);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int screenWidth, int screenHeight) {
      List<Identifier> tracked = new ArrayList<>(TrackingManager.getInstance().getTracked());
      int[] geo = this.getGeometry(tracked.size(), screenWidth, screenHeight);
      int panelX = geo[0];
      int panelY = geo[1];
      int panelHeight = geo[2];
      int listY = geo[3];
      if (!(mouseX < panelX) && !(mouseX > panelX + 320) && !(mouseY < panelY) && !(mouseY > panelY + panelHeight)) {
         int scrollbarX = panelX + 320 - 6 - 8;
         if (mouseX >= scrollbarX && mouseX <= scrollbarX + 8 && mouseY >= listY && mouseY <= listY + geo[4]) {
            this.isDraggingScroll = true;
            return true;
         } else {
            int rowY = listY - this.scrollOffset;
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) {
               return true;
            } else {
               for (Identifier id : tracked) {
                  int xBtnX = scrollbarX - 4 - 14;
                  int xBtnY = rowY + 6 - 1;
                  if (mouseX >= xBtnX && mouseX <= xBtnX + 14 && mouseY >= xBtnY && mouseY <= xBtnY + 14) {
                     TrackingManager.getInstance().untrack(id);
                     this.listener.onUntracked(id);
                     return true;
                  }

                  if (mouseX >= panelX + 6 && mouseX < xBtnX - 2 && mouseY >= rowY && mouseY < rowY + 26) {
                     AdvancementNode node = mc.getConnection().getAdvancements().getTree().get(id);
                     if (node != null) {
                        this.listener.onRowClicked(node);
                     }

                     this.visible = false;
                     return true;
                  }

                  rowY += 26;
               }

               return true;
            }
         }
      } else {
         this.visible = false;
         return true;
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int screenWidth, int screenHeight) {
      if (this.isDraggingScroll) {
         int[] geo = this.getGeometry(TrackingManager.getInstance().getTracked().size(), screenWidth, screenHeight);
         float pct = (float)Math.clamp((mouseY - geo[3]) / geo[4], 0.0, 1.0);
         this.scrollOffset = Math.clamp((long)((int)(pct * geo[5])), 0, geo[5]);
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount, int screenWidth, int screenHeight) {
      int scrollAmount = (int)(verticalAmount * 20.0);
      int[] geo = this.getGeometry(TrackingManager.getInstance().getTracked().size(), screenWidth, screenHeight);
      if (mouseX >= geo[0] && mouseX <= geo[0] + 320 && mouseY >= geo[1] && mouseY <= geo[1] + geo[2]) {
         this.scrollOffset = Math.clamp((long)(this.scrollOffset - scrollAmount), 0, geo[5]);
      }

      return true;
   }

   public void mouseReleased() {
      this.isDraggingScroll = false;
   }

   public interface Listener {
      void onRowClicked(AdvancementNode var1);

      void onUntracked(Identifier var1);
   }
}
