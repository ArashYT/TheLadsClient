package com.thelads.core.features.auto.modernadvancements.data.handler;

import java.util.ArrayList;
import java.util.List;
import com.thelads.core.features.auto.modernadvancements.data.search.SearchQuery;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class SearchHandler {
   private final EditBox searchField;
   private final SearchHandler.SearchListener listener;
   private String currentQuery;
   private boolean showHelp = false;
   private final List<String> suggestions = new ArrayList<>();
   private int selectedSuggestionIndex = -1;
   private boolean selectableSuggestions = false;

   public SearchHandler(Font font, int x, int y, int width, String existingQuery, SearchHandler.SearchListener listener) {
      this.listener = listener;
      this.searchField = new EditBox(font, x, y, width, 20, Component.translatable("gui.advancements.text.search"));
      this.searchField.setMaxLength(99);
      this.searchField.setHint(Component.translatable("gui.advancements.text.search"));
      this.searchField.setValue(existingQuery);
      this.currentQuery = existingQuery;
      this.searchField.setResponder(this::onSearchChanged);
   }

   public EditBox getField() {
      return this.searchField;
   }

   public String getCurrentQuery() {
      return this.currentQuery;
   }

   public boolean isShowingHelp() {
      return this.showHelp && !this.suggestions.isEmpty() && this.searchField.isFocused();
   }

   public void reapplyQuery() {
      if (!this.currentQuery.isEmpty()) {
         this.onSearchChanged(this.currentQuery);
      }
   }

   public void onSearchFieldClicked(double mx, double my) {
      boolean onField = mx >= this.searchField.getX()
         && mx <= this.searchField.getX() + this.searchField.getWidth()
         && my >= this.searchField.getY()
         && my <= this.searchField.getY() + 20;
      if (onField) {
         this.searchField.setFocused(true);
      } else if (!this.isOnDropdown(mx, my)) {
         this.searchField.setFocused(false);
         this.showHelp = false;
      }
   }

   public boolean isOnDropdown(double mx, double my) {
      if (this.showHelp && !this.suggestions.isEmpty()) {
         int dy = this.searchField.getY() + 22;
         int dh = this.suggestions.size() * 12 + 4;
         return mx >= this.searchField.getX() && mx <= this.searchField.getX() + this.searchField.getWidth() && my >= dy && my <= dy + dh;
      } else {
         return false;
      }
   }

   public boolean keyPressed(int keyCode) {
      if (!this.showHelp || !this.selectableSuggestions || this.suggestions.isEmpty() || !this.searchField.isFocused()) {
         return false;
      } else if (keyCode == 264) {
         this.selectedSuggestionIndex = Math.min(this.selectedSuggestionIndex + 1, this.suggestions.size() - 1);
         if (this.selectedSuggestionIndex < 0) {
            this.selectedSuggestionIndex = 0;
         }

         return true;
      } else if (keyCode == 265) {
         this.selectedSuggestionIndex = Math.max(this.selectedSuggestionIndex - 1, 0);
         return true;
      } else if ((keyCode == 257 || keyCode == 258) && this.selectedSuggestionIndex >= 0 && this.selectedSuggestionIndex < this.suggestions.size()) {
         this.applySuggestion(this.suggestions.get(this.selectedSuggestionIndex));
         return true;
      } else {
         return false;
      }
   }

   private void applySuggestion(String suggestion) {
      if (!suggestion.startsWith("Example:")) {
         String modifier = suggestion.split(" - ")[0].trim();
         String current = this.searchField.getValue();
         if (current.endsWith("+")) {
            this.searchField.setValue(current + modifier + " ");
         }

         this.showHelp = false;
         this.suggestions.clear();
         this.selectedSuggestionIndex = -1;
      }
   }

   private void onSearchChanged(String query) {
      this.currentQuery = query;
      if (query.isEmpty()) {
         this.showHelp = false;
         this.suggestions.clear();
         this.selectedSuggestionIndex = -1;
         this.selectableSuggestions = false;
         this.listener.onQueryCleared();
      } else {
         this.updateSuggestions(query);
         this.listener.onQueryChanged(query, SearchQuery.parse(query));
      }
   }

   private void updateSuggestions(String query) {
      this.suggestions.clear();
      this.selectedSuggestionIndex = -1;
      if (!query.endsWith("+") && (!query.contains("+") || query.contains("+&") || query.contains("+!") || query.contains("+$"))) {
         this.showHelp = true;
         this.selectableSuggestions = false;
         this.suggestions.add("+& - Combine different search terms");
         this.suggestions.add("Example: 'zombie +& skeleton'");
         this.suggestions.add("+! - Show incomplete only");
         this.suggestions.add("Example: '+! zombie' or just '+!'");
         this.suggestions.add("+$ - Show completed only");
         this.suggestions.add("Example: '+$ zombie' or just '+$'");
      } else {
         this.showHelp = true;
         this.selectableSuggestions = true;
         this.selectedSuggestionIndex = 0;
         this.suggestions.add("& - Combine different search terms (OR)");
         this.suggestions.add("! - Show incomplete advancements only");
         this.suggestions.add("$ - Show completed advancements only");
      }
   }

   public void renderDropdown(GuiGraphicsExtractor ctx, Font font) {
      if (this.isShowingHelp()) {
         int dx = this.searchField.getX();
         int dy = this.searchField.getY() + 22;
         int dw = this.searchField.getWidth();
         int lineH = 12;
         int dh = this.suggestions.size() * lineH + 4;
         ctx.fill(dx, dy, dx + dw, dy + dh, -536870912);
         ctx.outline(dx, dy, dw, dh, -12566464);
         int ty = dy + 2;

         for (int i = 0; i < this.suggestions.size(); i++) {
            String s = this.suggestions.get(i);
            if (this.selectableSuggestions && i == this.selectedSuggestionIndex) {
               ctx.fill(dx + 2, ty - 1, dx + dw - 2, ty + lineH - 1, -2130706433);
            }

            ctx.text(font, s, dx + 4, ty, s.startsWith("Example:") ? -7829368 : -1, false);
            ty += lineH;
         }
      }
   }

   public interface SearchListener {
      void onQueryChanged(String var1, SearchQuery var2);

      void onQueryCleared();
   }
}
