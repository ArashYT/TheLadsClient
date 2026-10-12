package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.TooltipFit;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.client.config.GuiUtils;
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

    /** The EnhancedTooltips food line, also the AppleSkin module's 1.8.9 tooltip (Food189), which leaves it to this one when both are on; null for non-food. */
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

    /**
     * GuiScreen.drawHoveringText (every item, creative tab and potion tooltip): one Forge's layout would leave partly off screen is
     * drawn here instead, smaller (TooltipFit) and unwrapped, with Forge's own layout in the larger space; false leaves it to Forge.
     * Forge wraps a tooltip that fits on neither side of the pointer: that stays, unless the wrapped one is too tall.
     */
    public static boolean drawFitted(List<String> lines, int x, int y, int screenWidth, int screenHeight, FontRenderer font) {
        TooltipFit.last = 1F;
        if (lines.isEmpty()) return false;
        int width = 0;
        for (String line : lines) width = Math.max(width, font.getStringWidth(line));
        boolean sideFits = x + 12 + width + 4 <= screenWidth || x - 16 - width >= 4;
        int count = lines.size();
        if (!sideFits && width + 10 <= screenWidth) { // Forge's wrap
            int wrap = x > screenWidth / 2 ? x - 20 : screenWidth - 16 - x;
            count = 0;
            for (String line : lines) count += font.listFormattedStringToWidth(line, wrap).size();
            if (height(count) + 10 <= screenHeight) return false;
        } else if (sideFits && height(count) + 10 <= screenHeight) return false;
        float scale = TooltipFit.scale(width, height(lines.size()), screenWidth, screenHeight, new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor());
        int scaledWidth = (int) (screenWidth / scale), scaledX = (int) (x / scale);
        if (scaledX + 12 + width + 4 > scaledWidth && scaledX - 16 - width < 4) scaledX = 4 - 12; // left edge, as later versions place it (no wrap)
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1F);
        GuiUtils.drawHoveringText(lines, scaledX, (int) (y / scale), scaledWidth, (int) (screenHeight / scale), -1, font);
        GlStateManager.popMatrix();
        return true;
    }

    /** Forge's tooltip text height for this many lines (10 each, 2 more under the title). */
    private static int height(int lines) { return 8 + (lines - 1) * 10 + (lines > 1 ? 2 : 0); }
}
