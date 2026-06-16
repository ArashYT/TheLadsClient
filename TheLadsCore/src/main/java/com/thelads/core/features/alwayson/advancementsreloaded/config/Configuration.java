package com.thelads.core.features.alwayson.advancementsreloaded.config;

import java.util.List;

public final class Configuration {
    public static boolean displaySidebar = true;
    public static boolean displayDescription = true;
    public static boolean criteriasAlphabeticOrder = true;
    public static AdvancementOrder advancementsOrder = AdvancementOrder.ALPHABETIC;
    public static TabOrder tabsOrder = TabOrder.ALPHABETIC;
    public static BackgroundStyle backgroundStyle = BackgroundStyle.TRANSPARENT;
    public static TranslationMode criteriasTranslationMode = TranslationMode.ONLY_COMPATIBLE;
    public static int headerHeight = 48;
    public static int footerHeight = 48;
    public static int criteriasWidth = 142;
    public static int aboveWidgetLimit = 14;
    public static int belowWidgetLimit = 14;
    public static List<String> customTabsOrder = List.of();
    public static List<String> customAdvancementsOrder = List.of();

    private Configuration() {
        throw new UnsupportedOperationException("Utility class");
    }

    public enum AdvancementOrder {
        NONE,
        ALPHABETIC,
        CONFIGURED_ORDER
    }

    public enum TabOrder {
        NONE,
        ALPHABETIC,
        CONFIGURED_ORDER
    }

    public enum BackgroundStyle {
        TRANSPARENT,
        ACHIEVEMENT,
        BLACK
    }

    public enum TranslationMode {
        NONE,
        ONLY_COMPATIBLE,
        TRY_TO_TRANSLATE
    }
}
