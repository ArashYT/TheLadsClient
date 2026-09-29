package com.thelads.core.v26_2.feature;

import com.thelads.core.client.DurabilityPresentation;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.TextOption;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import static com.thelads.core.v26_2.feature.NativeQualityOfLife.*;

/** Original implementation of durability display styles and namespace filters. */
public final class NativeDurabilityTooltip {
    private static String lastExclusions;
    private static Set<String> excluded = Set.of();
    private NativeDurabilityTooltip() {}
    static void append(ItemStack stack, TooltipDisplay display, List<Component> lines) {
        if (!stack.isDamageableItem() || !display.shows(DataComponents.DAMAGE) || !display.shows(DataComponents.MAX_DAMAGE)) return;
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        var module = module("EnhancedToolbars");
        String exclusions = ((TextOption) module.getOption("Excluded Mods")).getValue();
        if (!exclusions.equals(lastExclusions)) {
            excluded = DurabilityPresentation.excludedNamespaces(exclusions); lastExclusions = exclusions;
        }
        if (!DurabilityPresentation.visible(namespace, stack.getMaxDamage(), stack.getDamageValue(),
            bool("EnhancedToolbars", "Only Vanilla Tools", false), bool("EnhancedToolbars", "Show When Full", true), excluded)) return;
        // Replace only vanilla's durability line. Other advanced data, custom lore and mod tooltip components survive.
        lines.removeIf(line -> line.getContents() instanceof TranslatableContents text && "item.durability".equals(text.getKey()));
        var color = (ColorOption) module.getOption("Durability Base Color");
        int base = color.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : color.getColor();
        var style = new DurabilityPresentation.Style(
            DurabilityPresentation.Format.values()[choice("EnhancedToolbars", "Durability Style", 0)],
            DurabilityPresentation.Coloring.values()[choice("EnhancedToolbars", "Durability Color Style", 0)],
            bool("EnhancedToolbars", "Show Durability Hint", true), bool("EnhancedToolbars", "Show Max Durability", true),
            bool("EnhancedToolbars", "Colorize Durability", true), base);
        for (var line : DurabilityPresentation.lines(stack.getMaxDamage(), stack.getDamageValue(), style)) {
            var text = Component.empty();
            for (var span : line.spans()) text.append(Component.literal(span.text()).withColor(span.rgb()));
            lines.add(text);
        }
    }
}
