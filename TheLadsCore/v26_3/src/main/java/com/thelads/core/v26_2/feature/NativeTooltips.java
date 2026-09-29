package com.thelads.core.v26_2.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import static com.thelads.core.v26_2.feature.NativeQualityOfLife.*;

public final class NativeTooltips {
    private NativeTooltips() {}

    /** Registration is opt-in and has no production tick work. */
    public static void initializeVerification() {
        if (Boolean.getBoolean("thelads.verifyDurabilityTooltip")) NativeDurabilityProbe.initialize();
    }

    public static List<Component> append(ItemStack stack, List<Component> original, TooltipFlag flag) {
        boolean durability = enabled("EnhancedToolbars") && bool("EnhancedToolbars", "Detailed Durability", true);
        boolean details = enabled("EnhancedTooltips");
        if (stack.isEmpty() || original.isEmpty() || (!durability && !details)) return original;
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (display.hideTooltip()) return original;
        List<Component> lines = new ArrayList<>(original);
        if (durability) NativeDurabilityTooltip.append(stack, display, lines);
        if (details) {
            if (bool("EnhancedTooltips", "Show Item ID", true) && !flag.isAdvanced())
                lines.add(Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
            var food = stack.get(DataComponents.FOOD);
            if (food != null && display.shows(DataComponents.FOOD) && bool("EnhancedTooltips", "Show Food Values", true)) {
                lines.add(Component.literal("Food: +" + food.nutrition() + " hunger, +"
                    + String.format(Locale.ROOT, "%.1f", food.saturation()) + " saturation").withStyle(ChatFormatting.GOLD));
            }
            if (bool("EnhancedTooltips", "Show Component Count", false))
                lines.add(Component.literal("Components: " + stack.getComponents().size()).withStyle(ChatFormatting.DARK_GRAY));
            if (bool("EnhancedTooltips", "Show NBT Tags", false)) {
                var data = stack.get(DataComponents.CUSTOM_DATA);
                if (data != null && !data.isEmpty() && display.shows(DataComponents.CUSTOM_DATA)) {
                    String nbt = data.toString();
                    // Bound tooltip geometry/work for server-supplied custom data; item data is never changed.
                    int limit = Math.min(nbt.length(), 240);
                    lines.add(Component.literal("Custom NBT:").withStyle(ChatFormatting.GRAY));
                    for (int offset = 0; offset < limit; offset += 60)
                        lines.add(Component.literal(nbt.substring(offset, Math.min(limit, offset + 60))
                            + (offset + 60 >= limit && nbt.length() > limit ? "..." : "")).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        return lines;
    }
}
