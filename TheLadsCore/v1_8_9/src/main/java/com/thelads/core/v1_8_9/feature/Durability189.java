package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.DurabilityPresentation;
import com.thelads.core.config.TextOption;
import java.util.Collections;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * EnhancedToolbars (Durability Tooltip) on 1.8.9, as 26.x NativeDurabilityTooltip: numbers, a bar or condition text from the shared
 * DurabilityPresentation, in place of 1.8.9's advanced "Durability: x / y" line. 1.8.9 text has the 16 chat colours, so each colour
 * is the nearest of them. ItemStackTooltipMixin hides attribute lines (Show Item Attributes).
 */
public final class Durability189 {
    private static final String MODULE = "EnhancedToolbars";
    private static String lastExclusions;
    private static Set<String> excluded = Collections.emptySet();

    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.itemStack;
        if (stack == null || !stack.isItemStackDamageable() || !Options189.enabled(MODULE) || !Options189.bool(MODULE, "Detailed Durability", true)) return;
        String exclusions = ((TextOption) Options189.module(MODULE).getOption("Excluded Mods")).getValue();
        if (!exclusions.equals(lastExclusions)) {
            excluded = DurabilityPresentation.excludedNamespaces(exclusions);
            lastExclusions = exclusions;
        }
        Object id = Item.itemRegistry.getNameForObject(stack.getItem());
        String namespace = id instanceof ResourceLocation ? ((ResourceLocation) id).getResourceDomain() : "minecraft";
        int maximum = stack.getMaxDamage(), damage = stack.getItemDamage();
        if (!DurabilityPresentation.visible(namespace, maximum, damage, Options189.bool(MODULE, "Only Vanilla Tools", false),
            Options189.bool(MODULE, "Show When Full", true), excluded)) return;
        event.toolTip.remove("Durability: " + (maximum - damage) + " / " + maximum); // 1.8.9's own line (advanced tooltips)
        DurabilityPresentation.Style style = new DurabilityPresentation.Style(
            DurabilityPresentation.Format.values()[Options189.choice(MODULE, "Durability Style", 0)],
            DurabilityPresentation.Coloring.values()[Options189.choice(MODULE, "Durability Color Style", 0)],
            Options189.bool(MODULE, "Show Durability Hint", true), Options189.bool(MODULE, "Show Max Durability", true),
            Options189.bool(MODULE, "Colorize Durability", true), Options189.color(MODULE, "Durability Base Color", 0xffaaaaaa));
        for (DurabilityPresentation.Line line : DurabilityPresentation.lines(maximum, damage, style)) {
            StringBuilder text = new StringBuilder();
            for (DurabilityPresentation.Span span : line.spans()) text.append('\u00a7').append(Integer.toHexString(DurabilityPresentation.chatColor(span.rgb()))).append(span.text());
            event.toolTip.add(text.toString());
        }
    }
}
