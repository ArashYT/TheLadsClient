// The Lads: independent implementation (clean room) of the extra tooltip lines Tooltips TXF is described to add
// (durability, food, compostable, burn time, song duration, enchantability, repair cost, block strength,
// enchanting power, mining level/speed, mod name, components). It is not derived from that mod's code.
package com.thelads.core.v1_21_1.embedded.tooltips;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public final class ExtraTooltips {
    private static final DecimalFormat NUMBER = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final String KEY = "theladscore.tooltips.";
    private static final Map<String, Integer> TIERS = Map.of("incorrect_for_wooden_tool", 0, "incorrect_for_gold_tool", 0,
        "incorrect_for_stone_tool", 1, "incorrect_for_copper_tool", 1, "incorrect_for_iron_tool", 2,
        "incorrect_for_diamond_tool", 3, "incorrect_for_netherite_tool", 3);
    private static TooltipsConfig config;

    private ExtraTooltips() {}

    public static void init() {
        config = TooltipsConfig.load();
        ItemTooltipCallback.EVENT.register(ExtraTooltips::append);
    }

    static void append(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        TooltipsConfig c = config;
        if (c == null || !c.on("enableMod") || stack.isEmpty() || lines.isEmpty()) return;
        if (stack.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP)) return;
        if (c.on("showDurability") && stack.isDamageableItem() && !hasLine(lines, "item.durability")
                && !nativeLines("Detailed Durability", "EnhancedToolbars")) {
            lines.add(Component.translatable("item.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(style -> style.withColor(c.color("durability")))); // vanilla's text, as the running mod shows it
        }
        var food = stack.get(DataComponents.FOOD);
        if (c.on("showFoodValues") && food != null && !nativeLines("Show Food Values", "EnhancedTooltips")) {
            lines.add(line(c, "foodValues", "food", food.nutrition(), NUMBER.format(food.saturation())));
        }
        float compostable = ComposterBlock.COMPOSTABLES.getFloat(stack.getItem());
        if (c.on("showCompostable") && compostable > 0) {
            lines.add(line(c, "compostable", "compostable", Math.round(compostable * 100)));
        }
        int burnTime = AbstractFurnaceBlockEntity.getFuel().getOrDefault(stack.getItem(), 0);
        if (c.on("showBurnTime") && burnTime > 0) {
            lines.add(line(c, "burnTime", "burn_time", time(c, burnTime)));
        }
        if (c.on("showSongDuration") && stack.has(DataComponents.JUKEBOX_PLAYABLE)) {
            JukeboxSong.fromStack(context.registries(), stack)
                .ifPresent(song -> lines.add(line(c, "songDuration", "song_duration", seconds(song.value().lengthInSeconds()))));
        }
        int enchantability = stack.getItem().getEnchantmentValue();
        if (c.on("showEnchantability") && enchantability > 0) {
            lines.add(line(c, "enchantability", "enchantability", enchantability));
        }
        Integer repairCost = stack.get(DataComponents.REPAIR_COST);
        if (c.on("showRepairCost") && repairCost != null && repairCost > 0) {
            lines.add(line(c, "repairCost", "repair_cost", repairCost + 1)); // the anvil's cost for this item: prior work + 1
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (c.on("showStrength") && block.defaultDestroyTime() > 0) { // instant-break blocks (crops, flowers, torches) show none
                lines.add(line(c, "strength", "strength", NUMBER.format(block.defaultDestroyTime()), NUMBER.format(block.getExplosionResistance())));
            }
            if (c.on("showEnchantmentPower") && block.defaultBlockState().is(BlockTags.ENCHANTMENT_POWER_PROVIDER)) {
                lines.add(line(c, "enchantmentPower", "enchantment_power", 1));
            }
        }
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool != null) {
            if (c.on("showMiningLevel")) miningLevel(tool).ifPresent(tier -> lines.add(line(c, "miningLevel", "mining_level", tier)));
            if (c.on("showMiningSpeed")) lines.add(line(c, "miningSpeed", "mining_speed", NUMBER.format(miningSpeed(tool))));
        }
        if (c.on("showModName")) {
            String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
            String name = FabricLoader.getInstance().getModContainer(namespace).map(mod -> mod.getMetadata().getName()).orElse(namespace);
            lines.add(Component.literal(name).withStyle(style -> style.withColor(c.color("modName")).withItalic(true)));
        }
        if (c.on("showComponents")) components(c, stack, lines);
    }

    private static MutableComponent line(TooltipsConfig c, String colorKey, String key, Object... args) {
        return Component.translatable(KEY + key, args).withStyle(style -> style.withColor(c.color(colorKey)));
    }

    /** Another installed mod already shows the same values (the Durability Tooltip mod of the 1.21.11 pack). */
    private static boolean nativeLines(String option, String module) {
        return option.equals("Detailed Durability") && FabricLoader.getInstance().isModLoaded("durabilitytooltip");
    }

    private static boolean hasLine(List<Component> lines, String key) {
        for (Component line : lines) if (line.getContents() instanceof TranslatableContents text && key.equals(text.getKey())) return true;
        return false;
    }

    private static Object time(TooltipsConfig c, int ticks) {
        return c.on("timeInSeconds") ? seconds(ticks / 20.0) : ticks;
    }

    private static Component seconds(double seconds) {
        return Component.translatable(KEY + "seconds", NUMBER.format(seconds));
    }

    /** Harvest level of the tool's tier, read from its "incorrect for" block tag; tiers of other mods show no level. */
    private static Optional<Integer> miningLevel(Tool tool) {
        for (Tool.Rule rule : tool.rules()) {
            if (rule.correctForDrops().orElse(true)) continue;
            Optional<TagKey<Block>> tag = rule.blocks().unwrapKey();
            if (tag.isPresent() && TIERS.get(tag.get().location().getPath()) instanceof Integer level) return Optional.of(level);
        }
        return Optional.empty();
    }

    private static float miningSpeed(Tool tool) {
        float speed = tool.defaultMiningSpeed();
        for (Tool.Rule rule : tool.rules()) if (rule.speed().isPresent() && rule.speed().get() < Float.MAX_VALUE) speed = Math.max(speed, rule.speed().get()); // MAX_VALUE: instant mining
        return speed;
    }

    private static void components(TooltipsConfig c, ItemStack stack, List<Component> lines) {
        if (!Screen.hasControlDown()) {
            lines.add(Component.translatable(KEY + "components", Component.translatable(KEY + "hold_ctrl")
                .withStyle(style -> style.withColor(c.componentColor(1)))).withStyle(style -> style.withColor(c.componentColor(0))));
            return;
        }
        lines.add(Component.translatable(KEY + "components_title").withStyle(style -> style.withColor(c.componentColor(0))));
        for (TypedDataComponent<?> component : stack.getComponents()) {
            var id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type());
            String value = String.valueOf(component.value());
            if (value.length() > 60) value = value.substring(0, 57) + "...";
            lines.add(Component.literal(" " + id + ": ").withStyle(style -> style.withColor(c.componentColor(2)))
                .append(Component.literal(value).withStyle(style -> style.withColor(c.componentColor(3)))));
        }
    }
}
