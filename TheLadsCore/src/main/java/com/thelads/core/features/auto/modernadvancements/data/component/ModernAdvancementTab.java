package com.thelads.core.features.auto.modernadvancements.data.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import com.thelads.core.features.auto.modernadvancements.client.screen.ModernAdvancementsScreen;
import com.thelads.core.features.auto.modernadvancements.data.layout.LayoutMode;
import com.thelads.core.features.auto.modernadvancements.data.layout.ModernLayout;
import com.thelads.core.features.auto.modernadvancements.data.layout.TabLayoutOverride;
import com.thelads.core.features.auto.modernadvancements.data.layout.TabLayoutOverrideManager;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

public class ModernAdvancementTab {
   private static final int ICON_SPACING = 50;
   private static final int GRID_SIZE = 64;
   private static final int SEARCH_ROWS = 10;
   private final Map<AdvancementHolder, AdvancementIcon> icons = new LinkedHashMap<>();
   private final Map<AdvancementHolder, AdvancementIcon> allIcons = new LinkedHashMap<>();
   private final ModernAdvancementsScreen screen;
   private final AdvancementNode root;
   private final ItemStack icon;
   private final Component title;
   private final boolean isSearchTab;
   private LayoutMode activeLayout = LayoutMode.VANILLA;
   private boolean hasActiveFilter = false;
   private AdvancementNode noResultsPlaceholder = null;

   public ModernAdvancementTab(@Nullable ModernAdvancementsScreen screen, @Nullable AdvancementNode root, DisplayInfo display) {
      this.screen = screen;
      this.root = root;
      this.icon = display.getIcon().create();
      this.title = display.getTitle();
      this.isSearchTab = false;
      if (root != null) {
         this.addAdvancement(root);
      }
   }

   public ModernAdvancementTab(@Nullable ModernAdvancementsScreen screen, List<AdvancementNode> advancements, String titleKey) {
      this.screen = screen;
      this.root = advancements.isEmpty() ? null : advancements.getFirst().root();
      this.icon = ItemStack.EMPTY;
      this.title = Component.translatable(titleKey);
      this.isSearchTab = true;
      advancements.sort((a, b) -> {
         String ta = a.advancement().display().map(dx -> dx.getTitle().getString()).orElse("");
         String tb = b.advancement().display().map(dx -> dx.getTitle().getString()).orElse("");
         return ta.compareToIgnoreCase(tb);
      });
      int x = 0;
      int y = 0;

      for (AdvancementNode adv : advancements) {
         Optional<DisplayInfo> d = adv.advancement().display();
         if (d.isPresent()) {
            AdvancementIcon advIcon = new AdvancementIcon(adv, d.get(), x * 64, y * 64);
            this.icons.put(adv.holder(), advIcon);
            this.allIcons.put(adv.holder(), advIcon);
            if (++x >= 10) {
               x = 0;
               y++;
            }
         }
      }
   }

   @Nullable
   public static ModernAdvancementTab create(ModernAdvancementsScreen screen, AdvancementNode root) {
      return root.advancement().display().map(display -> new ModernAdvancementTab(screen, root, display)).orElse(null);
   }

   public void addAdvancement(AdvancementNode advancement) {
      Optional<DisplayInfo> display = advancement.advancement().display();
      if (!display.isEmpty()) {
         DisplayInfo d = display.get();
         int x = (int)(d.getX() * 50.0F);
         int y = (int)(d.getY() * 50.0F);
         AdvancementIcon advIcon = new AdvancementIcon(advancement, d, x, y);
         this.icons.put(advancement.holder(), advIcon);
         this.allIcons.put(advancement.holder(), advIcon);
      }
   }

   public void rebuildLayout(LayoutMode mode) {
      if (!this.isSearchTab && this.root != null) {
         this.activeLayout = mode;
         Map<AdvancementHolder, int[]> positions;
         switch (mode) {
            case MODERN: {
               List<AdvancementNode> allNodes = this.allIcons.values().stream().map(i -> i.advancement).collect(Collectors.toList());
               positions = new ModernLayout().compute(this.root, allNodes);
               break;
            }
            case CUSTOM: {
               List<AdvancementNode> allNodes = this.allIcons.values().stream().map(i -> i.advancement).collect(Collectors.toList());
               positions = new LinkedHashMap<>(new ModernLayout().compute(this.root, allNodes));
               this.applyOverridesToPositions(positions);
               break;
            }
            default:
               positions = new LinkedHashMap<>();

               for (AdvancementIcon icon : this.allIcons.values()) {
                  int x = (int)(icon.display.getX() * 50.0F);
                  int y = (int)(icon.display.getY() * 50.0F);
                  positions.put(icon.advancement.holder(), new int[]{x, y});
               }
         }

         this.applyPositionsToIcons(positions);
      }
   }

   private void applyOverridesToPositions(Map<AdvancementHolder, int[]> positions) {
      if (this.root != null) {
         TabLayoutOverride override = TabLayoutOverrideManager.getOrLoad(this.root.holder().id());
         if (!override.isEmpty()) {
            Identifier rootId = this.root.holder().id();

            for (Entry<String, int[]> entry : override.advancements.entrySet()) {
               if (!entry.getKey().equals(rootId.toString())) {
                  try {
                     Identifier id = Identifier.parse(entry.getKey());

                     for (AdvancementHolder holder : positions.keySet()) {
                        if (holder.id().equals(id)) {
                           int[] raw = entry.getValue();
                           if (raw != null && raw.length == 2) {
                              positions.put(holder, new int[]{raw[0], raw[1]});
                           }
                           break;
                        }
                     }
                  } catch (Exception var10) {
                  }
               }
            }
         }
      }
   }

   private void applyPositionsToIcons(Map<AdvancementHolder, int[]> positions) {
      Map<AdvancementHolder, AdvancementIcon> newAllIcons = new LinkedHashMap<>();

      for (Entry<AdvancementHolder, AdvancementIcon> entry : this.allIcons.entrySet()) {
         AdvancementIcon old = entry.getValue();
         int[] pos = positions.get(entry.getKey());
         newAllIcons.put(entry.getKey(), pos != null ? new AdvancementIcon(old.advancement, old.display, pos[0], pos[1]) : old);
      }

      this.allIcons.clear();
      this.allIcons.putAll(newAllIcons);
      if (!this.hasActiveFilter && this.noResultsPlaceholder == null) {
         this.icons.clear();
         this.icons.putAll(this.allIcons);
      } else {
         List<AdvancementHolder> visibleKeys = new ArrayList<>(this.icons.keySet());
         this.icons.clear();

         for (AdvancementHolder holder : visibleKeys) {
            AdvancementIcon updated = this.allIcons.get(holder);
            if (updated != null) {
               this.icons.put(holder, updated);
            }
         }
      }
   }

   public boolean hasNoResults() {
      return this.noResultsPlaceholder != null;
   }

   public void clearSearchFilter() {
      if (!this.isSearchTab) {
         this.icons.clear();
         this.icons.putAll(this.allIcons);
         this.hasActiveFilter = false;
         this.noResultsPlaceholder = null;
      }
   }

   public void applySearchFilter(String searchTerm, boolean onlyIncomplete, boolean onlyComplete, ModernAdvancementsScreen screen) {
      if (!this.isSearchTab) {
         this.icons.clear();
         this.noResultsPlaceholder = null;
         if (searchTerm.isEmpty() && !onlyIncomplete && !onlyComplete) {
            this.icons.putAll(this.allIcons);
            this.hasActiveFilter = false;
         } else {
            this.hasActiveFilter = true;
            List<AdvancementNode> matches = new ArrayList<>();
            if (searchTerm.contains("|SPLIT|")) {
               for (String term : searchTerm.split("\\|SPLIT\\|")) {
                  String trimmed = term.trim();

                  for (AdvancementIcon advIcon : this.allIcons.values()) {
                     if (this.matchesFilter(advIcon, trimmed, onlyIncomplete, onlyComplete, screen)) {
                        matches.add(advIcon.advancement);
                     }
                  }
               }

               matches = new ArrayList<>(new LinkedHashSet<>(matches));
            } else {
               for (AdvancementIcon advIconx : this.allIcons.values()) {
                  if (this.matchesFilter(advIconx, searchTerm, onlyIncomplete, onlyComplete, screen)) {
                     matches.add(advIconx.advancement);
                  }
               }
            }

            if (matches.isEmpty()) {
               this.createNoResultsPlaceholder();
            } else {
               for (AdvancementNode adv : matches) {
                  AdvancementIcon advIconxx = this.allIcons.get(adv.holder());
                  if (advIconxx != null) {
                     this.icons.put(adv.holder(), advIconxx);
                  }
               }
            }
         }
      }
   }

   private boolean matchesFilter(AdvancementIcon advIcon, String searchTerm, boolean onlyIncomplete, boolean onlyComplete, ModernAdvancementsScreen screen) {
      if (onlyIncomplete || onlyComplete) {
         boolean complete = advIcon.isCompleted(screen);
         if (onlyIncomplete && complete) {
            return false;
         }

         if (onlyComplete && !complete) {
            return false;
         }
      }

      return searchTerm.isEmpty() || advIcon.matchesSearch(searchTerm);
   }

   private void createNoResultsPlaceholder() {
      if (this.root != null || !this.allIcons.isEmpty()) {
         DisplayInfo noResults = new DisplayInfo(
            new ItemStackTemplate(Items.BARRIER),
            Component.translatable("gui.advancements.text.search.no_results"),
            Component.translatable("gui.advancements.text.search.no_results.text"),
            Optional.empty(),
            AdvancementType.TASK,
            false,
            false,
            false
         );
         AdvancementNode placeholder = this.root != null ? this.root : this.allIcons.values().iterator().next().advancement;
         this.icons.put(placeholder.holder(), new AdvancementIcon(placeholder, noResults, 0, 0));
         this.noResultsPlaceholder = placeholder;
      }
   }

   public boolean isSearchTab() {
      return this.isSearchTab;
   }

   public void searchAdvancements(String query, List<AdvancementNode> results, boolean onlyIncomplete, boolean onlyComplete, ModernAdvancementsScreen screen) {
      for (AdvancementIcon advIcon : this.allIcons.values()) {
         if (onlyIncomplete || onlyComplete) {
            boolean complete = advIcon.isCompleted(screen);
            if (onlyIncomplete && complete || onlyComplete && !complete) {
               continue;
            }
         }

         if (query.isEmpty() || advIcon.matchesSearch(query)) {
            results.add(advIcon.advancement);
         }
      }
   }

   public void render(
      GuiGraphicsExtractor context,
      int screenX,
      int screenY,
      int scrollOffsetX,
      int scrollOffsetY,
      int mouseX,
      int mouseY,
      int realMouseX,
      int realMouseY,
      Font font,
      boolean suppressTooltips
   ) {
      if (this.isSearchTab && this.icons.isEmpty()) {
         context.text(
            font, Component.translatable("gui.advancements.text.search.no_results"), screenX - scrollOffsetX, screenY - scrollOffsetY + 10, -5592406, false
         );
      } else {
         if (this.noResultsPlaceholder == null) {
            AdvancementConnectionRenderer renderer = new AdvancementConnectionRenderer(this.screen);
            if (this.activeLayout != LayoutMode.MODERN && this.activeLayout != LayoutMode.CUSTOM) {
               renderer.renderConnections(context, screenX, screenY, scrollOffsetX, scrollOffsetY, this.icons);
            } else {
               renderer.renderConnectionsSpine(context, screenX, screenY, scrollOffsetX, scrollOffsetY, this.icons);
            }
         }

         for (AdvancementIcon advIcon : this.icons.values()) {
            advIcon.render(context, screenX, screenY, scrollOffsetX, scrollOffsetY, mouseX, mouseY, realMouseX, realMouseY, font, suppressTooltips, this.screen);
         }
      }
   }

   public void renderInEditMode(
      GuiGraphicsExtractor context,
      int screenX,
      int screenY,
      int scrollOffsetX,
      int scrollOffsetY,
      int mouseX,
      int mouseY,
      int realMouseX,
      int realMouseY,
      Font font,
      Map<AdvancementHolder, int[]> pendingPositions,
      @Nullable AdvancementHolder dragging,
      boolean suppressTooltips
   ) {
      AdvancementConnectionRenderer renderer = new AdvancementConnectionRenderer(this.screen);
      renderer.renderConnectionsSpineWithOverrides(context, screenX, screenY, scrollOffsetX, scrollOffsetY, this.allIcons, pendingPositions);
      AdvancementHolder rootHolder = this.root != null ? this.root.holder() : null;

      for (Entry<AdvancementHolder, AdvancementIcon> entry : this.allIcons.entrySet()) {
         AdvancementHolder holder = entry.getKey();
         AdvancementIcon advIcon = entry.getValue();
         int[] pos = pendingPositions.getOrDefault(holder, new int[]{advIcon.x, advIcon.y});
         boolean isRoot = holder.equals(rootHolder);
         boolean isDragThis = holder.equals(dragging);
         advIcon.renderAt(
            context,
            screenX,
            screenY,
            scrollOffsetX,
            scrollOffsetY,
            pos[0],
            pos[1],
            mouseX,
            mouseY,
            realMouseX,
            realMouseY,
            font,
            suppressTooltips,
            this.screen,
            isRoot,
            isDragThis
         );
      }
   }

   public void drawIcon(GuiGraphicsExtractor context, int x, int y, int width, int height) {
      if (!this.icon.isEmpty()) {
         context.item(this.icon, x + width / 2 - 8, y + height / 2 - 8);
      } else if (this.isSearchTab) {
         int cx = x + width / 2;
         int cy = y + height / 2;
         context.fill(cx - 6, cy - 6, cx + 6, cy + 6, -1);
         context.fill(cx - 4, cy - 4, cx + 4, cy + 4, -16777216);
         context.fill(cx + 4, cy + 4, cx + 8, cy + 8, -1);
      }
   }

   @Nullable
   public AdvancementNode getAdvancementAt(double mouseX, double mouseY, int screenX, int screenY, int scrollOffsetX, int scrollOffsetY) {
      for (AdvancementIcon advIcon : this.icons.values()) {
         if (advIcon.isMouseOver(mouseX, mouseY, screenX, screenY, scrollOffsetX, scrollOffsetY)) {
            if (advIcon.advancement == this.noResultsPlaceholder) {
               return null;
            }

            return advIcon.advancement;
         }
      }

      return null;
   }

   @Nullable
   public AdvancementNode getRoot() {
      return this.root;
   }

   public Component getTitle() {
      return this.title;
   }

   public ItemStack getIconItem() {
      return this.icon;
   }

   public int[] getRootPosition() {
      if (this.root == null) {
         return new int[]{0, 0};
      } else {
         AdvancementIcon rootIcon = this.allIcons.get(this.root.holder());
         return rootIcon == null ? new int[]{0, 0} : new int[]{rootIcon.x, rootIcon.y};
      }
   }

   @Nullable
   public int[] getPositionOf(AdvancementHolder holder) {
      AdvancementIcon advIcon = this.allIcons.get(holder);
      return advIcon == null ? null : new int[]{advIcon.x, advIcon.y};
   }

   public Map<AdvancementHolder, int[]> getAllPositions() {
      Map<AdvancementHolder, int[]> result = new LinkedHashMap<>();

      for (Entry<AdvancementHolder, AdvancementIcon> entry : this.allIcons.entrySet()) {
         result.put(entry.getKey(), new int[]{entry.getValue().x, entry.getValue().y});
      }

      return result;
   }

   public Map<AdvancementHolder, int[]> getModernBasePositions() {
      if (!this.isSearchTab && this.root != null) {
         List<AdvancementNode> allNodes = this.allIcons.values().stream().map(i -> i.advancement).collect(Collectors.toList());
         return new ModernLayout().compute(this.root, allNodes);
      } else {
         return new LinkedHashMap<>();
      }
   }

   public int[] getCompletionStats(ModernAdvancementsScreen screen) {
      int total = this.allIcons.size();
      int completed = 0;

      for (AdvancementIcon icon : this.allIcons.values()) {
         if (icon.isCompleted(screen)) {
            completed++;
         }
      }

      return new int[]{completed, total};
   }
}
