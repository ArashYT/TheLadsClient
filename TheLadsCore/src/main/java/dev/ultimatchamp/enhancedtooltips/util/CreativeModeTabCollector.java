/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
 *  net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents$ModifyOutput
 *  net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents$ModifyOutputAll
 *  net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.world.flag.FeatureFlagSet
 *  net.minecraft.world.flag.FeatureFlags
 *  net.minecraft.world.item.CreativeModeTab
 *  net.minecraft.world.item.CreativeModeTab$ItemDisplayBuilder
 *  net.minecraft.world.item.CreativeModeTab$ItemDisplayParameters
 *  net.minecraft.world.item.CreativeModeTab$Output
 *  net.minecraft.world.item.CreativeModeTab$Type
 *  net.minecraft.world.item.CreativeModeTabs
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.Level
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.util;

import dev.ultimatchamp.enhancedtooltips.EnhancedTooltips;
import dev.ultimatchamp.enhancedtooltips.mixin.accessors.CreativeModeTabAccessor;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class CreativeModeTabCollector {
    /*
     * Issues handling annotations - annotations may be inaccurate
     */
    public static Map<CreativeModeTab, Collection<ItemStack>> collectTabs(@NotNull Level world) {
        LinkedHashMap<CreativeModeTab, Collection<ItemStack>> map = new LinkedHashMap<CreativeModeTab, Collection<ItemStack>>();
        FeatureFlagSet featureFlags = FeatureFlags.REGISTRY.allFlags();
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(featureFlags, true, (HolderLookup.Provider)world.registryAccess());
        for (CreativeModeTab group : CreativeModeTabs.allTabs()) {
            if (group.getType() == CreativeModeTab.Type.HOTBAR || group.getType() == CreativeModeTab.Type.INVENTORY) continue;
            try {
                CreativeModeTab.ItemDisplayBuilder builder = new CreativeModeTab.ItemDisplayBuilder(group, featureFlags);
                @NotNull ResourceKey resourceKey = (ResourceKey)BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(group).orElseThrow(() -> new IllegalStateException("Unregistered creative tab: " + String.valueOf(group)));
                ((CreativeModeTabAccessor)group).getDisplayItemsGenerator().accept(parameters, (CreativeModeTab.Output)builder);
                map.put(group, CreativeModeTabCollector.postFabricEvents(group, parameters, (ResourceKey<CreativeModeTab>)resourceKey, builder.tabContents));
            }
            catch (Throwable throwable) {
                EnhancedTooltips.LOGGER.error("Failed to collect creative tab: {}", (Object)group, (Object)throwable);
            }
        }
        return map;
    }

    private static Collection<ItemStack> postFabricEvents(CreativeModeTab group, CreativeModeTab.ItemDisplayParameters context, ResourceKey<@NotNull CreativeModeTab> resourceKey, Collection<ItemStack> tabContents) {
        try {
            FabricCreativeModeTabOutput entries = new FabricCreativeModeTabOutput(context, new LinkedList<ItemStack>(tabContents), new LinkedList());
            ((CreativeModeTabEvents.ModifyOutput)CreativeModeTabEvents.modifyOutputEvent(resourceKey).invoker()).modifyOutput(entries);
            ((CreativeModeTabEvents.ModifyOutputAll)CreativeModeTabEvents.MODIFY_OUTPUT_ALL.invoker()).modifyOutput(group, entries);
            return entries.getDisplayStacks();
        }
        catch (Throwable throwable) {
            EnhancedTooltips.LOGGER.error("Failed to collect fabric's creative group: {}", (Object)group, (Object)throwable);
            return tabContents;
        }
    }
}

