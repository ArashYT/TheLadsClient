package com.thelads.core.v1_8_9.feature;

import java.util.List;
import java.util.Locale;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * EnhancedTooltips through Forge's ItemTooltipEvent, as 26.x NativeTooltips. 1.8.9 items have NBT instead of data components:
 * "Show Component Count" counts the item's NBT tags and "Show NBT Tags" prints its whole tag. Advanced tooltips (F3+H)
 * already end with the item id and the tag count, so those lines are not repeated there.
 */
public final class Tooltips189 {
    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.itemStack;
        List<String> lines = event.toolTip;
        if (stack == null || stack.getItem() == null || lines.isEmpty() || !Options189.enabled("EnhancedTooltips")) return;
        if (option("Show Item ID", true) && !event.showAdvancedItemTooltips)
            lines.add(EnumChatFormatting.DARK_GRAY + String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())));
        String food = foodLine(stack);
        if (food != null && option("Show Food Values", true)) lines.add(food);
        if (option("Show Component Count", false) && stack.hasTagCompound() && !event.showAdvancedItemTooltips)
            lines.add(EnumChatFormatting.DARK_GRAY + "NBT: " + stack.getTagCompound().getKeySet().size() + " tag(s)");
        if (option("Show NBT Tags", false) && stack.hasTagCompound() && !stack.getTagCompound().hasNoTags()) {
            String nbt = stack.getTagCompound().toString();
            // Bounded like 26.x: server-supplied NBT must not grow the tooltip without limit.
            int limit = Math.min(nbt.length(), 240);
            lines.add(EnumChatFormatting.GRAY + "NBT:");
            for (int offset = 0; offset < limit; offset += 60)
                lines.add(EnumChatFormatting.DARK_GRAY + nbt.substring(offset, Math.min(limit, offset + 60))
                    + (offset + 60 >= limit && nbt.length() > limit ? "..." : ""));
        }
    }

    /** The EnhancedTooltips food line (AppleSkin's food tooltip leaves it to this one when both are on); null for non-food. */
    static String foodLine(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemFood)) return null;
        ItemFood food = (ItemFood) stack.getItem();
        int hunger = food.getHealAmount(stack);
        // FoodStats.addStats: saturation gained is hunger * modifier * 2.
        return EnumChatFormatting.GOLD + "Food: +" + hunger + " hunger, +"
            + String.format(Locale.ROOT, "%.1f", hunger * food.getSaturationModifier(stack) * 2) + " saturation";
    }

    private static boolean option(String name, boolean fallback) {
        return Options189.bool("EnhancedTooltips", name, fallback);
    }
}
