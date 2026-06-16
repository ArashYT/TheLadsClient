package com.thelads.core.features.alwayson.advancementsreloaded.utils;

import com.thelads.core.features.alwayson.advancementsreloaded.screens.AdvancementReloadedWidget;
import org.jetbrains.annotations.Nullable;

public final class Memory {
    private static AdvancementReloadedWidget currentWidget;
    private static String currentTabId;

    private Memory() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void setWidget(AdvancementReloadedWidget widget) {
        currentWidget = widget;
    }

    public static AdvancementReloadedWidget getWidget() {
        return currentWidget;
    }

    public static void setTabId(@Nullable String tabId) {
        currentTabId = tabId;
    }

    @Nullable
    public static String getTabId() {
        return currentTabId;
    }

    public static void clearTabId() {
        currentTabId = null;
    }
}
