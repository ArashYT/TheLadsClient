// The Lads: independent implementation (clean room) of the extra tooltip lines Tooltips TXF is described to add
// (durability, food, compostable, burn time, cooldown, song duration, enchantability, repair cost, block strength,
// enchanting power, mining level/speed, mod name, components). It is not derived from that mod's code.
// Ported to Minecraft 1.20.1 from the Lads 26.3 copy: item data components do not exist there, so each line reads the value
// from the 1.20.1 item class that holds it (see META-INF/lads-sources/tooltips/NOTICE.txt for the lines that differ or are left out).
package com.thelads.core.v1_20_1.embedded.tooltips;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;

public final class ExtraTooltips {
    private static final DecimalFormat NUMBER = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final String KEY = "theladscore.tooltips.";
    private static TooltipsConfig config;

    private ExtraTooltips() {}

    public static void init() {
        config = TooltipsConfig.load();
        ItemTooltipCallback.EVENT.register((stack, flag, lines) -> append(config, stack, flag, lines));
    }

    private static java.lang.reflect.Method forgeBurnTime;
    private static boolean forgeBurnTimeLooked;
    private static java.util.Map<Item, Integer> vanillaFuel;

    /**
     * The fuel time of the stack. Fabric API's registry first; under Forge (Sinytra Connector) Forgified Fabric API's registry holds only the
     * fuels added through it, not vanilla's, so Forge's own lookup answers (it covers vanilla and modded fuels alike).
     */
    static Integer burnTime(ItemStack stack) {
        Integer time = FuelRegistry.INSTANCE.get(stack.getItem());
        if (time != null && time > 0) return time;
        if (!forgeBurnTimeLooked) {
            forgeBurnTimeLooked = true;
            try {
                forgeBurnTime = Class.forName("net.minecraftforge.common.ForgeHooks").getMethod("getBurnTime", ItemStack.class, net.minecraft.world.item.crafting.RecipeType.class);
            } catch (ReflectiveOperationException | LinkageError notForge) {
                forgeBurnTime = null;
            }
        }
        if (forgeBurnTime != null) {
            try {
                Integer forge = (Integer) forgeBurnTime.invoke(null, stack, null);
                if (forge != null && forge > 0) return forge;
            } catch (ReflectiveOperationException | RuntimeException failure) {
                // fall through to the vanilla table
            }
        }
        // Forge fills its own table only once a world loaded its tags (the title screen has none): vanilla's table covers that
        if (vanillaFuel == null) vanillaFuel = net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel();
        Integer vanilla = vanillaFuel.get(stack.getItem());
        return vanilla != null && vanilla > 0 ? vanilla : time;
    }

    /** The loaded configuration (null before init), for the QA probe. */
    static TooltipsConfig current() {
        return config;
    }

    static void append(TooltipsConfig c, ItemStack stack, TooltipFlag flag, List<Component> lines) {
        if (c == null || !c.on("enableMod") || stack.isEmpty() || lines.isEmpty()) return;
        Item item = stack.getItem();
        if (c.on("showDurability") && stack.isDamageableItem() && !hasLine(lines, "item.durability")) {
            lines.add(Component.translatable("item.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(style -> style.withColor(c.color("durability")))); // vanilla's text, as the running mod shows it
        }
        FoodProperties food = item.isEdible() ? item.getFoodProperties() : null;
        if (c.on("showFoodValues") && food != null) {
            // 1.20.1 stores a saturation modifier; the saturation a player gets is nutrition * modifier * 2
            lines.add(line(c, "foodValues", "food", food.getNutrition(), NUMBER.format(food.getNutrition() * food.getSaturationModifier() * 2.0F)));
        }
        float compostChance = ComposterBlock.COMPOSTABLES.getFloat(item);
        if (c.on("showCompostable") && compostChance > 0.0F) {
            lines.add(line(c, "compostable", "compostable", Math.round(compostChance * 100)));
        }
        Integer burnTime = c.on("showBurnTime") ? burnTime(stack) : null;
        if (burnTime != null && burnTime > 0) {
            lines.add(line(c, "burnTime", "burn_time", time(c, burnTime)));
        }
        // showUseCooldown: 1.20.1 has no cooldown data on items (the few cooldowns are constants inside the item classes), so there is nothing to read.
        if (c.on("showSongDuration") && item instanceof RecordItem record) {
            lines.add(line(c, "songDuration", "song_duration", seconds(record.getLengthInTicks() / 20.0)));
        }
        if (c.on("showEnchantability") && item.getEnchantmentValue() > 0) {
            lines.add(line(c, "enchantability", "enchantability", item.getEnchantmentValue()));
        }
        int repairCost = stack.getBaseRepairCost();
        if (c.on("showRepairCost") && repairCost > 0) {
            lines.add(line(c, "repairCost", "repair_cost", repairCost + 1)); // the anvil's cost for this item: prior work + 1
        }
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (c.on("showStrength") && block.defaultDestroyTime() > 0) { // instant-break blocks (crops, flowers, torches) show none
                lines.add(line(c, "strength", "strength", NUMBER.format(block.defaultDestroyTime()), NUMBER.format(block.getExplosionResistance())));
            }
            // 1.20.1 has no enchantment power tag: the bookshelf is the one block the enchanting table counts
            if (c.on("showEnchantmentPower") && block == Blocks.BOOKSHELF) {
                lines.add(line(c, "enchantmentPower", "enchantment_power", 1));
            }
        }
        if (item instanceof DiggerItem digger) {
            Tier tier = digger.getTier();
            if (c.on("showMiningLevel")) lines.add(line(c, "miningLevel", "mining_level", tier.getLevel()));
            if (c.on("showMiningSpeed")) lines.add(line(c, "miningSpeed", "mining_speed", NUMBER.format(tier.getSpeed())));
        }
        if (c.on("showModName")) {
            String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
            String name = FabricLoader.getInstance().getModContainer(namespace).map(mod -> mod.getMetadata().getName()).orElse(namespace);
            lines.add(Component.literal(name).withStyle(style -> style.withColor(c.color("modName")).withItalic(true)));
        }
        if (c.on("showComponents")) components(c, stack, lines);
    }

    private static MutableComponent line(TooltipsConfig c, String colorKey, String key, Object... args) {
        return Component.translatable(KEY + key, args).withStyle(style -> style.withColor(c.color(colorKey)));
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

    /** 1.20.1's counterpart of the item components: the stack's NBT tag, one line per top-level entry. */
    private static void components(TooltipsConfig c, ItemStack stack, List<Component> lines) {
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.isEmpty()) return;
        if (!Screen.hasControlDown()) {
            lines.add(Component.translatable(KEY + "components", Component.translatable(KEY + "hold_ctrl")
                .withStyle(style -> style.withColor(c.componentColor(1)))).withStyle(style -> style.withColor(c.componentColor(0))));
            return;
        }
        lines.add(Component.translatable(KEY + "components_title").withStyle(style -> style.withColor(c.componentColor(0))));
        for (String key : tag.getAllKeys()) {
            String value = String.valueOf(tag.get(key));
            if (value.length() > 60) value = value.substring(0, 57) + "...";
            lines.add(Component.literal(" " + key + ": ").withStyle(style -> style.withColor(c.componentColor(2)))
                .append(Component.literal(value).withStyle(style -> style.withColor(c.componentColor(3)))));
        }
    }
}
