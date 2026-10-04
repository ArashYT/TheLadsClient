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

/** EnhancedToolbars on 26.x: Lads' durability line (DurabilityPresentation) in place of vanilla's, with the module's item filters. */
public final class NativeDurabilityTooltip {
    private static String lastExclusions;
    private static Set<String> excluded = Set.of();
    private NativeDurabilityTooltip() {}
    static void append(ItemStack stack, TooltipDisplay display, List<Component> lines) {
        if (!stack.isDamageableItem() || !display.shows(DataComponents.DAMAGE) || !display.shows(DataComponents.MAX_DAMAGE)) return;
        var module = module("EnhancedToolbars");
        String exclusions = ((TextOption) module.getOption("Excluded Mods")).getValue();
        if (!exclusions.equals(lastExclusions)) {
            excluded = DurabilityPresentation.exclusions(exclusions); lastExclusions = exclusions;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!DurabilityPresentation.shows(id, stack.getMaxDamage(), stack.getDamageValue(),
            bool("EnhancedToolbars", "Only Vanilla Tools", false), bool("EnhancedToolbars", "Show When Full", true), excluded)) return;
        // Replace only vanilla's durability line. Other advanced data, custom lore and mod tooltip components survive.
        lines.removeIf(line -> line.getContents() instanceof TranslatableContents text && "item.durability".equals(text.getKey()));
        var color = (ColorOption) module.getOption("Durability Base Color");
        int base = color.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : color.getColor();
        var settings = new DurabilityPresentation.Settings(
            DurabilityPresentation.Shape.values()[choice("EnhancedToolbars", "Durability Style", 0)],
            DurabilityPresentation.Tint.values()[choice("EnhancedToolbars", "Durability Color Style", 0)],
            bool("EnhancedToolbars", "Show Durability Hint", true), bool("EnhancedToolbars", "Show Max Durability", true),
            bool("EnhancedToolbars", "Colorize Durability", true), base);
        var parts = DurabilityPresentation.line(stack.getMaxDamage(), stack.getDamageValue(), settings);
        var text = Component.empty();
        for (var part : parts) text.append(Component.literal(part.text()).withColor(part.rgb()));
        lines.add(text);
    }
}
