package com.thelads.core.features.auto.modernadvancements.data.handler;

import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.client.screen.ModernAdvancementsScreen;
import com.thelads.core.features.auto.modernadvancements.data.component.ModernAdvancementTab;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class TabBarHandler {
   private static final int TAB_WIDTH = 32;
   private static final int TAB_HEIGHT = 32;
   private static final int TAB_PADDING = 3;
   private static final int TAB_AREA_WIDTH_PERCENT = 75;
   private static final int TAB_SCROLL_HEIGHT = 50;
   private static final int TITLE_HEIGHT = 20;
   private final ModernAdvancementsScreen screen;
   private final Font font;
   private final ClientAdvancements advancementHandler;
   private final TabBarHandler.Listener listener;
   private final Map<AdvancementHolder, ModernAdvancementTab> tabs = Maps.newLinkedHashMap();
   private final Map<AdvancementHolder, ModernAdvancementTab> serverAllTabs = Maps.newLinkedHashMap();
   @Nullable
   private ModernAdvancementTab selectedTab;
   @Nullable
   private ModernAdvancementTab allSearchResultsTab;
   @Nullable
   private ModernAdvancementTab savedSelectedTab;
   private int tabScrollOffset = 0;
   private int maxTabScroll = 0;
   private boolean isDraggingTabBar = false;
   private boolean isViewAllMode = false;
   private int screenWidth;

   public TabBarHandler(
      ModernAdvancementsScreen screen,
      Font font,
      ClientAdvancements advancementHandler,
      int screenWidth,
      TabBarHandler.Listener listener
   ) {
      this.screen = screen;
      this.font = font;
      this.advancementHandler = advancementHandler;
      this.screenWidth = screenWidth;
      this.listener = listener;
   }

   @Nullable
   public ModernAdvancementTab getSelectedTab() {
      return this.selectedTab;
   }

   public boolean isViewAllMode() {
      return !this.isViewAllMode;
   }

   public Map<AdvancementHolder, ModernAdvancementTab> getTabs() {
      return this.tabs;
   }

   public Map<AdvancementHolder, ModernAdvancementTab> getServerAllTabs() {
      return this.serverAllTabs;
   }

   public Map<AdvancementHolder, ModernAdvancementTab> getDisplayTabs() {
      return this.isViewAllMode && !this.serverAllTabs.isEmpty() ? this.serverAllTabs : this.tabs;
   }

   @Nullable
   public ModernAdvancementTab findTab(AdvancementNode node) {
      ModernAdvancementTab tab = this.tabs.get(node.root().holder());
      if (tab == null && this.isViewAllMode) {
         tab = this.serverAllTabs.get(node.root().holder());
      }

      return tab;
   }

   public void setSelectedTab(@Nullable ModernAdvancementTab tab) {
      this.selectedTab = tab;
   }

   public void setAllSearchResultsTab(@Nullable ModernAdvancementTab tab) {
      this.allSearchResultsTab = tab;
      this.calculateTabScroll();
   }

   public void addRoot(AdvancementNode root) {
      ModernAdvancementTab tab = ModernAdvancementTab.create(this.screen, root);
      if (tab != null) {
         this.tabs.put(root.holder(), tab);
         this.calculateTabScroll();
         this.listener.onTabAdded(tab);
      }
   }

   public void removeRoot(AdvancementNode root) {
      this.tabs.remove(root.holder());
      this.calculateTabScroll();
   }

   public void addTask(AdvancementNode dependent) {
      ModernAdvancementTab tab = this.tabs.get(dependent.root().holder());
      if (tab != null) {
         tab.addAdvancement(dependent);
      }
   }

   public void clear() {
      this.tabs.clear();
      this.selectedTab = null;
      this.allSearchResultsTab = null;
   }

   public void onServerTabChanged(@Nullable AdvancementHolder advancement) {
      if (!this.isViewAllMode) {
         ModernAdvancementTab newTab = this.tabs.get(advancement);
         if (newTab != null && newTab != this.selectedTab) {
            this.selectedTab = newTab;
            this.listener.onTabSelected(newTab);
         }
      }
   }

   public void buildServerAllTabs(@Nullable AdvancementTree tree) {
      this.serverAllTabs.clear();
      if (tree != null) {
         for (AdvancementNode root : tree.roots()) {
            ModernAdvancementTab tab = ModernAdvancementTab.create(this.screen, root);
            if (tab != null) {
               this.serverAllTabs.put(root.holder(), tab);
            }
         }

         for (AdvancementNode node : tree.nodes()) {
            if (node.parent() != null) {
               ModernAdvancementTab tab = this.serverAllTabs.get(node.root().holder());
               if (tab != null) {
                  tab.addAdvancement(node);
               }
            }
         }
      }
   }

   public void enterViewAll() {
      this.savedSelectedTab = this.selectedTab;
      this.isViewAllMode = true;
      if (!this.serverAllTabs.isEmpty()) {
         this.selectedTab = this.serverAllTabs.values().iterator().next();
      }

      this.calculateTabScroll();
   }

   public void exitViewAll() {
      this.isViewAllMode = false;
      this.selectedTab = this.savedSelectedTab;
      if (this.selectedTab != null && this.selectedTab.getRoot() != null) {
         this.advancementHandler.setSelectedTab(this.selectedTab.getRoot().holder(), true);
      }

      this.calculateTabScroll();
   }

   public void selectFirstTab() {
      if (!this.tabs.isEmpty()) {
         ModernAdvancementTab first = this.tabs.values().iterator().next();
         this.advancementHandler.setSelectedTab(first.getRoot() != null ? first.getRoot().holder() : null, true);
      }
   }

   public void notifyHandlerOfSelected() {
      if (this.selectedTab != null && this.selectedTab.getRoot() != null && !this.isViewAllMode) {
         this.advancementHandler.setSelectedTab(this.selectedTab.getRoot().holder(), true);
      }
   }

   public void calculateTabScroll() {
      int tabAreaWidth = this.screenWidth * 75 / 100;
      int totalTabWidth = this.getDisplayTabs().size() * 35;
      if (this.allSearchResultsTab != null) {
         totalTabWidth += 35;
      }

      this.maxTabScroll = Math.max(0, totalTabWidth - (tabAreaWidth - 70));
      this.tabScrollOffset = Math.min(this.tabScrollOffset, this.maxTabScroll);
   }

   public boolean mouseClicked(double mouseX, double mouseY, boolean popupsOpen) {
      int tabAreaWidth = this.screenWidth * 75 / 100;
      int tabBarY = 20;
      int tabY = tabBarY + 9;
      if (!(mouseY < tabBarY) && !(mouseY > tabBarY + 50) && !(mouseX >= tabAreaWidth)) {
         this.isDraggingTabBar = true;
         if (mouseY >= tabY && mouseY <= tabY + 32) {
            double x = 30 - this.tabScrollOffset;
            if (this.allSearchResultsTab != null) {
               if (mouseX >= x && mouseX <= x + 32.0) {
                  this.selectedTab = this.allSearchResultsTab;
                  this.listener.onTabSelected(this.allSearchResultsTab);
                  return true;
               }

               x += 35.0;
            }

            for (ModernAdvancementTab tab : this.getDisplayTabs().values()) {
               if (mouseX >= x && mouseX <= x + 32.0) {
                  this.selectedTab = tab;
                  if (!this.isViewAllMode && tab.getRoot() != null) {
                     this.advancementHandler.setSelectedTab(tab.getRoot().holder(), true);
                  }

                  this.listener.onTabSelected(tab);
                  return true;
               }

               x += 35.0;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean mouseDragged(double offsetX) {
      if (!this.isDraggingTabBar) {
         return false;
      } else {
         this.tabScrollOffset = Math.clamp((long)(this.tabScrollOffset - (int)offsetX), 0, this.maxTabScroll);
         return true;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      int tabAreaWidth = this.screenWidth * 75 / 100;
      if (mouseY >= 20.0 && mouseY <= 70.0 && mouseX < tabAreaWidth && this.maxTabScroll > 0) {
         this.tabScrollOffset = Math.clamp((long)(this.tabScrollOffset - (int)(amount * 20.0)), 0, this.maxTabScroll);
         return true;
      } else {
         return false;
      }
   }

   public void mouseReleased() {
      this.isDraggingTabBar = false;
   }

   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean popupsOpen) {
      int tabAreaWidth = this.screenWidth * 75 / 100;
      int tabBarY = 20;
      context.fill(0, tabBarY, this.screenWidth, tabBarY + 50, Integer.MIN_VALUE);
      context.outline(0, tabBarY, this.screenWidth, 50, -12566464);
      context.fill(tabAreaWidth, tabBarY, tabAreaWidth + 1, tabBarY + 50, -12566464);
      int startX = 30;
      int x = startX - this.tabScrollOffset;
      int y = tabBarY + 9;
      context.enableScissor(startX, tabBarY, tabAreaWidth - 5, tabBarY + 50);
      if (this.allSearchResultsTab != null) {
         boolean isSelected = this.selectedTab == this.allSearchResultsTab;
         boolean isHovered = !popupsOpen && mouseX >= x && mouseX <= x + 32 && mouseY >= y && mouseY <= y + 32;
         this.renderTabButton(context, this.allSearchResultsTab, x, y, isSelected, isHovered, mouseX, mouseY, startX, tabAreaWidth);
         x += 35;
      }

      for (ModernAdvancementTab tab : this.getDisplayTabs().values()) {
         boolean isSelected = tab == this.selectedTab;
         boolean isHovered = !popupsOpen && mouseX >= x && mouseX <= x + 32 && mouseY >= y && mouseY <= y + 32;
         this.renderTabButton(context, tab, x, y, isSelected, isHovered, mouseX, mouseY, startX, tabAreaWidth);
         x += 35;
      }

      context.disableScissor();
      if (this.maxTabScroll > 0) {
         int indicatorY = tabBarY + 25;
         if (this.tabScrollOffset > 0) {
            context.fill(5, indicatorY - 8, 20, indicatorY + 8, -805306368);
            context.centeredText(this.font, "◀", 12, indicatorY - 4, -1);
         }

         if (this.tabScrollOffset < this.maxTabScroll) {
            int arrowX = tabAreaWidth - 35;
            context.fill(arrowX, indicatorY - 8, arrowX + 15, indicatorY + 8, -805306368);
            context.centeredText(this.font, "▶", arrowX + 7, indicatorY - 4, -1);
         }
      }
   }

   private void renderTabButton(
      GuiGraphicsExtractor context,
      ModernAdvancementTab tab,
      int x,
      int y,
      boolean isSelected,
      boolean isHovered,
      int mouseX,
      int mouseY,
      int startX,
      int tabAreaWidth
   ) {
      context.fill(x, y, x + 32, y + 32, isSelected ? -10855846 : (isHovered ? -12961222 : -14013910));
      context.outline(x, y, 32, 32, isSelected ? -1 : (isHovered ? -8355712 : -12566464));
      tab.drawIcon(context, x, y, 32, 32);
      boolean hasServer = ModernAdvancementsClient.hasServerAdvancementData();
      if (!tab.isSearchTab() && hasServer) {
         int[] cs = tab.getCompletionStats(this.screen);
         int barY = y + 32 + 2;
         context.fill(x, barY, x + 32, barY + 3, -14540254);
         if (cs[1] > 0) {
            int filled = cs[0] >= cs[1] ? 32 : Math.max(1, (int)((float)cs[0] / cs[1] * 32.0F));
            context.fill(x, barY, x + filled, barY + 3, cs[0] >= cs[1] ? -16729344 : -14527011);
         }
      }

      if (isHovered && x >= startX && x + 32 <= tabAreaWidth - 40) {
         if (!tab.isSearchTab() && hasServer) {
            int[] cs = tab.getCompletionStats(this.screen);
            List<Component> lines = List.of(
               tab.getTitle(), Component.literal(cs[0] + " / " + cs[1]).withStyle(s -> s.withColor(cs[0] >= cs[1] ? '\udd00' : 11184810))
            );
            context.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
         } else {
            context.setTooltipForNextFrame(this.font, tab.getTitle(), mouseX, mouseY);
         }
      }
   }

   public interface Listener {
      void onTabSelected(ModernAdvancementTab var1);

      void onTabAdded(ModernAdvancementTab var1);
   }
}
