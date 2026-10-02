// The Lads: independent implementation (clean room) of the extra tooltip lines Tooltips TXF is described to add
// (durability, food, compostable, burn time, cooldown, song duration, enchantability, repair cost, block strength,
// enchanting power, mining level/speed, mod name, components). It is not derived from that mod's code.
package com.thelads.core.v26_2.embedded.tooltips;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.providers.number.ints.BinomialDistributionGenerator;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConditionalValue;
import net.minecraft.world.level.storage.loot.providers.number.ints.NumberDispatcher;
import net.minecraft.world.level.storage.loot.providers.number.ints.Quotient;
import net.minecraft.world.level.storage.loot.providers.number.ints.WeightedListValue;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

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
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (c.on("showDurability") && stack.isDamageableItem() && display.shows(DataComponents.DAMAGE) && !hasLine(lines, "item.durability")
                && !nativeLines("Detailed Durability", "EnhancedToolbars")) {
            lines.add(Component.translatable("item.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(style -> style.withColor(c.color("durability")))); // vanilla's text, as the running mod shows it
        }
        var food = stack.get(DataComponents.FOOD);
        if (c.on("showFoodValues") && food != null && display.shows(DataComponents.FOOD) && !nativeLines("Show Food Values", "EnhancedTooltips")) {
            lines.add(line(c, "foodValues", "food", food.nutrition(), NUMBER.format(food.saturation())));
        }
        var compostable = stack.get(DataComponents.COMPOSTABLE);
        if (c.on("showCompostable") && compostable != null) {
            chance(compostable.layers()).ifPresent(chance -> lines.add(line(c, "compostable", "compostable", Math.round(chance * 100))));
        }
        var fuel = stack.get(DataComponents.COOKING_FUEL);
        if (c.on("showBurnTime") && fuel != null) {
            constant(fuel.burnTime()).filter(ticks -> ticks > 0).ifPresent(ticks -> lines.add(line(c, "burnTime", "burn_time", time(c, ticks))));
        }
        var cooldown = stack.get(DataComponents.USE_COOLDOWN);
        if (c.on("showUseCooldown") && cooldown != null) {
            lines.add(line(c, "useCooldown", "cooldown", time(c, cooldown.ticks())));
        }
        var jukebox = stack.get(DataComponents.JUKEBOX_PLAYABLE);
        if (c.on("showSongDuration") && jukebox != null && jukebox.song().isBound()) {
            lines.add(line(c, "songDuration", "song_duration", seconds(jukebox.song().value().lengthInSeconds())));
        }
        var enchantable = stack.get(DataComponents.ENCHANTABLE);
        if (c.on("showEnchantability") && enchantable != null) {
            lines.add(line(c, "enchantability", "enchantability", enchantable.value()));
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

    /** Lads' own durability or food lines (EnhancedToolbars, EnhancedTooltips) already show the same values. */
    private static boolean nativeLines(String option, String module) {
        return NativeQualityOfLife.enabled(module) && NativeQualityOfLife.bool(module, option, true);
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
        if (!Minecraft.getInstance().hasControlDown()) {
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

    // Since 26.3 compostable chance and burn time are data-driven number providers. Constant values resolve anywhere;
    // named providers live in the server's reloadable registries, so they resolve in singleplayer only.
    private static Optional<Integer> constant(ResolvableInt value) {
        if (value instanceof ResolvableInt.Constant constant) return Optional.of(constant.value());
        if (value instanceof ResolvableInt.Reference reference)
            return provider(Registries.CONTEXT_INT_PROVIDER, reference.key()).flatMap(ExtraTooltips::constant);
        return Optional.empty();
    }

    /** The value in an ordinary furnace: constants, quotients, and the "otherwise" branch of conditions and dispatchers. */
    private static Optional<Integer> constant(ContextIntProvider provider) {
        return switch (provider) {
            case ConstantValue constant -> Optional.of(constant.value());
            case Quotient quotient -> constant(quotient.left()).flatMap(left -> constant(quotient.right()).filter(right -> right != 0).map(right -> left / right));
            case ConditionalValue conditional -> constant(conditional.onFalse());
            case NumberDispatcher dispatcher -> constant(dispatcher.defaultValue());
            default -> Optional.empty();
        };
    }

    private static Optional<Integer> constant(Holder<ContextIntProvider> holder) {
        return holder.isBound() ? constant(holder.value()) : Optional.empty();
    }

    /** The chance that an item adds a layer (an empty composter always takes the first one, the default case counts). */
    private static Optional<Float> chance(ResolvableInt layers) {
        if (layers instanceof ResolvableInt.Constant constant) return Optional.of(constant.value() > 0 ? 1F : 0F);
        if (!(layers instanceof ResolvableInt.Reference reference)) return Optional.empty();
        return provider(Registries.CONTEXT_INT_PROVIDER, reference.key()).flatMap(ExtraTooltips::chance);
    }

    private static Optional<Float> chance(ContextIntProvider provider) {
        return switch (provider) {
            case ConstantValue constant -> Optional.of(constant.value() > 0 ? 1F : 0F);
            case NumberDispatcher dispatcher -> dispatcher.defaultValue().isBound() ? chance(dispatcher.defaultValue().value()) : Optional.empty();
            case WeightedListValue list -> {
                int total = 0, adding = 0;
                for (Weighted<Holder<ContextIntProvider>> entry : list.distribution().unwrap()) {
                    total += entry.weight();
                    if (constant(entry.value()).orElse(0) > 0) adding += entry.weight();
                }
                yield total > 0 ? Optional.of((float) adding / total) : Optional.empty();
            }
            case BinomialDistributionGenerator binomial when binomial.n().isBound() && binomial.p().isBound()
                    && binomial.n().value() instanceof ConstantValue n
                    && binomial.p().value() instanceof net.minecraft.world.level.storage.loot.providers.number.floats.ConstantValue p ->
                Optional.of((float) (1 - Math.pow(1 - p.value(), n.value())));
            default -> Optional.empty();
        };
    }

    private static <T> Optional<T> provider(ResourceKey<net.minecraft.core.Registry<T>> registry, ResourceKey<T> key) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return Optional.empty();
        HolderLookup.Provider lookup = server.reloadableRegistries().lookup();
        return lookup.lookup(registry).flatMap(entries -> entries.get(key)).map(Holder::value);
    }
}
