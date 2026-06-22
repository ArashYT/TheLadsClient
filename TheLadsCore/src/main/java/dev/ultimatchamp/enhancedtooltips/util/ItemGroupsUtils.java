/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.util.Tuple
 *  net.minecraft.world.item.CreativeModeTab
 *  net.minecraft.world.item.CreativeModeTabs
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.util;

import dev.ultimatchamp.enhancedtooltips.util.BadgesUtils;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ItemGroupsUtils {
    private static final Map<ResourceKey<@NotNull CreativeModeTab>, Integer> VANILLA_GROUP_COLORS = new LinkedHashMap<ResourceKey<CreativeModeTab>, Integer>();
    public static Map<CreativeModeTab, Collection<ItemStack>> tabs = new LinkedHashMap<CreativeModeTab, Collection<ItemStack>>();
    public static final List<String> ITEM_GROUP_KEYS = List.of("itemGroup.combat", "itemGroup.tools", "itemGroup.spawnEggs", "itemGroup.op", "itemGroup.foodAndDrink", "itemGroup.redstone", "itemGroup.ingredients", "itemGroup.coloredBlocks", "itemGroup.functional", "itemGroup.natural", "itemGroup.buildingBlocks");

    /*
     * Issues handling annotations - annotations may be inaccurate
     */
    @NotNull
    public static Map<Collection<Item>, Tuple<Component, Integer>> getItemGroups() {
        LinkedHashMap<Collection<Item>, Tuple<Component, Integer>> resultGroups = new LinkedHashMap<Collection<Item>, Tuple<Component, Integer>>();
        Map<CreativeModeTab, Collection<ItemStack>> collectedGroups = tabs;
        for (Map.Entry<CreativeModeTab, Collection<ItemStack>> entry : collectedGroups.entrySet()) {
            int fillColor;
            Component text;
            CreativeModeTab group = entry.getKey();
            @NotNull Optional groupKeyOpt = BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(group);
            if (groupKeyOpt.isPresent() && VANILLA_GROUP_COLORS.containsKey(groupKeyOpt.get())) {
                text = group.getDisplayName();
                fillColor = VANILLA_GROUP_COLORS.get(groupKeyOpt.get());
            } else {
                Identifier groupId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(group);
                if (groupId != null) {
                    String namespace = groupId.getNamespace();
                    text = Component.literal((String)BadgesUtils.getMods().getOrDefault(namespace, ""));
                    fillColor = BadgesUtils.getColorFromModName(namespace);
                } else {
                    text = group.getDisplayName();
                    fillColor = BadgesUtils.getColorFromModName(text.getString());
                }
            }
            Collection items = entry.getValue().stream().map(ItemStack::getItem).collect(Collectors.toList());
            resultGroups.put(items, (Tuple<Component, Integer>)new Tuple((Object)text, (Object)fillColor));
        }
        return resultGroups;
    }

    static {
        VANILLA_GROUP_COLORS.put((ResourceKey<CreativeModeTab>)CreativeModeTabs.COMBAT, -442044);
        VANILLA_GROUP_COLORS.put((ResourceKey<CreativeModeTab>)CreativeModeTabs.TOOLS_AND_UTILITIES, -6596170);
        VANILLA_GROUP_COLORS.put((ResourceKey<CreativeModeTab>)CreativeModeTabs.SPAWN_EGGS, -6120450);
        VANILLA_GROUP_COLORS.put((ResourceKey<CreativeModeTab>)CreativeModeTabs.OP_BLOCKS, -6518344);
        VANILLA_GROUP_COLORS.put((ResourceKey<CreativeModeTab>)CreativeModeTabs.FOOD_AND_DRINKS, -10373304);
        VANILLA_GROUP_COLORS.put((ResourceKey<CreativeModeTab>)CreativeModeTabs.REDSTONE_BLOCKS, -38037);
        VANILLA_GROUP_COLORS.put((ResourceKey<CreativeModeTab>)CreativeModeTabs.INGREDIENTS, -40121);
        VANILLA_GROUP_COLORS.put((ResourceKey<CreativeModeTab>)CreativeModeTabs.COLORED_BLOCKS, -12409355);
        VANILLA_GROUP_COLORS.put((ResourceKey<CreativeModeTab>)CreativeModeTabs.FUNCTIONAL_BLOCKS, -13984369);
        VANILLA_GROUP_COLORS.put((ResourceKey<CreativeModeTab>)CreativeModeTabs.NATURAL_BLOCKS, -10044566);
        VANILLA_GROUP_COLORS.put((ResourceKey<CreativeModeTab>)CreativeModeTabs.BUILDING_BLOCKS, -865972);
    }
}

