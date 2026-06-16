/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  me.shedaniel.clothconfig2.api.AbstractConfigListEntry
 *  me.shedaniel.clothconfig2.api.ConfigBuilder
 *  me.shedaniel.clothconfig2.api.ConfigCategory
 *  me.shedaniel.clothconfig2.api.ConfigEntryBuilder
 *  me.shedaniel.clothconfig2.gui.entries.EnumListEntry
 *  me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder
 *  net.minecraft.ChatFormatting
 *  net.minecraft.network.chat.Component
 */
package com.thelads.core.features.alwayson.clientsort.gui.screen.config;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.config.ClassPolicy;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.config.Operation;
import com.thelads.core.features.alwayson.clientsort.config.Vec2i;
import com.thelads.core.features.alwayson.clientsort.order.CreativeSearchOrder;
import com.thelads.core.features.alwayson.clientsort.order.SortOrder;
import com.thelads.core.features.alwayson.clientsort.util.KeybindManager;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.KeyMappingAccessor;
import com.thelads.core.features.alwayson.clientsort.util.Localization;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ClothScreenProvider {
    private static EnumListEntry<?> firstSelector = null;
    private static EnumListEntry<?> secondSelector = null;
    private static EnumListEntry<?> thirdSelector = null;
    private static EnumListEntry<?> fourthSelector = null;

    static Screen getConfigScreen(Screen parent) {
        Config.Options options = Config.options();
        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle((Component)Localization.localized("name", new Object[0])).setSavingRunnable(Config::getAndSave);
        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory((Component)Localization.localized("option", "general", new Object[0]));
        general.addEntry((AbstractConfigListEntry)eb.startIntField((Component)Localization.localized("option", "interactionInterval", new Object[0]), options.interactionInterval).setTooltip(new Component[]{Localization.localized("option", "interactionInterval.tooltip", new Object[0])}).setErrorSupplier(val -> {
            if (val < 1) {
                return Optional.of(Localization.localized("error", "low", new Object[0]));
            }
            if (val > 1000) {
                return Optional.of(Localization.localized("error", "high", new Object[0]));
            }
            return Optional.empty();
        }).setDefaultValue(10).setSaveConsumer(val -> {
            options.interactionInterval = val;
        }).build());
        general.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "useServerAcceleration", new Object[0]), options.useServerAcceleration).setTooltip(new Component[]{Localization.localized("option", "useServerAcceleration.tooltip", new Object[0])}).setDefaultValue(true).setSaveConsumer(val -> {
            options.useServerAcceleration = val;
        }).build());
        general.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "useClientFallback", new Object[0]), options.useClientFallback).setTooltip(new Component[]{Localization.localized("option", "useClientFallback.tooltip", new Object[0])}).setDefaultValue(false).setSaveConsumer(val -> {
            options.useClientFallback = val;
        }).build());
        general.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "optimizeCreativeSorting", new Object[0]), options.optimizeCreativeSorting).setTooltip(new Component[]{Localization.localized("option", "optimizeCreativeSorting.tooltip", new Object[0])}).setDefaultValue(true).setSaveConsumer(val -> {
            options.optimizeCreativeSorting = val;
            if (val.booleanValue()) {
                CreativeSearchOrder.tryRefreshStackPositionMap();
            }
        }).build());
        general.addEntry((AbstractConfigListEntry)eb.startIntField((Component)Localization.localized("option", "autoOpDelayPlayer", new Object[0]), options.autoOpDelayPlayer).setTooltip(new Component[]{Localization.localized("option", "autoOpDelayPlayer.tooltip", new Object[0])}).setErrorSupplier(val -> {
            if (val < 0) {
                return Optional.of(Localization.localized("error", "low", new Object[0]));
            }
            if (val > 40) {
                return Optional.of(Localization.localized("error", "high", new Object[0]));
            }
            return Optional.empty();
        }).setDefaultValue(2).setSaveConsumer(val -> {
            options.autoOpDelayPlayer = val;
        }).build());
        general.addEntry((AbstractConfigListEntry)eb.startIntField((Component)Localization.localized("option", "autoOpDelayContainer", new Object[0]), options.autoOpDelayContainer).setTooltip(new Component[]{Localization.localized("option", "autoOpDelayContainer.tooltip", new Object[0])}).setErrorSupplier(val -> {
            if (val < 0) {
                return Optional.of(Localization.localized("error", "low", new Object[0]));
            }
            if (val > 40) {
                return Optional.of(Localization.localized("error", "high", new Object[0]));
            }
            return Optional.empty();
        }).setDefaultValue(2).setSaveConsumer(val -> {
            options.autoOpDelayContainer = val;
        }).build());
        general.addEntry((AbstractConfigListEntry)eb.startEnumSelector((Component)Localization.localized("option", "hotbarScope", new Object[0]), Config.Options.HotbarScope.class, options.hotbarScope).setEnumNameProvider(val -> Localization.localized("hotbarScope", val.name(), new Object[0])).setTooltipSupplier(val -> Optional.of(new Component[]{Localization.localized("hotbarScope", String.valueOf(val) + ".tooltip", new Object[0])})).setDefaultValue(Config.Options.hotbarScopeDefault).setSaveConsumer(val -> {
            options.hotbarScope = val;
        }).build());
        general.addEntry((AbstractConfigListEntry)eb.startEnumSelector((Component)Localization.localized("option", "extraSlotScope", new Object[0]), Config.Options.ExtraSlotScope.class, options.extraSlotScope).setEnumNameProvider(val -> Localization.localized("extraSlotScope", val.name(), new Object[0])).setTooltipSupplier(val -> Optional.of(new Component[]{Localization.localized("extraSlotScope", String.valueOf(val) + ".tooltip", new Object[0])})).setDefaultValue(Config.Options.extraSlotScopeDefault).setSaveConsumer(val -> {
            options.extraSlotScope = val;
        }).build());
        general.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "bundlesUseRightClick", new Object[0]), options.bundlesUseRightClick).setTooltip(new Component[]{Localization.localized("option", "bundlesUseRightClick.tooltip", new Object[0])}).setDefaultValue(false).setSaveConsumer(val -> {
            options.bundlesUseRightClick = val;
            if (val.booleanValue()) {
                CreativeSearchOrder.tryRefreshStackPositionMap();
            }
        }).build());
        general.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "transferReverseOrder", new Object[0]), options.transferReverseOrder).setTooltip(new Component[]{Localization.localized("option", "transferReverseOrder.tooltip", new Object[0])}).setDefaultValue(true).setSaveConsumer(val -> {
            options.transferReverseOrder = val;
        }).build());
        general.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "showDebugInfo", new Object[0]), ClientSort.debugEnabled).setTooltip(new Component[]{Localization.localized("option", "showDebugInfo.tooltip", new Object[0])}).setDefaultValue(false).setSaveConsumer(val -> {
            ClientSort.debugEnabled = val;
        }).build());
        ConfigCategory sort = builder.getOrCreateCategory((Component)Localization.localized("option", "sorting", new Object[0]));
        sort.addEntry((AbstractConfigListEntry)eb.startSelector((Component)Localization.localized("option", "sortOrder", new Object[0]), SortOrder.SORT_ORDERS.keySet().toArray(), (Object)options.sortOrderStr).setNameProvider(val -> Localization.localized("sortOrder", (String)val, new Object[0])).setDefaultValue((Object)Config.Options.sortOrderStrDefault).setSaveConsumer(val -> {
            options.sortOrderStr = (String)val;
        }).build());
        sort.addEntry((AbstractConfigListEntry)eb.startSelector((Component)Localization.localized("option", "shiftSortOrder", new Object[0]), SortOrder.SORT_ORDERS.keySet().toArray(), (Object)options.shiftSortOrderStr).setNameProvider(val -> Localization.localized("sortOrder", (String)val, new Object[0])).setDefaultValue((Object)Config.Options.shiftSortOrderStrDefault).setSaveConsumer(val -> {
            options.shiftSortOrderStr = (String)val;
        }).build());
        sort.addEntry((AbstractConfigListEntry)eb.startSelector((Component)Localization.localized("option", "ctrlSortOrder", new Object[0]), SortOrder.SORT_ORDERS.keySet().toArray(), (Object)options.ctrlSortOrderStr).setNameProvider(val -> Localization.localized("sortOrder", (String)val, new Object[0])).setDefaultValue((Object)Config.Options.ctrlSortOrderStrDefault).setSaveConsumer(val -> {
            options.ctrlSortOrderStr = (String)val;
        }).build());
        sort.addEntry((AbstractConfigListEntry)eb.startSelector((Component)Localization.localized("option", "altSortOrder", new Object[0]), SortOrder.SORT_ORDERS.keySet().toArray(), (Object)options.altSortOrderStr).setNameProvider(val -> Localization.localized("sortOrder", (String)val, new Object[0])).setDefaultValue((Object)Config.Options.altSortOrderStrDefault).setSaveConsumer(val -> {
            options.altSortOrderStr = (String)val;
        }).build());
        sort.addEntry((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "orderOverrides", new Object[0])).build());
        sort.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "useStartOverrides", new Object[0]), options.useStartOverrides).setTooltip(new Component[]{Localization.localized("option", "useStartOverrides.tooltip", new Object[0])}).setDefaultValue(true).setSaveConsumer(val -> {
            options.useStartOverrides = val;
        }).build());
        sort.addEntry((AbstractConfigListEntry)eb.startStrList((Component)Localization.localized("option", "startOverrideItems", new Object[0]), options.startOverrideItems).setTooltip(new Component[]{Localization.localized("option", "startOverrideItems.tooltip", new Object[0]).append("\n").append((Component)Localization.localized("option", "overrideItems.tooltip.2", Localization.localized("option", "overrideItems.tooltip.2.item", new Object[0]).withStyle(ChatFormatting.GOLD)))}).setDefaultValue(Config.Options.startOverrideItemsDefault.get()).setSaveConsumer(val -> {
            options.startOverrideItems = val.stream().map(String::strip).filter(s -> !s.isBlank()).toList();
        }).setInsertInFront(true).setExpanded(options.useStartOverrides).build());
        sort.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "useEndOverrides", new Object[0]), options.useEndOverrides).setTooltip(new Component[]{Localization.localized("option", "useEndOverrides.tooltip", new Object[0])}).setDefaultValue(true).setSaveConsumer(val -> {
            options.useEndOverrides = val;
        }).build());
        sort.addEntry((AbstractConfigListEntry)eb.startStrList((Component)Localization.localized("option", "endOverrideItems", new Object[0]), options.endOverrideItems).setTooltip(new Component[]{Localization.localized("option", "endOverrideItems.tooltip", new Object[0]).append("\n").append((Component)Localization.localized("option", "overrideItems.tooltip.2", Localization.localized("option", "overrideItems.tooltip.2.item", new Object[0]).withStyle(ChatFormatting.GOLD)))}).setDefaultValue(Config.Options.endOverrideItemsDefault.get()).setSaveConsumer(val -> {
            options.endOverrideItems = val.stream().map(String::strip).filter(s -> !s.isBlank()).toList();
        }).setInsertInFront(true).setExpanded(options.useEndOverrides).build());
        ConfigCategory matching = builder.getOrCreateCategory((Component)Localization.localized("option", "matching", new Object[0]));
        matching.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "alwaysMatchByType", new Object[0]), options.alwaysMatchByType).setTooltip(new Component[]{Localization.localized("option", "alwaysMatchByType.tooltip", new Object[0])}).setDefaultValue(false).setSaveConsumer(val -> {
            options.alwaysMatchByType = val;
        }).build());
        matching.addEntry((AbstractConfigListEntry)eb.startStrList((Component)Localization.localized("option", "typeMatchTags", new Object[0]), options.typeMatchTags).setTooltip(new Component[]{Localization.localized("option", "typeMatchTags.tooltip.1", new Object[0]).append("\n").append((Component)Localization.localized("option", "typeMatchTags.tooltip.2", new Object[0])).append("\n").append((Component)Localization.localized("option", "typeMatchTags.tooltip.3", Component.literal((String)"https://minecraft.wiki/w/Item_tag").withStyle(ChatFormatting.GOLD)))}).setDefaultValue(Config.Options.typeMatchTagsDefault.get()).setSaveConsumer(val -> {
            options.typeMatchTags = val.stream().map(String::strip).filter(s -> !s.isBlank()).toList();
        }).setInsertInFront(true).setExpanded(!options.alwaysMatchByType).build());
        ConfigCategory sounds = builder.getOrCreateCategory((Component)Localization.localized("option", "sounds", new Object[0]));
        sounds.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "playSoundSort", new Object[0]), options.playSoundSort).setTooltip(new Component[]{Localization.localized("option", "playSoundSort.tooltip", new Object[0])}).setDefaultValue(false).setSaveConsumer(val -> {
            options.playSoundSort = val;
        }).build());
        sounds.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "playSoundOther", new Object[0]), options.playSoundOther).setTooltip(new Component[]{Localization.localized("option", "playSoundOther.tooltip", new Object[0])}).setDefaultValue(false).setSaveConsumer(val -> {
            options.playSoundOther = val;
        }).build());
        sounds.addEntry((AbstractConfigListEntry)eb.startStrField((Component)Localization.localized("option", "interactionSound", new Object[0]), options.interactionSound).setDefaultValue("minecraft:block.note_block.xylophone").setSaveConsumer(val -> {
            options.interactionSound = val;
        }).setErrorSupplier(val -> {
            if (Identifier.tryParse(val) == null) {
                return Optional.of(Localization.localized("error", "Identifier.parse", new Object[0]));
            }
            return Optional.empty();
        }).build());
        sounds.addEntry((AbstractConfigListEntry)eb.startIntField((Component)Localization.localized("option", "soundInterval", new Object[0]), options.soundInterval).setTooltip(new Component[]{Localization.localized("option", "soundInterval.tooltip", new Object[0])}).setErrorSupplier(val -> {
            if (val < 1) {
                return Optional.of(Localization.localized("error", "low", new Object[0]));
            }
            if (val > 100) {
                return Optional.of(Localization.localized("error", "high", new Object[0]));
            }
            return Optional.empty();
        }).setDefaultValue(1).setSaveConsumer(val -> {
            options.soundInterval = val;
        }).build());
        sounds.addEntry((AbstractConfigListEntry)eb.startFloatField((Component)Localization.localized("option", "soundPitchMin", new Object[0]), options.soundPitchMin).setTooltip(new Component[]{Localization.localized("option", "soundPitchMin.tooltip", new Object[0])}).setErrorSupplier(val -> {
            if (val.floatValue() < 0.5f) {
                return Optional.of(Localization.localized("error", "low", new Object[0]));
            }
            if (val.floatValue() > options.soundPitchMax) {
                return Optional.of(Localization.localized("error", "high", new Object[0]));
            }
            return Optional.empty();
        }).setDefaultValue(0.5f).setSaveConsumer(val -> {
            options.soundPitchMin = val.floatValue();
        }).build());
        sounds.addEntry((AbstractConfigListEntry)eb.startFloatField((Component)Localization.localized("option", "soundPitchMax", new Object[0]), options.soundPitchMax).setTooltip(new Component[]{Localization.localized("option", "soundPitchMax.tooltip", new Object[0])}).setErrorSupplier(val -> {
            if (val.floatValue() < options.soundPitchMin) {
                return Optional.of(Localization.localized("error", "low", new Object[0]));
            }
            if (val.floatValue() > 2.0f) {
                return Optional.of(Localization.localized("error", "high", new Object[0]));
            }
            return Optional.empty();
        }).setDefaultValue(2.0f).setSaveConsumer(val -> {
            options.soundPitchMax = val.floatValue();
        }).build());
        sounds.addEntry((AbstractConfigListEntry)eb.startFloatField((Component)Localization.localized("option", "soundVolume", new Object[0]), options.soundVolume).setErrorSupplier(val -> {
            if (val.floatValue() < 0.0f) {
                return Optional.of(Localization.localized("error", "low", new Object[0]));
            }
            if (val.floatValue() > 1.0f) {
                return Optional.of(Localization.localized("error", "high", new Object[0]));
            }
            return Optional.empty();
        }).setDefaultValue(0.2f).setSaveConsumer(val -> {
            options.soundVolume = val.floatValue();
        }).build());
        sounds.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "allowSoundOverlap", new Object[0]), options.allowSoundOverlap).setTooltip(new Component[]{Localization.localized("option", "allowSoundOverlap.tooltip", new Object[0])}).setDefaultValue(true).setSaveConsumer(val -> {
            options.allowSoundOverlap = val;
        }).build());
        ConfigCategory keybinds = builder.getOrCreateCategory((Component)Localization.localized("option", "keybinds", new Object[0]));
        keybinds.addEntry((AbstractConfigListEntry)eb.startKeyCodeField((Component)Localization.localized("key", "edit", new Object[0]), ((KeyMappingAccessor)KeybindManager.EDIT_KEY).clientsort$getKey()).setDefaultValue(KeybindManager.EDIT_KEY.getDefaultKey()).setKeySaveConsumer(key -> KeybindManager.bindKey(KeybindManager.EDIT_KEY, key)).build());
        keybinds.addEntry((AbstractConfigListEntry)eb.startKeyCodeField((Component)Localization.localized("key", "cancelAuto", new Object[0]), ((KeyMappingAccessor)KeybindManager.CANCEL_AUTO_KEY).clientsort$getKey()).setDefaultValue(KeybindManager.CANCEL_AUTO_KEY.getDefaultKey()).setKeySaveConsumer(key -> KeybindManager.bindKey(KeybindManager.CANCEL_AUTO_KEY, key)).build());
        keybinds.addEntry((AbstractConfigListEntry)eb.startKeyCodeField((Component)Localization.localized("key", "op.sort", new Object[0]), ((KeyMappingAccessor)KeybindManager.SORT_KEY).clientsort$getKey()).setDefaultValue(KeybindManager.SORT_KEY.getDefaultKey()).setKeySaveConsumer(key -> KeybindManager.bindKey(KeybindManager.SORT_KEY, key)).build());
        keybinds.addEntry((AbstractConfigListEntry)eb.startKeyCodeField((Component)Localization.localized("key", "op.stackFill", new Object[0]), ((KeyMappingAccessor)KeybindManager.STACK_FILL_KEY).clientsort$getKey()).setDefaultValue(KeybindManager.STACK_FILL_KEY.getDefaultKey()).setKeySaveConsumer(key -> KeybindManager.bindKey(KeybindManager.STACK_FILL_KEY, key)).build());
        keybinds.addEntry((AbstractConfigListEntry)eb.startKeyCodeField((Component)Localization.localized("key", "op.matchTransfer", new Object[0]), ((KeyMappingAccessor)KeybindManager.MATCH_TRANSFER_KEY).clientsort$getKey()).setDefaultValue(KeybindManager.MATCH_TRANSFER_KEY.getDefaultKey()).setKeySaveConsumer(key -> KeybindManager.bindKey(KeybindManager.MATCH_TRANSFER_KEY, key)).build());
        keybinds.addEntry((AbstractConfigListEntry)eb.startKeyCodeField((Component)Localization.localized("key", "op.transfer", new Object[0]), ((KeyMappingAccessor)KeybindManager.TRANSFER_KEY).clientsort$getKey()).setDefaultValue(KeybindManager.TRANSFER_KEY.getDefaultKey()).setKeySaveConsumer(key -> KeybindManager.bindKey(KeybindManager.TRANSFER_KEY, key)).build());
        ConfigCategory buttons = builder.getOrCreateCategory((Component)Localization.localized("option", "buttons", new Object[0]));
        buttons.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "showButtons", new Object[0]), options.showButtons).setTooltip(new Component[]{Localization.localized("option", "showButtons.tooltip", new Object[0])}).setDefaultValue(true).setSaveConsumer(val -> {
            options.showButtons = val;
        }).build());
        buttons.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "showButtonTooltips", new Object[0]), options.showButtonTooltips).setTooltip(new Component[]{Localization.localized("option", "showButtonTooltips.tooltip", new Object[0])}).setDefaultValue(false).setSaveConsumer(val -> {
            options.showButtonTooltips = val;
        }).build());
        buttons.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "anchorButtonsLeft", new Object[0]), options.anchorButtonsLeft).setTooltip(new Component[]{Localization.localized("option", "anchorButtonsLeft.tooltip", new Object[0])}).setDefaultValue(false).setSaveConsumer(val -> {
            options.anchorButtonsLeft = val;
        }).build());
        buttons.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "justifyButtonsTopLeft", new Object[0]), options.justifyButtonsTopLeft).setTooltip(new Component[]{Localization.localized("option", "justifyButtonsTopLeft.tooltip", new Object[0])}).setDefaultValue(true).setSaveConsumer(val -> {
            options.justifyButtonsTopLeft = val;
        }).build());
        buttons.addEntry((AbstractConfigListEntry)eb.startBooleanToggle((Component)Localization.localized("option", "buttonsHorizontal", new Object[0]), options.buttonsHorizontal).setTooltip(new Component[]{Localization.localized("option", "buttonsHorizontal.tooltip", new Object[0])}).setDefaultValue(false).setSaveConsumer(val -> {
            options.buttonsHorizontal = val;
        }).build());
        firstSelector = eb.startEnumSelector((Component)Localization.localized("option", "firstButtonOp", new Object[0]), Operation.class, options.firstButtonOp).setErrorSupplier(val -> val.equals((Object)ClothScreenProvider.getSecondSelector()) || val.equals((Object)ClothScreenProvider.getThirdSelector()) || val.equals((Object)ClothScreenProvider.getFourthSelector()) ? Optional.of(Localization.localized("error", "triggerButton.duplicate", new Object[0])) : Optional.empty()).setEnumNameProvider(val -> Localization.localized("triggerButton", val.name(), new Object[0])).setDefaultValue(Config.Options.firstButtonOpDefault).setSaveConsumer(val -> {
            options.firstButtonOp = val;
        }).build();
        buttons.addEntry(firstSelector);
        secondSelector = eb.startEnumSelector((Component)Localization.localized("option", "secondButtonOp", new Object[0]), Operation.class, options.secondButtonOp).setEnumNameProvider(val -> Localization.localized("triggerButton", val.name(), new Object[0])).setErrorSupplier(val -> val.equals((Object)ClothScreenProvider.getFirstSelector()) || val.equals((Object)ClothScreenProvider.getThirdSelector()) || val.equals((Object)ClothScreenProvider.getFourthSelector()) ? Optional.of(Localization.localized("error", "triggerButton.duplicate", new Object[0])) : Optional.empty()).setDefaultValue(Config.Options.secondButtonOpDefault).setSaveConsumer(val -> {
            options.secondButtonOp = val;
        }).build();
        buttons.addEntry(secondSelector);
        thirdSelector = eb.startEnumSelector((Component)Localization.localized("option", "thirdButtonOp", new Object[0]), Operation.class, options.thirdButtonOp).setEnumNameProvider(val -> Localization.localized("triggerButton", val.name(), new Object[0])).setErrorSupplier(val -> val.equals((Object)ClothScreenProvider.getFirstSelector()) || val.equals((Object)ClothScreenProvider.getSecondSelector()) || val.equals((Object)ClothScreenProvider.getFourthSelector()) ? Optional.of(Localization.localized("error", "triggerButton.duplicate", new Object[0])) : Optional.empty()).setDefaultValue(Config.Options.thirdButtonOpDefault).setSaveConsumer(val -> {
            options.thirdButtonOp = val;
        }).build();
        buttons.addEntry(thirdSelector);
        fourthSelector = eb.startEnumSelector((Component)Localization.localized("option", "fourthButtonOp", new Object[0]), Operation.class, options.fourthButtonOp).setEnumNameProvider(val -> Localization.localized("triggerButton", val.name(), new Object[0])).setErrorSupplier(val -> val.equals((Object)ClothScreenProvider.getFirstSelector()) || val.equals((Object)ClothScreenProvider.getSecondSelector()) || val.equals((Object)ClothScreenProvider.getThirdSelector()) ? Optional.of(Localization.localized("error", "triggerButton.duplicate", new Object[0])) : Optional.empty()).setDefaultValue(Config.Options.fourthButtonOpDefault).setSaveConsumer(val -> {
            options.fourthButtonOp = val;
        }).build();
        buttons.addEntry(fourthSelector);
        SubCategoryBuilder layoutDefaults = eb.startSubCategory((Component)Localization.localized("option", "layoutDefaults", new Object[0])).setTooltip(new Component[]{Localization.localized("option", "layoutDefaults.tooltip", new Object[0])}).setExpanded(true);
        layoutDefaults.add((AbstractConfigListEntry)eb.startIntField((Component)Localization.localized("option", "layoutOffset.x", new Object[0]), options.layoutOffset.x()).setDefaultValue(Config.Options.layoutOffsetDefault.x()).setSaveConsumer(val -> {
            options.layoutOffset = new Vec2i((int)val, options.layoutOffset.y());
        }).build());
        layoutDefaults.add((AbstractConfigListEntry)eb.startIntField((Component)Localization.localized("option", "layoutOffset.y", new Object[0]), options.layoutOffset.y()).setDefaultValue(Config.Options.layoutOffsetDefault.y()).setSaveConsumer(val -> {
            options.layoutOffset = new Vec2i(options.layoutOffset.x(), (int)val);
        }).build());
        buttons.addEntry((AbstractConfigListEntry)layoutDefaults.build());
        ConfigCategory policies = builder.getOrCreateCategory((Component)Localization.localized("option", "policies", new Object[0]));
        SubCategoryBuilder policiesInstructions = eb.startSubCategory((Component)Localization.localized("option", "policies.instructions", new Object[0]));
        policiesInstructions.add((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "policies.description.1", new Object[0])).build());
        policiesInstructions.add((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "policies.description.2", new Object[0])).build());
        policiesInstructions.add((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "policies.description.3", new Object[0])).build());
        policiesInstructions.add((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "policies.description.4", new Object[0])).build());
        policiesInstructions.add((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "policies.description.5", new Object[0])).build());
        policiesInstructions.add((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "policies.description.6", new Object[0])).build());
        policiesInstructions.add((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "policies.description.7", new Object[0])).build());
        policiesInstructions.add((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "policies.description.8", new Object[0])).build());
        policiesInstructions.add((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "policies.description.9", new Object[0])).build());
        policiesInstructions.add((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "policies.description.10", new Object[0])).build());
        policiesInstructions.add((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "policies.description.11", new Object[0])).build());
        policiesInstructions.add((AbstractConfigListEntry)eb.startTextDescription((Component)Localization.localized("option", "policies.description.12", new Object[0])).build());
        policies.addEntry((AbstractConfigListEntry)policiesInstructions.build());
        policies.addEntry((AbstractConfigListEntry)eb.startStrList((Component)Localization.localized("option", "classPolicies", new Object[0]), ClothScreenProvider.getPolicyStrings(options.classPolicies.values())).setTooltip(new Component[]{Localization.localized("option", "classPolicies.tooltip", new Object[0])}).setExpanded(true).setErrorSupplier(list -> {
            int i = 0;
            for (String string : list) {
                try {
                    ClassPolicy.fromDataString(string, options.classPolicies.keySet());
                }
                catch (ParseException ex) {
                    return Optional.of(Localization.localized("error", "classPolicy.parse", i + 1, ex.getMessage()));
                }
                ++i;
            }
            return Optional.empty();
        }).setDefaultValue(ClothScreenProvider.getPolicyStrings((Collection<ClassPolicy>)Config.Options.classPoliciesDefaultList.get())).setSaveConsumer(list -> {
            HashSet<ClassPolicy> classPolicies = new HashSet<ClassPolicy>();
            for (String string : list) {
                try {
                    ClassPolicy policy2 = ClassPolicy.fromDataString(string, options.classPolicies.keySet());
                    classPolicies.add(policy2);
                }
                catch (ParseException ex) {
                    com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.error("Encountered a class policy parsing error on string '{}' not caught by error checker: {}", string, ex.getMessage());
                }
            }
            options.classPolicies.clear();
            classPolicies.forEach(policy -> options.classPolicies.put(policy.getKey(), (ClassPolicy)policy));
        }).build());
        return builder.build();
    }

    private static List<String> getPolicyStrings(Collection<ClassPolicy> policies) {
        ArrayList<String> strings = new ArrayList<String>();
        for (ClassPolicy policy : policies) {
            strings.add(policy.toDataString());
        }
        return strings;
    }

    private static Operation getFirstSelector() {
        return firstSelector == null ? null : (Operation)((Object)firstSelector.getValue());
    }

    private static Operation getSecondSelector() {
        return secondSelector == null ? null : (Operation)((Object)secondSelector.getValue());
    }

    private static Operation getThirdSelector() {
        return thirdSelector == null ? null : (Operation)((Object)thirdSelector.getValue());
    }

    private static Operation getFourthSelector() {
        return fourthSelector == null ? null : (Operation)((Object)fourthSelector.getValue());
    }
}
