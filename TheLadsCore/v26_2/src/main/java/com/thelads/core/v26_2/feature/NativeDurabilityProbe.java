package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.thelads.core.client.DurabilityPresentation;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.TextOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.slf4j.LoggerFactory;

/** Opt-in transformed tooltip and actual text extraction checks. Every preference is restored. */
public final class NativeDurabilityProbe {
    private static boolean initialized, done;
    private static int passed;
    private NativeDurabilityProbe() {}
    public static void initialize() {
        if (initialized || !Boolean.getBoolean("thelads.verifyDurabilityTooltip")) return;
        initialized = true;
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (done || !mc.isGameLoadFinished() || mc.gui.overlay() != null || mc.font == null || mc.level == null || mc.player == null) return;
            done = true;
            try { run(); }
            catch (Throwable failure) { LoggerFactory.getLogger("TheLadsCore").error("Lads durability tooltip probe FAILED after {} checks", passed, failure); }
        });
    }
    public static int run() {
        passed = 0;
        var mc = Minecraft.getInstance();
        Module bars = NativeQualityOfLife.module("EnhancedToolbars");
        boolean enabled = bars.isEnabled(); long modified = bars.getLastModified();
        Map<Option, JsonElement> settings = new LinkedHashMap<>();
        bars.getOptions().forEach(option -> settings.put(option, option.save().deepCopy()));
        try {
            bars.setEnabled(false);
            var sword = new ItemStack(Items.DIAMOND_SWORD); sword.setDamageValue(100);
            int maximum = sword.getMaxDamage(), remaining = maximum - 100;
            // Lads lines are literal text; vanilla and third-party mods (Tooltips TXF) add the translatable item.durability line.
            check(durability(sword, TooltipFlag.NORMAL).isEmpty(), "disabled adds no Lads durability line: " + lines(sword, TooltipFlag.NORMAL).stream().map(Component::getString).toList());
            bars.setEnabled(true);
            set(bars, "Detailed Durability", true); set(bars, "Show Max Durability", true);
            set(bars, "Colorize Durability", true); set(bars, "Show Item Attributes", true);
            set(bars, "Show Durability Hint", true); set(bars, "Only Vanilla Tools", false); set(bars, "Show When Full", true);
            ((TextOption) bars.getOption("Excluded Mods")).setValue("");
            ((DropdownOption) bars.getOption("Durability Style")).setIndex(0);
            ((DropdownOption) bars.getOption("Durability Color Style")).setIndex(0);
            var color = (ColorOption) bars.getOption("Durability Base Color"); color.setUseGlobal(false); color.setColor(0xff123456);
            check(lines(sword, TooltipFlag.NORMAL).stream().noneMatch(NativeDurabilityProbe::itemDurabilityKey), "enabled Lads line replaces every item.durability line (no duplicate)");
            check(durability(sword, TooltipFlag.NORMAL).equals(List.of("Uses left: " + remaining + " / " + maximum)), "numbers show actual remaining/max");
            check(durability(sword, TooltipFlag.ADVANCED).size() == 1, "advanced tooltip has one durability line");
            set(bars, "Show Max Durability", false);
            check(durability(sword, TooltipFlag.NORMAL).equals(List.of("Uses left: " + remaining)), "existing maximum control consumed");
            set(bars, "Show Max Durability", true);
            int attributeCount = lines(sword, TooltipFlag.NORMAL).size(); set(bars, "Show Item Attributes", false);
            check(lines(sword, TooltipFlag.NORMAL).size() < attributeCount, "existing attribute control still reaches real hook");
            set(bars, "Show Item Attributes", true);
            ((DropdownOption) bars.getOption("Durability Style")).setIndex(1);
            var bar = durabilityComponents(sword, TooltipFlag.NORMAL);
            int pips = DurabilityPresentation.PIPS, lit = (int) ((long) remaining * pips / maximum);
            check(bar.size() == 1 && bar.getFirst().getString().equals("Uses left: " + "|".repeat(pips) + " " + remaining * 100 / maximum + "%"),
                "gauge is one line of pips and a percentage: " + durability(sword, TooltipFlag.NORMAL));
            int lead = "Uses left: ".length();
            check(colorAt(bar.getFirst(), lead + lit - 1) != 0x555555 && colorAt(bar.getFirst(), lead + lit) == 0x555555, "gauge reflects actual damage");
            set(bars, "Show Durability Hint", false);
            check(durability(sword, TooltipFlag.NORMAL).getFirst().startsWith("|"), "gauge label can be hidden");
            ((DropdownOption) bars.getOption("Durability Style")).setIndex(2);
            check(durability(sword, TooltipFlag.NORMAL).equals(List.of("Good")), "text style without hint");
            sword.setDamageValue(maximum / 2);
            check(durability(sword, TooltipFlag.NORMAL).equals(List.of("Worn")), "half-life condition word");
            sword.setDamageValue(maximum * 3 / 4);
            check(durability(sword, TooltipFlag.NORMAL).equals(List.of("Battered")), "quarter-life condition word");
            sword.setDamageValue(maximum - 1);
            check(durability(sword, TooltipFlag.NORMAL).equals(List.of("About to break")), "low condition word");
            ((DropdownOption) bars.getOption("Durability Color Style")).setIndex(2);
            check(firstColor(durabilityComponents(sword, TooltipFlag.NORMAL).getFirst()) == 0xffaa00, "gold color style on actual component");
            ((DropdownOption) bars.getOption("Durability Color Style")).setIndex(1);
            check(firstColor(durabilityComponents(sword, TooltipFlag.NORMAL).getFirst()) == 0x123456, "custom base color consumed");
            ((DropdownOption) bars.getOption("Durability Color Style")).setIndex(0);
            check(firstColor(durabilityComponents(sword, TooltipFlag.NORMAL).getFirst()) == 0xff0000, "varying colour is the durability bar's red when almost broken");
            set(bars, "Colorize Durability", false);
            check(firstColor(durabilityComponents(sword, TooltipFlag.NORMAL).getFirst()) == 0x123456, "existing colorize off uses base");
            set(bars, "Colorize Durability", true); sword.setDamageValue(0);
            check(durability(sword, TooltipFlag.NORMAL).equals(List.of("Like new")), "full condition word");
            set(bars, "Show When Full", false);
            check(durability(sword, TooltipFlag.NORMAL).isEmpty(), "full durability can be hidden");
            sword.setDamageValue(100);
            check(!durability(sword, TooltipFlag.NORMAL).isEmpty(), "damaged items remain visible with full hidden");
            set(bars, "Only Vanilla Tools", true);
            check(!durability(sword, TooltipFlag.NORMAL).isEmpty(), "vanilla namespace passes filter");
            ((TextOption) bars.getOption("Excluded Mods")).setValue(" minecraft ,tconstruct");
            check(durability(sword, TooltipFlag.NORMAL).isEmpty(), "whole mod namespace blacklist consumed");
            ((TextOption) bars.getOption("Excluded Mods")).setValue("minecraft_extra");
            check(!durability(sword, TooltipFlag.NORMAL).isEmpty(), "blacklist does not match namespace prefix");
            ((TextOption) bars.getOption("Excluded Mods")).setValue("minecraft:diamond_sword");
            check(durability(sword, TooltipFlag.NORMAL).isEmpty(), "a single item id can be excluded");
            ((TextOption) bars.getOption("Excluded Mods")).setValue("minecraft:iron_sword");
            check(!durability(sword, TooltipFlag.NORMAL).isEmpty(), "excluding another item leaves this one");
            ((TextOption) bars.getOption("Excluded Mods")).setValue("");
            sword.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.DAMAGE, true));
            check(durability(sword, TooltipFlag.NORMAL).isEmpty(), "hidden damage component respected");
            sword.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.MAX_DAMAGE, true));
            check(durability(sword, TooltipFlag.NORMAL).isEmpty(), "hidden maximum component respected");
            sword.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
            check(lines(sword, TooltipFlag.NORMAL).isEmpty(), "whole hidden tooltip respected");
            sword.remove(DataComponents.TOOLTIP_DISPLAY);
            set(bars, "Detailed Durability", false);
            check(durability(sword, TooltipFlag.NORMAL).isEmpty(), "existing detail switch consumed");
            set(bars, "Detailed Durability", true);
            check(durability(new ItemStack(Items.APPLE), TooltipFlag.NORMAL).isEmpty(), "non-damageable item has no durability");
            for (int style = 0; style < 3; style++) {
                ((DropdownOption) bars.getOption("Durability Style")).setIndex(style); set(bars, "Show Durability Hint", true);
                var components = durabilityComponents(sword, TooltipFlag.NORMAL);
                var state = new GuiRenderState(); var graphics = new GuiGraphicsExtractor(mc, state, 0, 0);
                var pose = new org.joml.Matrix3x2f(graphics.pose());
                int y = 20;
                for (var component : components) {
                    var tooltip = ClientTooltipComponent.create(component.getVisualOrderText());
                    check(tooltip.getWidth(mc.font) > 0 && tooltip.getHeight(mc.font) > 0, "style has actual native tooltip dimensions");
                    tooltip.extractText(graphics, mc.font, 20, y); y += tooltip.getHeight(mc.font);
                }
                int[] extracted = {0};
                // 26.2 queues text separately from textured GUI elements until glyph preparation.
                state.forEachText(text -> {
                    if (text.ensurePrepared() != null && text.bounds() != null && text.bounds().width() > 0) extracted[0]++;
                });
                check(extracted[0] == components.size() && graphics.pose().equals(pose), "native text extraction prepares every line and preserves pose");
            }
            // Preserve a foreign custom component and lore when replacing a vanilla advanced durability line.
            var foreign = Component.literal("Other mod data"); var lore = Component.literal("Durability: enchanted lore");
            var result = NativeTooltips.append(sword, List.of(Component.literal("Sword"), foreign, lore, Component.translatable("item.durability", 1, 2)), TooltipFlag.ADVANCED);
            check(result.contains(foreign) && result.contains(lore), "custom components and similarly worded lore remain intact");
        } finally {
            settings.forEach(Option::load); bars.setEnabled(enabled); bars.setLastModified(modified);
        }
        LoggerFactory.getLogger("TheLadsCore").info("Lads durability tooltip probe END: {} passed, 0 failed (transformed tooltip and text extraction; preferences restored)", passed);
        return passed;
    }
    private static List<Component> lines(ItemStack item, TooltipFlag flag) { return item.getTooltipLines(Item.TooltipContext.EMPTY, null, flag); }
    private static List<Component> durabilityComponents(ItemStack item, TooltipFlag flag) {
        return lines(item, flag).stream().filter(line -> {
            String text = line.getString();
            return !itemDurabilityKey(line) && (text.startsWith("Uses left: ") || text.startsWith("Condition: ") || text.startsWith("|")
                || DurabilityPresentation.WEAR_WORDS.contains(text));
        }).toList();
    }
    private static List<String> durability(ItemStack item, TooltipFlag flag) { return durabilityComponents(item, flag).stream().map(Component::getString).toList(); }
    private static int firstColor(Component component) { return colorAt(component, 0); }
    /** The colour of the {@code at}-th character drawn (-1: none). */
    private static int colorAt(Component component, int at) {
        int[] color = {-1}, seen = {0};
        component.getVisualOrderText().accept((index, style, codePoint) -> {
            if (seen[0]++ < at) return true;
            color[0] = style.getColor() == null ? -1 : style.getColor().getValue();
            return false;
        });
        return color[0];
    }
    private static boolean itemDurabilityKey(Component line) { return line.getContents() instanceof TranslatableContents text && "item.durability".equals(text.getKey()); }
    private static void set(Module module, String name, boolean value) { ((BoolOption) module.getOption(name)).set(value); }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); passed++; }
}
