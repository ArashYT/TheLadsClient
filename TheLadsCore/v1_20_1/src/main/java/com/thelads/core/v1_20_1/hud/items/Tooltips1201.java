package com.thelads.core.v1_20_1.hud.items;

import com.thelads.core.client.DurabilityPresentation;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.TextOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import static com.thelads.core.v1_20_1.hud.Hud1201.*;

/**
 * EnhancedTooltips and EnhancedToolbars' durability line on 1.20.1 (26.3 NativeTooltips + NativeDurabilityTooltip), appended to
 * the lines ItemStack.getTooltipLines returns (ItemTooltipMixin). 1.20.1 items have NBT, not components: "Show Component Count"
 * counts the item's top-level NBT keys and "Show NBT Tags" prints its whole tag; per-component hiding does not exist there.
 */
public final class Tooltips1201 {
    private static String lastExclusions;
    private static Set<String> excluded = Set.of();
    private Tooltips1201() {}

    /** The tooltip with Lads' lines, or {@code original} itself when nothing is added. */
    public static List<Component> append(ItemStack stack, List<Component> original, TooltipFlag flag) {
        // A loaded Durability Tooltip jar keeps the job (EnhancedToolbars is then not built in).
        boolean durability = enabled("EnhancedToolbars") && ModuleSupport.isBuiltIn("EnhancedToolbars") && bool("EnhancedToolbars", "Detailed Durability", true);
        boolean details = enabled("EnhancedTooltips");
        if (stack.isEmpty() || original.isEmpty() || !durability && !details) return original;
        List<Component> lines = new ArrayList<>(original);
        if (durability) durability(stack, lines);
        if (details) {
            if (bool("EnhancedTooltips", "Show Item ID", true) && !flag.isAdvanced())
                lines.add(Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
            var food = Food1201.food(stack);
            if (food != null && bool("EnhancedTooltips", "Show Food Values", true)) {
                lines.add(Component.literal("Food: +" + food.getNutrition() + " hunger, +"
                    + String.format(Locale.ROOT, "%.1f", Food1201.saturationGain(food)) + " saturation").withStyle(ChatFormatting.GOLD));
            }
            var tag = stack.getTag();
            if (bool("EnhancedTooltips", "Show Component Count", false))
                lines.add(Component.literal("NBT keys: " + (tag == null ? 0 : tag.size())).withStyle(ChatFormatting.DARK_GRAY));
            if (bool("EnhancedTooltips", "Show NBT Tags", false) && tag != null && !tag.isEmpty()) {
                String nbt = tag.toString();
                // Bound tooltip geometry/work for server-supplied item data; item data is never changed.
                int limit = Math.min(nbt.length(), 240);
                lines.add(Component.literal("Custom NBT:").withStyle(ChatFormatting.GRAY));
                for (int offset = 0; offset < limit; offset += 60)
                    lines.add(Component.literal(nbt.substring(offset, Math.min(limit, offset + 60))
                        + (offset + 60 >= limit && nbt.length() > limit ? "..." : "")).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
    }

    /** "Show Item Attributes" off hides the attribute modifier block (ItemTooltipMixin). */
    public static boolean showAttributes() {
        return !enabled("EnhancedToolbars") || bool("EnhancedToolbars", "Show Item Attributes", true);
    }

    /** Lads' durability line (DurabilityPresentation) in place of vanilla's advanced-tooltip one, with the module's item filters. */
    static void durability(ItemStack stack, List<Component> lines) {
        if (!stack.isDamageableItem()) return;
        var module = module("EnhancedToolbars");
        String exclusions = ((TextOption) module.getOption("Excluded Mods")).getValue();
        if (!exclusions.equals(lastExclusions)) {
            excluded = DurabilityPresentation.exclusions(exclusions);
            lastExclusions = exclusions;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!DurabilityPresentation.shows(id, stack.getMaxDamage(), stack.getDamageValue(),
            bool("EnhancedToolbars", "Only Vanilla Tools", false), bool("EnhancedToolbars", "Show When Full", true), excluded)) return;
        // Replace only vanilla's durability line. Other advanced data, custom lore and mod lines survive.
        lines.removeIf(line -> line.getContents() instanceof TranslatableContents text && "item.durability".equals(text.getKey()));
        var color = (ColorOption) module.getOption("Durability Base Color");
        int base = color.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : color.getColor();
        var settings = new DurabilityPresentation.Settings(
            DurabilityPresentation.Shape.values()[choice("EnhancedToolbars", "Durability Style", 0)],
            DurabilityPresentation.Tint.values()[choice("EnhancedToolbars", "Durability Color Style", 0)],
            bool("EnhancedToolbars", "Show Durability Hint", true), bool("EnhancedToolbars", "Show Max Durability", true),
            bool("EnhancedToolbars", "Colorize Durability", true), base);
        MutableComponent text = Component.empty();
        for (var part : DurabilityPresentation.line(stack.getMaxDamage(), stack.getDamageValue(), settings))
            text.append(Component.literal(part.text()).withStyle(style -> style.withColor(part.rgb())));
        lines.add(text);
    }
}
