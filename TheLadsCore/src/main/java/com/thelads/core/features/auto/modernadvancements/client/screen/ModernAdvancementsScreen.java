package com.thelads.core.features.auto.modernadvancements.client.screen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.client.screen.popup.DetailPanelPopUpScreen;
import com.thelads.core.features.auto.modernadvancements.client.screen.popup.TrackingPopupScreen;
import com.thelads.core.features.auto.modernadvancements.data.SessionSnapshot;
import com.thelads.core.features.auto.modernadvancements.data.api.TabCompletionStat;
import com.thelads.core.features.auto.modernadvancements.data.component.IconButton;
import com.thelads.core.features.auto.modernadvancements.data.component.ModernAdvancementTab;
import com.thelads.core.features.auto.modernadvancements.data.handler.PopupHandler;
import com.thelads.core.features.auto.modernadvancements.data.handler.SearchHandler;
import com.thelads.core.features.auto.modernadvancements.data.handler.TabBarHandler;
import com.thelads.core.features.auto.modernadvancements.data.handler.ViewportHandler;
import com.thelads.core.features.auto.modernadvancements.data.layout.LayoutEditSession;
import com.thelads.core.features.auto.modernadvancements.data.layout.LayoutMode;
import com.thelads.core.features.auto.modernadvancements.data.layout.TabLayoutOverride;
import com.thelads.core.features.auto.modernadvancements.data.layout.TabLayoutOverrideManager;
import com.thelads.core.features.auto.modernadvancements.data.search.SearchQuery;
import com.thelads.core.features.auto.modernadvancements.data.tracker.TrackingManager;
import com.thelads.core.features.auto.modernadvancements.network.ModernAdvancementsPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientAdvancements.Listener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public class ModernAdvancementsScreen extends Screen implements Listener {
   private static final int TITLE_HEIGHT = 20;
   private static final int TAB_SCROLL_HEIGHT = 50;
   private static final int ADVANCEMENT_AREA_TOP = 70;
   private static final int TRACK_BUTTON_HEIGHT = 20;
   private static final int ICON_BTN_SIZE = 20;
   private static final int ICON_BTN_SPACING = 4;
   @Nullable
   private static SessionSnapshot lastSession = null;
   @Nullable
   private final Screen parent;
   private final ClientAdvancements advancementHandler;
   private final Map<AdvancementHolder, AdvancementProgress> progressMap = new LinkedHashMap<>();
   private final ViewportHandler viewport = new ViewportHandler();
   private TabBarHandler tabBar;
   private SearchHandler search;
   private PopupHandler popups;
   private DetailPanelPopUpScreen detailPanel;
   private Button trackButton;
   private Button closeDetailButton;
   private IconButton viewAllButton;
   private IconButton editButton;
   private IconButton recenterButton;
   private Button doneButton;
   private IconButton configButton;
   private IconButton statsButton;
   private IconButton trackedButton;
   private IconButton leaderboardButton;
   private IconButton feedButton;
   private Button editSaveButton;
   private Button editCancelButton;
   private Button editResetButton;
   private Button editDefaultButton;
   private boolean isEditMode = false;
   private final Map<AdvancementHolder, LayoutEditSession> editSessions = new LinkedHashMap<>();
   @Nullable
   private Identifier currentEditTabId = null;

   public ModernAdvancementsScreen(ClientAdvancements advancementHandler, @Nullable Screen parent) {
      super(Component.translatable("gui.advancements"));
      this.advancementHandler = advancementHandler;
      this.parent = parent;
   }

   private void navigateTo(AdvancementNode node) {
      ModernAdvancementTab tab = this.tabBar.findTab(node);
      if (tab != null) {
         this.viewport.saveScrollForTab(this.tabBar.getSelectedTab());
         this.tabBar.setSelectedTab(tab);
         if (this.tabBar.isViewAllMode()) {
            this.advancementHandler.setSelectedTab(node.root().holder(), true);
         }

         this.viewport.centerOnNode(node, tab, this.width, this.height, this.isDetailOpen());
      }
   }

   private void applyCurrentSearchToTab(ModernAdvancementTab tab) {
      if (this.search != null && !this.search.getCurrentQuery().isEmpty()) {
         SearchQuery sq = SearchQuery.parse(this.search.getCurrentQuery());
         tab.applySearchFilter(sq.term(), sq.onlyIncomplete(), sq.onlyComplete(), this);
      }
   }

   private void playClickSound() {
      AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
   }

   private void centerOnSelected() {
      if (this.isEditMode) {
         LayoutEditSession session = this.currentEditSession();
         if (session != null) {
            this.viewport.centerOnRoot(session.getTab(), this.width, this.height, false);
         }
      } else {
         ModernAdvancementTab selected = this.tabBar.getSelectedTab();
         if (selected != null) {
            AdvancementNode node = this.detailPanel.getSelected();
            if (node != null && selected.getPositionOf(node.holder()) != null) {
               this.viewport.centerOnNode(node, selected, this.width, this.height, this.isDetailOpen());
            } else {
               this.viewport.centerOnRoot(selected, this.width, this.height, this.isDetailOpen());
            }
         }
      }
   }

   public void rebuildAllLayouts() {
      LayoutMode mode = ModernAdvancementsClient.CONFIG.layoutMode();

      for (ModernAdvancementTab tab : this.tabBar.getTabs().values()) {
         tab.rebuildLayout(mode);
      }

      for (ModernAdvancementTab tab : this.tabBar.getServerAllTabs().values()) {
         tab.rebuildLayout(mode);
      }

      ModernAdvancementTab selected = this.tabBar.getSelectedTab();
      if (selected != null) {
         this.viewport.centerOnRoot(selected, this.width, this.height, this.isDetailOpen());
      }
   }

   private boolean isDetailOpen() {
      return this.detailPanel.visible && this.detailPanel.getSelected() != null;
   }

   private void syncDetailButtons() {
      boolean show = this.isDetailOpen();
      this.trackButton.active = show;
      if (show) {
         this.trackButton
            .setMessage(
               Component.translatable(
                  TrackingManager.getInstance().isTracked(this.detailPanel.getSelected().holder().id())
                     ? "gui.advancements.text.untrack"
                     : "gui.advancements.text.track"
               )
            );
      }
   }

   private void saveSessionState() {
      AdvancementNode selected = this.detailPanel.getSelected();
      ModernAdvancementTab selectedTab = this.tabBar.getSelectedTab();
      lastSession = new SessionSnapshot(
         selectedTab != null && selectedTab.getRoot() != null ? selectedTab.getRoot().holder().id() : null,
         selected != null ? selected.holder().id() : null,
         this.viewport.getScrollX(),
         this.viewport.getScrollY(),
         this.viewport.getZoom(),
         this.detailPanel.visible && selected != null,
         this.viewport.buildScrollSnapshot(),
         this.search != null ? this.search.getCurrentQuery() : ""
      );
   }

   private void restoreSessionState() {
      if (ModernAdvancementsClient.pendingFocusAdvancementId != null) {
         Identifier focusId = ModernAdvancementsClient.pendingFocusAdvancementId;
         ModernAdvancementsClient.pendingFocusAdvancementId = null;
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() != null) {
            AdvancementNode node = mc.getConnection().getAdvancements().getTree().get(focusId);
            if (node != null) {
               this.detailPanel.open(node);
               this.navigateTo(node);
               this.search.reapplyQuery();
               return;
            }
         }
      }

      if (lastSession != null && lastSession.tabId() != null) {
         this.viewport.restoreScrollEntries(lastSession.tabScrolls(), this.tabBar.getTabs().keySet());
         this.viewport.setZoom(lastSession.zoom());
         ModernAdvancementTab restoredTab = null;

         for (Entry<AdvancementHolder, ModernAdvancementTab> entry : this.tabBar.getTabs().entrySet()) {
            if (entry.getKey().id().equals(lastSession.tabId())) {
               restoredTab = entry.getValue();
               break;
            }
         }

         if (restoredTab != null) {
            this.tabBar.setSelectedTab(restoredTab);
            if (this.tabBar.isViewAllMode() && restoredTab.getRoot() != null) {
               this.advancementHandler.setSelectedTab(restoredTab.getRoot().holder(), true);
            }

            this.viewport.setScroll(lastSession.scrollX(), lastSession.scrollY());
            if (restoredTab.getRoot() != null) {
               this.viewport.putScrollFor(restoredTab.getRoot().holder(), lastSession.scrollX(), lastSession.scrollY());
            }
         }

         if (lastSession.advancementId() != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
               AdvancementNode node = mc.getConnection().getAdvancements().getTree().get(lastSession.advancementId());
               if (node != null) {
                  if (lastSession.detailOpen()) {
                     this.detailPanel.open(node);
                  } else {
                     this.detailPanel.setSelected(node);
                  }
               }
            }
         }

         this.search.reapplyQuery();
      } else {
         this.autoFocusTracked();
         this.search.reapplyQuery();
      }
   }

   private List<TabCompletionStat> buildCompletionStats() {
      boolean hasServer = ModernAdvancementsClient.hasServerAdvancementData();
      Map<AdvancementHolder, ModernAdvancementTab> source = hasServer ? this.tabBar.getServerAllTabs() : this.tabBar.getTabs();
      List<TabCompletionStat> stats = new ArrayList<>();

      for (ModernAdvancementTab tab : source.values()) {
         if (!tab.isSearchTab()) {
            int[] cs = tab.getCompletionStats(this);
            stats.add(new TabCompletionStat(tab.getTitle(), tab.getIconItem(), cs[0], hasServer ? cs[1] : 0));
         }
      }

      return stats;
   }

   @Nullable
   public AdvancementProgress getAdvancementProgressById(Identifier id) {
      for (Entry<AdvancementHolder, AdvancementProgress> entry : this.progressMap.entrySet()) {
         if (entry.getKey().id().equals(id)) {
            return entry.getValue();
         }
      }

      return null;
   }

   @Nullable
   private LayoutEditSession currentEditSession() {
      if (this.currentEditTabId == null) {
         return null;
      } else {
         for (Entry<AdvancementHolder, LayoutEditSession> entry : this.editSessions.entrySet()) {
            if (entry.getKey().id().equals(this.currentEditTabId)) {
               return entry.getValue();
            }
         }

         return null;
      }
   }

   private void enterEditMode() {
      if (ModernAdvancementsClient.hasServerAdvancementData()) {
         if (this.tabBar.getServerAllTabs().isEmpty()) {
            this.tabBar.buildServerAllTabs(ModernAdvancementsClient.getServerAdvancementTree());
         }

         for (ModernAdvancementTab tab : this.tabBar.getServerAllTabs().values()) {
            tab.rebuildLayout(LayoutMode.CUSTOM);
         }

         this.editSessions.clear();

         for (Entry<AdvancementHolder, ModernAdvancementTab> entry : this.tabBar.getServerAllTabs().entrySet()) {
            ModernAdvancementTab tab = entry.getValue();
            if (tab.getRoot() != null) {
               this.editSessions.put(entry.getKey(), new LayoutEditSession(tab, tab.getModernBasePositions()));
            }
         }

         if (!this.editSessions.isEmpty()) {
            if (this.tabBar.isViewAllMode()) {
               this.tabBar.enterViewAll();
            }

            ModernAdvancementTab currentTab = this.tabBar.getSelectedTab();
            this.currentEditTabId = null;
            if (currentTab != null && currentTab.getRoot() != null) {
               Identifier cid = currentTab.getRoot().holder().id();

               for (AdvancementHolder h : this.editSessions.keySet()) {
                  if (h.id().equals(cid)) {
                     this.currentEditTabId = cid;
                     break;
                  }
               }
            }

            if (this.currentEditTabId == null) {
               this.currentEditTabId = this.editSessions.keySet().iterator().next().id();
            }

            this.syncTabBarToCurrentEditTab();
            this.isEditMode = true;
            this.popups.closeAll();
            this.detailPanel.close();
            this.setNormalButtonsVisible(false);
            this.setEditButtonsVisible(true);
            this.search.getField().visible = false;
            this.editButton.visible = true;
            this.editButton.setIcon(Identifier.fromNamespaceAndPath("modern-advancements", "widget/center"));
            this.editButton.setTooltip(Tooltip.create(Component.translatable("gui.advancements.button.recenter")));
            LayoutEditSession session = this.currentEditSession();
            if (session != null) {
               this.viewport.centerOnRoot(session.getTab(), this.width, this.height, false);
            }
         }
      }
   }

   private void exitEditMode(boolean save) {
      if (save) {
         for (LayoutEditSession session : this.editSessions.values()) {
            TabLayoutOverride override = session.buildOverride();
            ModernAdvancementTab tab = session.getTab();
            if (tab.getRoot() != null) {
               TabLayoutOverrideManager.save(tab.getRoot().holder().id(), override);
            }
         }

         if (ModernAdvancementsClient.CONFIG.layoutMode() != LayoutMode.CUSTOM) {
            ModernAdvancementsClient.CONFIG.layoutMode(LayoutMode.CUSTOM);
            ModernAdvancementsClient.CONFIG.save();
         }
      }

      this.editSessions.clear();
      this.currentEditTabId = null;
      this.isEditMode = false;
      if (!this.tabBar.isViewAllMode()) {
         this.tabBar.exitViewAll();
      }

      this.setEditButtonsVisible(false);
      this.setNormalButtonsVisible(true);
      this.search.getField().visible = true;
      this.editButton.setIcon(Identifier.fromNamespaceAndPath("modern-advancements", "widget/edit"));
      this.editButton.setTooltip(Tooltip.create(Component.translatable("gui.advancements.button.edit_layout")));
      this.editButton.visible = ModernAdvancementsClient.CONFIG.layoutMode() == LayoutMode.CUSTOM;
      this.rebuildAllLayouts();
   }

   private void switchEditTab(Identifier newTabId) {
      boolean found = false;

      for (AdvancementHolder h : this.editSessions.keySet()) {
         if (h.id().equals(newTabId)) {
            found = true;
            break;
         }
      }

      if (found) {
         LayoutEditSession outgoing = this.currentEditSession();
         if (outgoing != null) {
            this.viewport.saveScrollForTab(outgoing.getTab());
         }

         this.currentEditTabId = newTabId;
         LayoutEditSession incoming = this.currentEditSession();
         if (incoming != null) {
            ModernAdvancementTab inTab = incoming.getTab();
            if (inTab.getRoot() != null && this.viewport.hasScrollFor(inTab.getRoot().holder())) {
               this.viewport.restoreScrollFor(inTab.getRoot().holder());
            } else {
               this.viewport.centerOnRoot(inTab, this.width, this.height, false);
            }
         }
      }
   }

   private void syncTabBarToCurrentEditTab() {
      if (this.currentEditTabId != null) {
         for (Entry<AdvancementHolder, ModernAdvancementTab> entry : this.tabBar.getServerAllTabs().entrySet()) {
            if (entry.getKey().id().equals(this.currentEditTabId)) {
               this.tabBar.setSelectedTab(entry.getValue());
               break;
            }
         }
      }
   }

   private void setNormalButtonsVisible(boolean visible) {
      if (this.viewAllButton != null) {
         this.viewAllButton.visible = visible;
      }

      if (this.recenterButton != null) {
         this.recenterButton.visible = visible;
      }

      if (this.doneButton != null) {
         this.doneButton.visible = visible;
      }

      if (this.configButton != null) {
         this.configButton.visible = visible;
      }

      if (this.statsButton != null) {
         this.statsButton.visible = visible;
      }

      if (this.trackedButton != null) {
         this.trackedButton.visible = visible;
      }

      if (this.leaderboardButton != null) {
         this.leaderboardButton.visible = visible;
      }

      if (this.feedButton != null) {
         this.feedButton.visible = visible;
      }

      if (this.editButton != null) {
         this.editButton.visible = visible && ModernAdvancementsClient.CONFIG.layoutMode() == LayoutMode.CUSTOM;
      }

      if (!visible) {
         if (this.trackButton != null) {
            this.trackButton.visible = false;
         }

         if (this.closeDetailButton != null) {
            this.closeDetailButton.visible = false;
         }
      }
   }

   private void setEditButtonsVisible(boolean visible) {
      if (this.editSaveButton != null) {
         this.editSaveButton.visible = visible;
      }

      if (this.editCancelButton != null) {
         this.editCancelButton.visible = visible;
      }

      if (this.editResetButton != null) {
         this.editResetButton.visible = visible;
      }

      if (this.editDefaultButton != null) {
         this.editDefaultButton.visible = visible;
      }
   }

   protected void init() {
      super.init();
      this.clearWidgets();
      this.detailPanel = new DetailPanelPopUpScreen(this.font, node -> {
         this.playClickSound();
         this.navigateTo(node);
      }, this.progressMap::get);
      this.detailPanel.setStateListener(this::syncDetailButtons);
      this.detailPanel.setScreenshotListener(advId -> this.popups.screenshot.open(advId));
      this.popups = new PopupHandler(
         this.font,
         this,
         new TrackingPopupScreen.Listener() {
            @Override
            public void onRowClicked(AdvancementNode node) {
               ModernAdvancementsScreen.this.detailPanel.open(node);
               ModernAdvancementsScreen.this.navigateTo(node);
            }

            @Override
            public void onUntracked(Identifier id) {
               if (ModernAdvancementsScreen.this.detailPanel.getSelected() != null
                  && ModernAdvancementsScreen.this.detailPanel.getSelected().holder().id().equals(id)) {
                  ModernAdvancementsScreen.this.trackButton.setMessage(Component.translatable("gui.advancements.text.track"));
               }
            }
         },
         (uuid, var2x) -> {
            ClientPlayNetworking.send(new ModernAdvancementsPackets.RequestPlayerDetailPacket(uuid));
            this.popups.leaderboard.visible = false;
         }
      );
      int tabAreaWidth = this.width * 75 / 100;
      int searchBarY = 35;
      String existingQuery = this.search != null ? this.search.getCurrentQuery() : (lastSession != null ? lastSession.searchQuery() : "");
      this.search = new SearchHandler(
         this.font,
         tabAreaWidth + 10,
         searchBarY,
         this.width - tabAreaWidth - 20,
         existingQuery,
         new SearchHandler.SearchListener() {
            @Override
            public void onQueryChanged(String query, SearchQuery parsed) {
               ModernAdvancementsScreen.this.performSearch(parsed);
            }

            @Override
            public void onQueryCleared() {
               ModernAdvancementsScreen.this.tabBar.setAllSearchResultsTab(null);

               for (ModernAdvancementTab tab : ModernAdvancementsScreen.this.tabBar.getTabs().values()) {
                  tab.clearSearchFilter();
               }

               for (ModernAdvancementTab tab : ModernAdvancementsScreen.this.tabBar.getServerAllTabs().values()) {
                  tab.clearSearchFilter();
               }

               ModernAdvancementTab selected = ModernAdvancementsScreen.this.tabBar.getSelectedTab();
               if (selected != null && selected.isSearchTab()) {
                  Map<AdvancementHolder, ModernAdvancementTab> display = ModernAdvancementsScreen.this.tabBar.getDisplayTabs();
                  if (!display.isEmpty()) {
                     ModernAdvancementTab first = display.values().iterator().next();
                     ModernAdvancementsScreen.this.tabBar.setSelectedTab(first);
                     if (first.getRoot() != null) {
                        if (ModernAdvancementsScreen.this.tabBar.isViewAllMode()) {
                           ModernAdvancementsScreen.this.advancementHandler.setSelectedTab(first.getRoot().holder(), true);
                        }

                        ModernAdvancementsScreen.this.viewport
                           .centerOnRoot(
                              first, ModernAdvancementsScreen.this.width, ModernAdvancementsScreen.this.height, ModernAdvancementsScreen.this.isDetailOpen()
                           );
                     }
                  }
               }
            }
         }
      );
      this.addWidget(this.search.getField());
      this.tabBar = new TabBarHandler(
         this,
         this.font,
         this.advancementHandler,
         this.width,
         new TabBarHandler.Listener() {
            @Override
            public void onTabSelected(ModernAdvancementTab tab) {
               if (ModernAdvancementsScreen.this.isEditMode) {
                  if (tab.getRoot() != null) {
                     ModernAdvancementsScreen.this.switchEditTab(tab.getRoot().holder().id());
                  }
               } else if (tab.isSearchTab()) {
                  ModernAdvancementsScreen.this.viewport.setScroll(0, 0);
               } else {
                  ModernAdvancementsScreen.this.applyCurrentSearchToTab(tab);
                  if (!ModernAdvancementsScreen.this.search.getCurrentQuery().isEmpty() && tab.hasNoResults()) {
                     ModernAdvancementsScreen.this.viewport.setScroll(0, 0);
                  } else {
                     AdvancementHolder entry = tab.getRoot() != null ? tab.getRoot().holder() : null;
                     if (entry != null && ModernAdvancementsScreen.this.viewport.hasScrollFor(entry)) {
                        ModernAdvancementsScreen.this.viewport.restoreScrollFor(entry);
                     } else {
                        ModernAdvancementsScreen.this.viewport
                           .centerOnRoot(
                              tab, ModernAdvancementsScreen.this.width, ModernAdvancementsScreen.this.height, ModernAdvancementsScreen.this.isDetailOpen()
                           );
                     }
                  }
               }
            }

            @Override
            public void onTabAdded(ModernAdvancementTab tab) {
               ModernAdvancementsScreen.this.applyCurrentSearchToTab(tab);
               if (ModernAdvancementsScreen.this.viewAllButton != null) {
                  ModernAdvancementsScreen.this.viewAllButton.active = ModernAdvancementsClient.hasServerAdvancementData();
               }
            }
         }
      );
      TrackingManager.getInstance().loadForSession();
      this.advancementHandler.setListener(this);
      if (ModernAdvancementsClient.hasServerAdvancementData() && this.tabBar.getServerAllTabs().isEmpty()) {
         this.tabBar.buildServerAllTabs(ModernAdvancementsClient.getServerAdvancementTree());
      }

      this.rebuildAllLayouts();
      if (this.tabBar.getSelectedTab() == null && !this.tabBar.getTabs().isEmpty()) {
         this.tabBar.selectFirstTab();
      } else if (this.tabBar.getSelectedTab() != null) {
         ModernAdvancementTab selected = this.tabBar.getSelectedTab();
         if (selected.getRoot() != null) {
            AdvancementHolder entry = selected.getRoot().holder();
            if (this.viewport.hasScrollFor(entry)) {
               this.viewport.restoreScrollFor(entry);
            } else {
               this.viewport.centerOnRoot(selected, this.width, this.height, this.isDetailOpen());
            }

            this.tabBar.notifyHandlerOfSelected();
         }
      }

      int bottomY = this.height - 30;
      int doneW = 100;
      int doneX = this.width / 2 - doneW / 2;
      this.viewAllButton = IconButton.builder(Identifier.fromNamespaceAndPath("modern-advancements", "widget/box_empty"), var1x -> this.toggleViewAll())
         .bounds(5, bottomY, 20, 20)
         .tooltip(Tooltip.create(Component.translatable("gui.advancements.button.view_all")))
         .build();
      this.viewAllButton.active = ModernAdvancementsClient.hasServerAdvancementData();
      this.addRenderableWidget(this.viewAllButton);
      int editBtnX = 29;
      this.editButton = IconButton.builder(Identifier.fromNamespaceAndPath("modern-advancements", "widget/edit"), var1x -> {
         if (this.isEditMode) {
            this.centerOnSelected();
         } else {
            this.enterEditMode();
         }
      }).bounds(editBtnX, bottomY, 20, 20).tooltip(Tooltip.create(Component.translatable("gui.advancements.button.edit_layout"))).build();
      this.editButton.active = ModernAdvancementsClient.hasServerAdvancementData();
      this.editButton.visible = ModernAdvancementsClient.CONFIG.layoutMode() == LayoutMode.CUSTOM;
      this.addRenderableWidget(this.editButton);
      this.recenterButton = IconButton.builder(Identifier.fromNamespaceAndPath("modern-advancements", "widget/center"), var1x -> this.centerOnSelected())
         .bounds(doneX - 4 - 20, bottomY, 20, 20)
         .tooltip(Tooltip.create(Component.translatable("gui.advancements.button.recenter")))
         .build();
      this.addRenderableWidget(this.recenterButton);
      this.doneButton = Button.builder(CommonComponents.GUI_DONE, var1x -> this.onClose()).bounds(doneX, bottomY, doneW, 20).build();
      this.addRenderableWidget(this.doneButton);
      int iconRight = this.width - 5;
      int configX = iconRight - 20;
      this.configButton = IconButton.builder(
            Identifier.fromNamespaceAndPath("modern-advancements", "widget/settings"), var1x -> this.popups.toggle(PopupHandler.Type.CONFIG)
         )
         .bounds(configX, bottomY, 20, 20)
         .tooltip(Tooltip.create(Component.translatable("gui.advancements.button.settings")))
         .build();
      this.addRenderableWidget(this.configButton);
      int statsX = configX - 4 - 20;
      this.statsButton = IconButton.builder(
            Identifier.fromNamespaceAndPath("modern-advancements", "widget/stats"), var1x -> this.popups.toggle(PopupHandler.Type.STATS)
         )
         .bounds(statsX, bottomY, 20, 20)
         .tooltip(Tooltip.create(Component.translatable("gui.advancements.button.stats")))
         .build();
      this.addRenderableWidget(this.statsButton);
      int trackedX = statsX - 4 - 20;
      this.trackedButton = IconButton.builder(
            Identifier.fromNamespaceAndPath("modern-advancements", "widget/tracking"), var1x -> this.popups.toggle(PopupHandler.Type.TRACKING)
         )
         .bounds(trackedX, bottomY, 20, 20)
         .tooltip(Tooltip.create(Component.translatable("gui.advancements.button.tracked")))
         .build();
      this.addRenderableWidget(this.trackedButton);
      int leaderboardX = trackedX - 4 - 20;
      this.leaderboardButton = IconButton.builder(
            Identifier.fromNamespaceAndPath("modern-advancements", "widget/leaderboard"), var1x -> this.popups.toggle(PopupHandler.Type.LEADERBOARD)
         )
         .bounds(leaderboardX, bottomY, 20, 20)
         .tooltip(Tooltip.create(Component.translatable("gui.advancements.button.leaderboard")))
         .build();
      this.addRenderableWidget(this.leaderboardButton);
      int feedX = leaderboardX - 4 - 20;
      this.feedButton = IconButton.builder(
            Identifier.fromNamespaceAndPath("modern-advancements", "widget/list"), var1x -> this.popups.toggle(PopupHandler.Type.FEED)
         )
         .bounds(feedX, bottomY, 20, 20)
         .tooltip(Tooltip.create(Component.translatable("gui.advancements.button.feed")))
         .build();
      this.addRenderableWidget(this.feedButton);
      this.trackButton = Button.builder(Component.translatable("gui.advancements.text.track"), var1x -> {
         AdvancementNode selected = this.detailPanel.getSelected();
         if (selected != null) {
            Identifier id = selected.holder().id();
            TrackingManager tm = TrackingManager.getInstance();
            if (tm.isTracked(id)) {
               tm.untrack(id);
            } else {
               tm.track(id);
            }

            this.trackButton.setMessage(Component.translatable(tm.isTracked(id) ? "gui.advancements.text.untrack" : "gui.advancements.text.track"));
         }
      }).bounds(this.width - DetailPanelPopUpScreen.getPanelWidth() + 10, this.height - 70, DetailPanelPopUpScreen.getPanelWidth() - 20, 20).build();
      this.trackButton.visible = false;
      this.trackButton.active = false;
      this.addRenderableWidget(this.trackButton);
      this.closeDetailButton = Button.builder(Component.literal("X"), var1x -> this.detailPanel.close()).bounds(this.width - 18, 74, 14, 14).build();
      this.closeDetailButton.visible = false;
      this.addRenderableWidget(this.closeDetailButton);
      int editBtnW = 80;
      int editBtnGap = 4;
      int editBtnTotal = 4 * editBtnW + 3 * editBtnGap;
      int editBtnStart = this.width / 2 - editBtnTotal / 2;
      this.editResetButton = Button.builder(Component.translatable("gui.advancements.hud.edit.reset"), var1x -> {
         LayoutEditSession s = this.currentEditSession();
         if (s != null) {
            s.reset();
         }
      }).bounds(editBtnStart, bottomY, editBtnW, 20).build();
      this.editResetButton.visible = false;
      this.addRenderableWidget(this.editResetButton);
      this.editDefaultButton = Button.builder(Component.translatable("gui.advancements.hud.edit.default"), var1x -> {
         LayoutEditSession s = this.currentEditSession();
         if (s != null) {
            s.applyDefault();
         }
      }).bounds(editBtnStart + editBtnW + editBtnGap, bottomY, editBtnW, 20).build();
      this.editDefaultButton.visible = false;
      this.addRenderableWidget(this.editDefaultButton);
      this.editSaveButton = Button.builder(Component.translatable("gui.advancements.hud.edit.save"), var1x -> this.exitEditMode(true))
         .bounds(editBtnStart + 2 * (editBtnW + editBtnGap), bottomY, editBtnW, 20)
         .build();
      this.editSaveButton.visible = false;
      this.addRenderableWidget(this.editSaveButton);
      this.editCancelButton = Button.builder(Component.translatable("gui.advancements.hud.edit.cancel"), var1x -> this.exitEditMode(false))
         .bounds(editBtnStart + 3 * (editBtnW + editBtnGap), bottomY, editBtnW, 20)
         .build();
      this.editCancelButton.visible = false;
      this.addRenderableWidget(this.editCancelButton);
      this.restoreSessionState();
   }

   private void toggleViewAll() {
      if (ModernAdvancementsClient.hasServerAdvancementData()) {
         if (!this.isEditMode) {
            this.viewport.saveScrollForTab(this.tabBar.getSelectedTab());
            if (this.tabBar.isViewAllMode()) {
               if (this.tabBar.getServerAllTabs().isEmpty()) {
                  this.tabBar.buildServerAllTabs(ModernAdvancementsClient.getServerAdvancementTree());
               }

               this.tabBar.enterViewAll();
               this.viewAllButton.setIcon(Identifier.fromNamespaceAndPath("modern-advancements", "widget/box_full"));
            } else {
               this.tabBar.exitViewAll();
               this.viewAllButton.setIcon(Identifier.fromNamespaceAndPath("modern-advancements", "widget/box_empty"));
            }

            ModernAdvancementTab selected = this.tabBar.getSelectedTab();
            if (selected != null) {
               this.viewport.centerOnRoot(selected, this.width, this.height, this.isDetailOpen());
            }

            this.detailPanel.close();
            if (this.search != null && !this.search.getCurrentQuery().isEmpty()) {
               this.performSearch(SearchQuery.parse(this.search.getCurrentQuery()));
            }
         }
      }
   }

   private void autoFocusTracked() {
      List<Identifier> tracked = TrackingManager.getInstance().getTracked();
      if (!tracked.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() != null) {
            for (Identifier id : tracked) {
               AdvancementNode node = mc.getConnection().getAdvancements().getTree().get(id);
               if (node != null) {
                  ModernAdvancementTab tab = this.tabBar.getTabs().get(node.root().holder());
                  if (tab != null) {
                     this.viewport.saveScrollForTab(this.tabBar.getSelectedTab());
                     this.tabBar.setSelectedTab(tab);
                     this.advancementHandler.setSelectedTab(node.root().holder(), true);
                     this.viewport.centerOnNode(node, tab, this.width, this.height, this.isDetailOpen());
                     return;
                  }
               }
            }
         }
      }
   }

   private void performSearch(SearchQuery sq) {
      List<AdvancementNode> allResults = new ArrayList<>();
      Collection<ModernAdvancementTab> sourceTabs = this.tabBar.getDisplayTabs().values();
      if (sq.isMultiTerm()) {
         for (String orTerm : sq.splitTerms()) {
            for (ModernAdvancementTab tab : sourceTabs) {
               tab.searchAdvancements(orTerm.trim(), allResults, sq.onlyIncomplete(), sq.onlyComplete(), this);
            }
         }

         allResults = new ArrayList<>(new LinkedHashSet<>(allResults));
      } else {
         for (ModernAdvancementTab tab : sourceTabs) {
            tab.searchAdvancements(sq.term(), allResults, sq.onlyIncomplete(), sq.onlyComplete(), this);
         }
      }

      ModernAdvancementTab searchTab = new ModernAdvancementTab(this, allResults, "gui.advancements.text.search.tab");
      this.tabBar.setAllSearchResultsTab(searchTab);
      ModernAdvancementTab currentTab = this.tabBar.getSelectedTab();
      if (currentTab != null && !currentTab.isSearchTab()) {
         currentTab.applySearchFilter(sq.term(), sq.onlyIncomplete(), sq.onlyComplete(), this);
      }

      this.tabBar.setSelectedTab(searchTab);
      this.viewport.setScroll(0, 0);
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      context.centeredText(this.font, this.title, this.width / 2, 5, -1);
      this.tabBar.render(context, mouseX, mouseY, this.popups.anyOpen() || this.isEditMode);
      int advAreaLeft = 0;
      int advAreaTop = 70;
      int advAreaBottom = this.height - 40;
      int advAreaRight = this.isEditMode ? this.width : (this.isDetailOpen() ? this.width - DetailPanelPopUpScreen.getPanelWidth() : this.width);
      if (!this.isEditMode) {
         this.search.getField().extractRenderState(context, mouseX, mouseY, delta);
      }

      context.fill(advAreaLeft, advAreaTop, advAreaRight, advAreaBottom, Integer.MIN_VALUE);
      context.outline(advAreaLeft, advAreaTop, advAreaRight - advAreaLeft, advAreaBottom - advAreaTop, -12566464);
      float zoom = this.viewport.getZoom();
      if (this.isEditMode) {
         LayoutEditSession session = this.currentEditSession();
         if (session != null) {
            this.renderEditModeArea(context, mouseX, mouseY, zoom, advAreaLeft, advAreaRight, advAreaTop, advAreaBottom, session);
         }

         this.trackButton.visible = false;
         this.closeDetailButton.visible = false;
         super.extractRenderState(context, mouseX, mouseY, delta);
      } else {
         ModernAdvancementTab selectedTab = this.tabBar.getSelectedTab();
         boolean blockHover = this.popups.anyOpen();
         if (selectedTab != null) {
            context.enableScissor(advAreaLeft + 5, advAreaTop + 5, advAreaRight - 5, advAreaBottom - 5);
            int transformedMouseX = blockHover ? -9999 : (int)((mouseX - (advAreaLeft + 20)) / zoom + (advAreaLeft + 20));
            int transformedMouseY = blockHover ? -9999 : (int)((mouseY - (advAreaTop + 20)) / zoom + (advAreaTop + 20));
            boolean mouseInAdvArea = mouseX >= advAreaLeft + 5 && mouseX < advAreaRight - 5 && mouseY >= advAreaTop + 5 && mouseY < advAreaBottom - 5;
            boolean mouseOverDetail = !blockHover && this.isDetailOpen() && mouseX >= this.width - DetailPanelPopUpScreen.getPanelWidth();
            boolean suppressTooltips = mouseOverDetail || blockHover || !mouseInAdvArea;
            context.pose().pushMatrix();
            context.pose().translate(advAreaLeft + 20, advAreaTop + 20);
            context.pose().scale(zoom, zoom);
            context.pose().translate(-(advAreaLeft + 20), -(advAreaTop + 20));
            selectedTab.render(
               context,
               advAreaLeft + 20,
               advAreaTop + 20,
               (int)(this.viewport.getScrollX() / zoom),
               (int)(this.viewport.getScrollY() / zoom),
               transformedMouseX,
               transformedMouseY,
               mouseX,
               mouseY,
               this.font,
               suppressTooltips
            );
            context.pose().popMatrix();
            context.disableScissor();
         }

         this.detailPanel.render(context, mouseX, mouseY, this.width, this.height);
         if (ModernAdvancementsClient.pendingDetailPacket != null) {
            this.popups.comparison.open(ModernAdvancementsClient.pendingDetailPacket);
            ModernAdvancementsClient.pendingDetailPacket = null;
         }

         boolean popupOpen = this.popups.anyOpen();
         this.trackButton.visible = this.isDetailOpen() && !popupOpen;
         this.closeDetailButton.visible = this.isDetailOpen() && !popupOpen;
         if (!this.viewAllButton.active) {
            int bx = this.viewAllButton.getX();
            int by = this.viewAllButton.getY();
            if (mouseX >= bx && mouseX <= bx + this.viewAllButton.getWidth() && mouseY >= by && mouseY <= by + this.viewAllButton.getHeight()) {
               context.setTooltipForNextFrame(this.font, Component.translatable("gui.advancements.button.view_all.not_synced"), mouseX, mouseY);
            }
         }

         this.search.renderDropdown(context, this.font);
         int widgetMouseX = popupOpen ? -9999 : mouseX;
         int widgetMouseY = popupOpen ? -9999 : mouseY;
         super.extractRenderState(context, widgetMouseX, widgetMouseY, delta);
         if (popupOpen) {
            context.nextStratum();
            this.popups
               .renderAll(context, mouseX, mouseY, this.width, this.height, this::buildCompletionStats, ModernAdvancementsClient.hasServerAdvancementData());
         }
      }
   }

   private void renderEditModeArea(
      GuiGraphicsExtractor context,
      int mouseX,
      int mouseY,
      float zoom,
      int advAreaLeft,
      int advAreaRight,
      int advAreaTop,
      int advAreaBottom,
      LayoutEditSession session
   ) {
      int scissorLeft = advAreaLeft + 5;
      int scissorTop = advAreaTop + 5;
      int scissorRight = advAreaRight - 5;
      int scissorBottom = advAreaBottom - 5;
      context.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom);
      boolean mouseInAdvArea = mouseX >= scissorLeft && mouseX < scissorRight && mouseY >= scissorTop && mouseY < scissorBottom;
      boolean suppressTooltips = !mouseInAdvArea;
      int transformedMouseX = suppressTooltips ? -9999 : (int)((mouseX - (advAreaLeft + 20)) / zoom + (advAreaLeft + 20));
      int transformedMouseY = suppressTooltips ? -9999 : (int)((mouseY - (advAreaTop + 20)) / zoom + (advAreaTop + 20));
      context.pose().pushMatrix();
      context.pose().translate(advAreaLeft + 20, advAreaTop + 20);
      context.pose().scale(zoom, zoom);
      context.pose().translate(-(advAreaLeft + 20), -(advAreaTop + 20));
      session.getTab()
         .renderInEditMode(
            context,
            advAreaLeft + 20,
            advAreaTop + 20,
            (int)(this.viewport.getScrollX() / zoom),
            (int)(this.viewport.getScrollY() / zoom),
            transformedMouseX,
            transformedMouseY,
            mouseX,
            mouseY,
            this.font,
            session.getPendingPositions(),
            session.getDragging(),
            suppressTooltips
         );
      context.pose().popMatrix();
      context.disableScissor();
      context.fill(advAreaLeft, advAreaTop, advAreaRight, advAreaTop + 30, -872415232);
      context.text(this.font, Component.translatable("gui.advancements.layout.edit.hint"), advAreaLeft + 8, advAreaTop + 4, -3355444, false);
      context.text(this.font, Component.translatable("gui.advancements.layout.edit.notice"), advAreaLeft + 8, advAreaTop + 16, -3355444, false);
      Component tabName = Component.literal("[ ").append(session.getTab().getTitle()).append(" ]");
      int tabNameW = this.font.width(tabName);
      context.text(this.font, tabName, advAreaRight - tabNameW - 8, advAreaTop + 10, -7820545, false);
   }

   public void syncEditButtonVisibility() {
      if (this.editButton != null) {
         this.editButton.visible = ModernAdvancementsClient.CONFIG.layoutMode() == LayoutMode.CUSTOM;
      }
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      if (this.isEditMode) {
         if (button == 0) {
            int advAreaTop = 70;
            int advAreaBottom = this.height - 40;
            boolean inAdvArea = mouseX >= 5.0 && mouseX < this.width - 5 && mouseY >= advAreaTop + 5 && mouseY < advAreaBottom - 5;
            if (mouseY >= 20.0 && mouseY <= 70.0) {
               this.viewport.saveScrollForTab(this.tabBar.getSelectedTab());
               this.tabBar.mouseClicked(mouseX, mouseY, false);
               return true;
            }

            if (inAdvArea) {
               LayoutEditSession session = this.currentEditSession();
               if (session != null) {
                  float zoom = this.viewport.getZoom();
                  double layoutX = (mouseX - 20.0 + this.viewport.getScrollX()) / zoom;
                  double layoutY = (mouseY - (advAreaTop + 20) + this.viewport.getScrollY()) / zoom;
                  if (session.tryStartDrag(layoutX, layoutY)) {
                     return true;
                  }
               }
            }
         }

         return super.mouseClicked(click, doubled);
      } else {
         this.search.onSearchFieldClicked(mouseX, mouseY);
         if (button == 0) {
            if (this.popups.mouseClicked(mouseX, mouseY, this.width, this.height)) {
               return true;
            }

            if (this.isDetailOpen() && this.detailPanel.mouseClicked(mouseX, mouseY, this.width, this.height)) {
               return true;
            }

            this.viewport.saveScrollForTab(this.tabBar.getSelectedTab());
            if (this.tabBar.mouseClicked(mouseX, mouseY, this.popups.anyOpen())) {
               return true;
            }

            ModernAdvancementTab selectedTab = this.tabBar.getSelectedTab();
            int advAreaRight = this.width - (this.isDetailOpen() ? DetailPanelPopUpScreen.getPanelWidth() : 0);
            if (selectedTab != null && mouseX < advAreaRight && mouseY > 70.0 && mouseY < this.height - 40) {
               float zoom = this.viewport.getZoom();
               int adjX = (int)((mouseX - 20.0) / zoom + 20.0);
               int adjY = (int)((mouseY - 90.0) / zoom + 90.0);
               AdvancementNode clicked = selectedTab.getAdvancementAt(
                  adjX, adjY, 20, 90, (int)(this.viewport.getScrollX() / zoom), (int)(this.viewport.getScrollY() / zoom)
               );
               if (clicked != null) {
                  this.playClickSound();
                  if (this.detailPanel.getSelected() == clicked && this.detailPanel.visible) {
                     this.detailPanel.close();
                  } else {
                     this.detailPanel.open(clicked);
                  }

                  return true;
               }
            }
         }

         return super.mouseClicked(click, doubled);
      }
   }

   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      if (this.isEditMode && button == 0) {
         LayoutEditSession session = this.currentEditSession();
         if (session != null && session.isDragging()) {
            float zoom = this.viewport.getZoom();
            double layoutX = (mouseX - 20.0 + this.viewport.getScrollX()) / zoom;
            double layoutY = (mouseY - 90.0 + this.viewport.getScrollY()) / zoom;
            session.updateDrag(layoutX, layoutY);
            return true;
         }

         int advAreaBottom = this.height - 40;
         if (mouseX >= 5.0 && mouseX < this.width - 5 && mouseY >= 75.0 && mouseY < advAreaBottom - 5) {
            this.viewport.setScroll(this.viewport.getScrollX() - (int)offsetX, this.viewport.getScrollY() - (int)offsetY);
            if (session != null) {
               this.viewport.saveScrollForTab(session.getTab());
            }

            return true;
         }
      }

      if (button == 0) {
         if (this.popups.mouseDragged(mouseX, mouseY, this.width, this.height)) {
            return true;
         }

         if (this.detailPanel.mouseDragged(mouseY, this.height)) {
            return true;
         }

         if (this.tabBar.mouseDragged(offsetX)) {
            return true;
         }

         ModernAdvancementTab selectedTab = this.tabBar.getSelectedTab();
         int advAreaRight = this.width - (this.isDetailOpen() ? DetailPanelPopUpScreen.getPanelWidth() : 0);
         if (selectedTab != null && mouseX < advAreaRight && mouseY > 70.0 && mouseY < this.height - 40) {
            this.viewport.setScroll(this.viewport.getScrollX() - (int)offsetX, this.viewport.getScrollY() - (int)offsetY);
            this.viewport.saveScrollForTab(selectedTab);
            return true;
         }
      }

      return super.mouseDragged(click, offsetX, offsetY);
   }

   public boolean mouseReleased(MouseButtonEvent click) {
      if (this.isEditMode) {
         LayoutEditSession session = this.currentEditSession();
         if (session != null) {
            session.endDrag();
         }
      }

      this.tabBar.mouseReleased();
      this.detailPanel.mouseReleased();
      this.popups.mouseReleased();
      return super.mouseReleased(click);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (!this.isEditMode) {
         if (this.popups.mouseScrolled(mouseX, mouseY, verticalAmount, this.width, this.height)) {
            return true;
         }

         if (this.tabBar.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
         }

         if (this.detailPanel.mouseScrolled(mouseX, verticalAmount, this.width)) {
            return true;
         }
      }

      int advAreaRight = this.isEditMode ? this.width : this.width - (this.isDetailOpen() ? DetailPanelPopUpScreen.getPanelWidth() : 0);
      ModernAdvancementTab activeTab = this.isEditMode
         ? (this.currentEditSession() != null ? this.currentEditSession().getTab() : null)
         : this.tabBar.getSelectedTab();
      if (activeTab != null && mouseX < advAreaRight && mouseY > 70.0 && mouseY < this.height - 40) {
         if (this.viewport.applyZoom(verticalAmount, mouseX, mouseY)) {
            this.viewport.saveScrollForTab(activeTab);
         }

         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   public boolean keyPressed(KeyEvent input) {
      int keyCode = input.input();
      if (this.isEditMode) {
         if (keyCode == 256) {
            this.exitEditMode(false);
            return true;
         } else {
            return super.keyPressed(input);
         }
      } else if (this.search.keyPressed(keyCode)) {
         return true;
      } else if (this.popups.keyPressed(keyCode)) {
         return true;
      } else if (keyCode == 256 && this.detailPanel.visible && this.detailPanel.getSelected() != null) {
         this.detailPanel.close();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   public void onClose() {
      if (this.isEditMode) {
         this.exitEditMode(false);
      }

      this.saveSessionState();
      Minecraft.getInstance().setScreenAndShow(this.parent);
   }

   public void removed() {
      this.advancementHandler.setListener(null);
      TrackingManager.getInstance().activateAsListener(this.advancementHandler);
      ClientPacketListener handler = Minecraft.getInstance().getConnection();
      if (handler != null) {
         handler.send(ServerboundSeenAdvancementsPacket.closedScreen());
      }
   }

   public void onAddAdvancementRoot(AdvancementNode root) {
      this.tabBar.addRoot(root);
   }

   public void onRemoveAdvancementRoot(AdvancementNode root) {
      this.tabBar.removeRoot(root);
   }

   public void onAddAdvancementTask(AdvancementNode dependent) {
      this.tabBar.addTask(dependent);
      ModernAdvancementTab tab = this.tabBar.getTabs().get(dependent.root().holder());
      if (tab != null) {
         this.applyCurrentSearchToTab(tab);
      }
   }

   public void onRemoveAdvancementTask(AdvancementNode dependent) {
   }

   public void onUpdateAdvancementProgress(AdvancementNode advancement, AdvancementProgress progress) {
      this.progressMap.put(advancement.holder(), progress);
      TrackingManager.getInstance().updateProgress(advancement, progress);
   }

   public void onSelectedTabChanged(@Nullable AdvancementHolder advancement) {
      this.tabBar.onServerTabChanged(advancement);
   }

   public void onAdvancementsCleared() {
      this.tabBar.clear();
      this.progressMap.clear();
   }

   @Nullable
   public AdvancementProgress getAdvancementProgress(AdvancementHolder entry) {
      return this.progressMap.get(entry);
   }
}
