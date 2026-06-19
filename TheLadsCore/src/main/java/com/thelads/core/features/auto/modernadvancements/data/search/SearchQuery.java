package com.thelads.core.features.auto.modernadvancements.data.search;

import java.util.Locale;

public record SearchQuery(String term, boolean onlyIncomplete, boolean onlyComplete) {
   public static SearchQuery parse(String raw) {
      String lower = raw.toLowerCase(Locale.ROOT);
      return new SearchQuery(lower.replace("+!", "").replace("+$", "").replace("+&", "|SPLIT|").trim(), lower.contains("+!"), lower.contains("+$"));
   }

   public boolean isMultiTerm() {
      return this.term.contains("|SPLIT|");
   }

   public String[] splitTerms() {
      return this.term.split("\\|SPLIT\\|");
   }
}
