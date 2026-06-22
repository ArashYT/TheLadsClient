/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.Font
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.Style
 *  net.minecraft.network.chat.TextColor
 *  net.minecraft.world.item.ItemStack
 */
package dev.ultimatchamp.enhancedtooltips.tooltip;

import dev.ultimatchamp.enhancedtooltips.config.EnhancedTooltipsConfig;
import dev.ultimatchamp.enhancedtooltips.util.TranslationStringColorParser;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

public class TooltipHelper {
    public static void renderText(GuiGraphicsExtractor graphics, Font font, Component str, int x, int y, int color, boolean dropShadow) {
        graphics.text(font, str, x, y, color, dropShadow);
    }

    public static Component getRarityName(ItemStack stack) {
        String key = "enhancedtooltips.rarity." + stack.getRarity().name().toLowerCase();
        return Component.translatable((String)key).setStyle(Style.EMPTY.withColor(-8355712));
    }

    public static Component getDisplayName(ItemStack stack) {
        return stack.getStyledHoverName();
    }

    public static Integer[] getItemBorderColor(ItemStack stack) {
        Component displayName = TooltipHelper.getDisplayName(stack);
        Integer[] colors = new Integer[]{null, null};
        if (EnhancedTooltipsConfig.load().border.borderColor == EnhancedTooltipsConfig.BorderColorMode.ITEM_NAME) {
            TextColor clr;
            displayName.visit((style, text) -> {
                TextColor color = style.getColor();
                if (color != null) {
                    if (colors[0] == null) {
                        colors[0] = color.getValue();
                    } else if (color.getValue() != colors[0].intValue()) {
                        colors[1] = color.getValue();
                    }
                }
                return Optional.empty();
            }, displayName.getStyle());
            if (colors[0] == null || colors[0] == -1 || colors[0] == 0xFFFFFF) {
                Integer[] trans = TranslationStringColorParser.getColorsFromTranslation(displayName);
                colors[0] = trans[0];
                colors[1] = trans[1];
            }
            if ((colors[0] == null || colors[0] == -1 || colors[0] == 0xFFFFFF) && (clr = displayName.getStyle().getColor()) != null) {
                colors[0] = clr.getValue();
                if (colors[0] == 0xFFFFFF) {
                    colors[0] = -1;
                }
            }
        } else {
            TextColor clr = displayName.getStyle().getColor();
            if (clr != null) {
                colors[0] = clr.getValue();
                if (colors[0] == 0xFFFFFF) {
                    colors[0] = -1;
                }
            }
        }
        return colors;
    }
}

