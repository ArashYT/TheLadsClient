package com.thelads.smp.features.arise.nb2;

/**
 * Handles Distraction Free Recipes logic:
 * hides the JEI item overlay until something is typed into the search box.
 */
public final class DistractionFreeRecipesManager {
    private DistractionFreeRecipesManager() {}

    public static boolean isDistractionFreeEnabled() {
        return NB2Config.get().distractionFreeRecipes;
    }

    public static boolean isSearchEmpty(String filterText) {
        return filterText == null || filterText.trim().isEmpty();
    }

    public static boolean shouldHideOverlay(String filterText) {
        return isDistractionFreeEnabled() && isSearchEmpty(filterText);
    }
}
