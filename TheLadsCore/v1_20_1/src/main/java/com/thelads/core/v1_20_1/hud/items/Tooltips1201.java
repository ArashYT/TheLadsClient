package com.thelads.core.v1_20_1.hud.items;

import com.thelads.core.client.DurabilityPresentation;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
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

    private static Module modToolbars;
    private static Module modTooltips;
    private static BoolOption optDetailedDurability;
    private static BoolOption optShowItemAttributes;
    private static BoolOption optOnlyVanillaTools;
    private static BoolOption optShowWhenFull;
    private static BoolOption optShowDurabilityHint;
    private static BoolOption optShowMaxDurability;
    private static BoolOption optColorizeDurability;
    private static TextOption optExcludedMods;
    private static ColorOption optDurabilityBaseColor;
    private static DropdownOption optDurabilityStyle;
    private static DropdownOption optDurabilityColorStyle;

    private static BoolOption optShowItemId;
    private static BoolOption optShowFoodValues;
    private static BoolOption optShowComponentCount;
    private static BoolOption optShowNbtTags;

    private Tooltips1201() {}

    private static void ensureCached() {
        if (modToolbars == null) {
            Module tb = module("EnhancedToolbars");
            if (tb != null) {
                optDetailedDurability = (BoolOption) tb.getOption("Detailed Durability");
                optShowItemAttributes = (BoolOption) tb.getOption("Show Item Attributes");
                optOnlyVanillaTools = (BoolOption) tb.getOption("Only Vanilla Tools");
                optShowWhenFull = (BoolOption) tb.getOption("Show When Full");
                optShowDurabilityHint = (BoolOption) tb.getOption("Show Durability Hint");
                optShowMaxDurability = (BoolOption) tb.getOption("Show Max Durability");
                optColorizeDurability = (BoolOption) tb.getOption("Colorize Durability");
                optExcludedMods = (TextOption) tb.getOption("Excluded Mods");
                optDurabilityBaseColor = (ColorOption) tb.getOption("Durability Base Color");
                optDurabilityStyle = (DropdownOption) tb.getOption("Durability Style");
                optDurabilityColorStyle = (DropdownOption) tb.getOption("Durability Color Style");
                modToolbars = tb;
            }
        }
        if (modTooltips == null) {
            Module tt = module("EnhancedTooltips");
            if (tt != null) {
                optShowItemId = (BoolOption) tt.getOption("Show Item ID");
                optShowFoodValues = (BoolOption) tt.getOption("Show Food Values");
                optShowComponentCount = (BoolOption) tt.getOption("Show Component Count");
                optShowNbtTags = (BoolOption) tt.getOption("Show NBT Tags");
                modTooltips = tt;
            }
        }
    }

    /** The tooltip with Lads' lines, or {@code original} itself when nothing is added. */
    public static List<Component> append(ItemStack stack, List<Component> original, TooltipFlag flag) {
        ensureCached();
        // A loaded Durability Tooltip jar keeps the job (EnhancedToolbars is then not built in).
        boolean durability = modToolbars != null && modToolbars.isEnabled() && ModuleSupport.isBuiltIn("EnhancedToolbars")
            && (optDetailedDurability == null || optDetailedDurability.get());
        boolean details = modTooltips != null && modTooltips.isEnabled();
        if (stack.isEmpty() || original.isEmpty() || !durability && !details) return original;
        List<Component> lines = new ArrayList<>(original);
        if (durability) durability(stack, lines);
        if (details) {
            if ((optShowItemId == null || optShowItemId.get()) && !flag.isAdvanced())
                lines.add(Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
            var food = Food1201.food(stack);
            if (food != null && (optShowFoodValues == null || optShowFoodValues.get())) {
                lines.add(Component.literal("Food: +" + food.getNutrition() + " hunger, +"
                    + String.format(Locale.ROOT, "%.1f", Food1201.saturationGain(food)) + " saturation").withStyle(ChatFormatting.GOLD));
            }
            var tag = stack.getTag();
            if (optShowComponentCount != null && optShowComponentCount.get())
                lines.add(Component.literal("NBT keys: " + (tag == null ? 0 : tag.size())).withStyle(ChatFormatting.DARK_GRAY));
            if (optShowNbtTags != null && optShowNbtTags.get() && tag != null && !tag.isEmpty()) {
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
        ensureCached();
        return modToolbars == null || !modToolbars.isEnabled() || optShowItemAttributes == null || optShowItemAttributes.get();
    }

    /** Lads' durability line (DurabilityPresentation) in place of vanilla's advanced-tooltip one, with the module's item filters. */
    static void durability(ItemStack stack, List<Component> lines) {
        if (!stack.isDamageableItem()) return;
        ensureCached();
        String exclusions = optExcludedMods != null ? optExcludedMods.getValue() : "";
        if (!exclusions.equals(lastExclusions)) {
            excluded = DurabilityPresentation.exclusions(exclusions);
            lastExclusions = exclusions;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!DurabilityPresentation.shows(id, stack.getMaxDamage(), stack.getDamageValue(),
            optOnlyVanillaTools != null && optOnlyVanillaTools.get(),
            optShowWhenFull == null || optShowWhenFull.get(), excluded)) return;
        // Replace only vanilla's durability line. Other advanced data, custom lore and mod lines survive.
        lines.removeIf(line -> line.getContents() instanceof TranslatableContents text && "item.durability".equals(text.getKey()));
        int base = optDurabilityBaseColor == null || optDurabilityBaseColor.isUseGlobal()
            ? HudSettings.getInstance().getGlobalColor()
            : optDurabilityBaseColor.getColor();
        var settings = new DurabilityPresentation.Settings(
            DurabilityPresentation.Shape.values()[optDurabilityStyle != null ? optDurabilityStyle.getIndex() : 0],
            DurabilityPresentation.Tint.values()[optDurabilityColorStyle != null ? optDurabilityColorStyle.getIndex() : 0],
            optShowDurabilityHint == null || optShowDurabilityHint.get(),
            optShowMaxDurability == null || optShowMaxDurability.get(),
            optColorizeDurability == null || optColorizeDurability.get(), base);
        MutableComponent text = Component.empty();
        for (var part : DurabilityPresentation.line(stack.getMaxDamage(), stack.getDamageValue(), settings))
            text.append(Component.literal(part.text()).withStyle(style -> style.withColor(part.rgb())));
        lines.add(text);
    }
}
