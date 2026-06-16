package com.thelads.core.features.alwayson.advancementsreloaded.config.gui;

import com.thelads.core.features.alwayson.advancementsreloaded.AdvancementTreeRecalculator;
import com.thelads.core.features.alwayson.advancementsreloaded.config.Configuration;
import com.thelads.core.features.alwayson.advancementsreloaded.config.ModConfigurationFile;
import java.util.ArrayList;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ConfigurationScreen {
    private ConfigurationScreen() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Screen screen(Screen parent) {
        return ConfigurationScreen.configBuilder(parent).build();
    }

    public static ConfigBuilder configBuilder(Screen parent) {
        Configuration.AdvancementOrder originalAdvancementsOrder = Configuration.advancementsOrder;
        ArrayList<String> originalCustomAdvancementsOrder = new ArrayList<>(Configuration.customAdvancementsOrder);
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTransparentBackground(true)
            .setTitle(Component.translatable("text.config.advancements_reloaded.title"))
            .setSavingRunnable(() -> {
                boolean customOrderChanged;
                ModConfigurationFile.saveRunnable.run();
                boolean orderModeChanged = originalAdvancementsOrder != Configuration.advancementsOrder;
                customOrderChanged = !originalCustomAdvancementsOrder.equals(Configuration.customAdvancementsOrder);
                if (orderModeChanged || (customOrderChanged && Configuration.advancementsOrder == Configuration.AdvancementOrder.CONFIGURED_ORDER)) {
                    AdvancementTreeRecalculator.recalculateAll();
                }
            });
        ConfigurationScreen.createApparanceEntries(builder);
        ConfigurationScreen.createAdvancedCustomizationEntries(builder);
        return builder;
    }

    private static void createApparanceEntries(ConfigBuilder builder) {
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory appearance = builder.getOrCreateCategory(Component.translatable("text.config.advancements_reloaded.section.appearance"));
        
        appearance.addEntry(entryBuilder.startBooleanToggle(Component.translatable("text.config.advancements_reloaded.option.display_sidebar"), Configuration.displaySidebar)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("text.config.advancements_reloaded.option.display_sidebar.tooltip"))
            .setSaveConsumer(newValue -> Configuration.displaySidebar = newValue)
            .build());
            
        appearance.addEntry(entryBuilder.startBooleanToggle(Component.translatable("text.config.advancements_reloaded.option.display_description"), Configuration.displayDescription)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("text.config.advancements_reloaded.option.display_description.tooltip"))
            .setSaveConsumer(newValue -> Configuration.displayDescription = newValue)
            .build());
            
        appearance.addEntry(entryBuilder.startBooleanToggle(Component.translatable("text.config.advancements_reloaded.option.criterias_alphabetic_order"), Configuration.criteriasAlphabeticOrder)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("text.config.advancements_reloaded.option.criterias_alphabetic_order.tooltip"))
            .setSaveConsumer(newValue -> Configuration.criteriasAlphabeticOrder = newValue)
            .build());
            
        appearance.addEntry(entryBuilder.startEnumSelector(Component.translatable("text.config.advancements_reloaded.option.advancements_order"), Configuration.AdvancementOrder.class, Configuration.advancementsOrder)
            .setDefaultValue(Configuration.AdvancementOrder.ALPHABETIC)
            .setTooltip(Component.translatable("text.config.advancements_reloaded.option.advancements_order.tooltip"))
            .setSaveConsumer(newValue -> Configuration.advancementsOrder = newValue)
            .build());
            
        appearance.addEntry(entryBuilder.startEnumSelector(Component.translatable("text.config.advancements_reloaded.option.tabs_order"), Configuration.TabOrder.class, Configuration.tabsOrder)
            .setDefaultValue(Configuration.TabOrder.ALPHABETIC)
            .setTooltip(Component.translatable("text.config.advancements_reloaded.option.tabs_order.tooltip"))
            .setSaveConsumer(newValue -> Configuration.tabsOrder = newValue)
            .build());
            
        appearance.addEntry(entryBuilder.startEnumSelector(Component.translatable("text.config.advancements_reloaded.option.background_style"), Configuration.BackgroundStyle.class, Configuration.backgroundStyle)
            .setDefaultValue(Configuration.BackgroundStyle.TRANSPARENT)
            .setTooltip(Component.translatable("text.config.advancements_reloaded.option.background_style.tooltip"))
            .setSaveConsumer(newValue -> Configuration.backgroundStyle = newValue)
            .build());
            
        appearance.addEntry(entryBuilder.startEnumSelector(Component.translatable("text.config.advancements_reloaded.option.criterias_translation_mode"), Configuration.TranslationMode.class, Configuration.criteriasTranslationMode)
            .setDefaultValue(Configuration.TranslationMode.ONLY_COMPATIBLE)
            .setTooltip(Component.translatable("text.config.advancements_reloaded.option.criterias_translation_mode.tooltip"))
            .setSaveConsumer(newValue -> Configuration.criteriasTranslationMode = newValue)
            .build());
    }

    private static void createAdvancedCustomizationEntries(ConfigBuilder builder) {
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory advancedCustomization = builder.getOrCreateCategory(Component.translatable("text.config.advancements_reloaded.section.advanced_customization"));
        
        advancedCustomization.addEntry(entryBuilder.startIntSlider(Component.translatable("text.config.advancements_reloaded.option.header_height"), Configuration.headerHeight, 42, 128)
            .setDefaultValue(48)
            .setTooltip(Component.translatable("text.config.advancements_reloaded.option.header_height.tooltip"))
            .setSaveConsumer(newValue -> Configuration.headerHeight = newValue)
            .build());
            
        advancedCustomization.addEntry(entryBuilder.startIntSlider(Component.translatable("text.config.advancements_reloaded.option.footer_height"), Configuration.footerHeight, 42, 128)
            .setDefaultValue(48)
            .setTooltip(Component.translatable("text.config.advancements_reloaded.option.footer_height.tooltip"))
            .setSaveConsumer(newValue -> Configuration.footerHeight = newValue)
            .build());
            
        advancedCustomization.addEntry(entryBuilder.startIntSlider(Component.translatable("text.config.advancements_reloaded.option.criterias_width"), Configuration.criteriasWidth, 50, 512)
            .setDefaultValue(142)
            .setTooltip(Component.translatable("text.config.advancements_reloaded.option.criterias_width.tooltip"))
            .setSaveConsumer(newValue -> Configuration.criteriasWidth = newValue)
            .build());
            
        advancedCustomization.addEntry(entryBuilder.startIntSlider(Component.translatable("text.config.advancements_reloaded.option.above_widget_limit"), Configuration.aboveWidgetLimit, 0, 42)
            .setDefaultValue(14)
            .setTooltip(Component.translatable("text.config.advancements_reloaded.option.above_widget_limit.tooltip"))
            .setSaveConsumer(newValue -> Configuration.aboveWidgetLimit = newValue)
            .build());
            
        advancedCustomization.addEntry(entryBuilder.startIntSlider(Component.translatable("text.config.advancements_reloaded.option.below_widget_limit"), Configuration.belowWidgetLimit, 0, 42)
            .setDefaultValue(14)
            .setTooltip(Component.translatable("text.config.advancements_reloaded.option.below_widget_limit.tooltip"))
            .setSaveConsumer(newValue -> Configuration.belowWidgetLimit = newValue)
            .build());
    }
}
