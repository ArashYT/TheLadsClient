/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.loader.api.FabricLoader
 *  net.fabricmc.loader.api.ModContainer
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.util.Tuple
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.util;

import dev.ultimatchamp.enhancedtooltips.util.ItemGroupsUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class BadgesUtils {
    private static Map<String, String> mods = new HashMap<String, String>();

    @NotNull
    public static Tuple<Component, Integer> getBadgeText(ItemStack stack) {
        Component text = Component.empty();
        int fillColor = 0;
        for (Map.Entry<Collection<Item>, Tuple<Component, Integer>> entry : ItemGroupsUtils.getItemGroups().entrySet()) {
            if (!entry.getKey().contains(stack.getItem())) continue;
            text = entry.getValue().getA();
            fillColor = entry.getValue().getB();
            break;
        }
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        Component finalText = text;
        if (text.toFlatList().isEmpty() || !namespace.equals("minecraft") && ItemGroupsUtils.ITEM_GROUP_KEYS.stream().map(key -> Component.translatable((String)key).getString()).anyMatch(arg_0 -> arg_0.contains(finalText.getString()))) {
            text = Component.literal((String)BadgesUtils.getMods().getOrDefault(namespace, ""));
            fillColor = BadgesUtils.getColorFromModName(namespace);
        }
        return new Tuple((Object)text, (Object)fillColor);
    }

    public static Map<String, String> getMods() {
        if (!mods.isEmpty()) {
            return mods;
        }
        for (ModContainer modContainer : FabricLoader.getInstance().getAllMods()) {
            if (modContainer.getMetadata().getId().equals("minecraft")) continue;
            mods.put(modContainer.getMetadata().getId(), modContainer.getMetadata().getName());
        }
        return mods;
    }

    public static int darkenColor(int color, float factor) {
        int alpha = color >> 24 & 0xFF;
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        red = Math.max(0, (int)((float)red * factor));
        green = Math.max(0, (int)((float)green * factor));
        blue = Math.max(0, (int)((float)blue * factor));
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    public static int getColorFromModName(String modName) {
        int hash = modName.hashCode();
        int r = hash >> 16 & 0xFF;
        int g = hash >> 8 & 0xFF;
        int b = hash & 0xFF;
        int a = 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static void drawFrame(GuiGraphicsExtractor context, int x, int y, int width, int height, int z, int color) {
        BadgesUtils.renderVerticalLine(context, x, y, height - 2, z, color);
        BadgesUtils.renderVerticalLine(context, x + width - 1, y, height - 2, z, color);
        BadgesUtils.renderHorizontalLine(context, x + 1, y - 1, width - 2, z, color);
        BadgesUtils.renderHorizontalLine(context, x + 1, y - 1 + height - 1, width - 2, z, color);
    }

    private static void renderVerticalLine(GuiGraphicsExtractor context, int x, int y, int height, int z, int color) {
        context.fill(x, y, x + 1, y + height, color);
    }

    private static void renderHorizontalLine(GuiGraphicsExtractor context, int x, int y, int width, int z, int color) {
        context.fill(x, y, x + width, y + 1, color);
    }


}

