package com.thelads.smp.features.arise.nb1;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native clean-room implementation of Complete Turtle Armor.
 * Adds Turtle Chestplate, Turtle Leggings, and Turtle Boots to complete vanilla's Turtle Helmet.
 * Grants permanent Water Breathing and Dolphin's Grace when the full 4-piece set is worn underwater.
 */
public final class CompleteTurtleArmorFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-TurtleArmor");

    public static Item TURTLE_CHESTPLATE;
    public static Item TURTLE_LEGGINGS;
    public static Item TURTLE_BOOTS;

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", name), item);
    }

    public static void init() {
        if (TURTLE_CHESTPLATE == null) {
            TURTLE_CHESTPLATE = register("turtle_chestplate",
                    new Item(new Item.Properties().humanoidArmor(ArmorMaterials.TURTLE_SCUTE, ArmorType.CHESTPLATE)));
            TURTLE_LEGGINGS = register("turtle_leggings",
                    new Item(new Item.Properties().humanoidArmor(ArmorMaterials.TURTLE_SCUTE, ArmorType.LEGGINGS)));
            TURTLE_BOOTS = register("turtle_boots",
                    new Item(new Item.Properties().humanoidArmor(ArmorMaterials.TURTLE_SCUTE, ArmorType.BOOTS)));
        }

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!isEnabled()) return;
            if (server.getTickCount() % 20 != 0) return; // run once per second

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tickPlayerArmor(player);
            }
        });
        LOGGER.info("[Lads SMP] Complete Turtle Armor registered");
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().completeTurtleArmor.enabled;
    }

    public static void tickPlayerArmor(ServerPlayer player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);

        boolean hasHelmet = head.is(Items.TURTLE_HELMET);
        boolean hasChest = chest.is(TURTLE_CHESTPLATE);
        boolean hasLegs = legs.is(TURTLE_LEGGINGS);
        boolean hasBoots = feet.is(TURTLE_BOOTS);

        int count = (hasHelmet ? 1 : 0) + (hasChest ? 1 : 0) + (hasLegs ? 1 : 0) + (hasBoots ? 1 : 0);
        if (count == 0) return;

        boolean submerged = player.isUnderWater() || player.isInWater();

        if (count == 4 && submerged) {
            // Full set bonus: permanent Water Breathing + Dolphin's Grace while in water
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, false, false, true));
            if (AriseNB1Config.get().completeTurtleArmor.fullSetDolphinsGrace) {
                player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 200, 0, false, false, true));
            }
        } else if (submerged && AriseNB1Config.get().completeTurtleArmor.individualWaterBreathing) {
            // Partial set: grants water breathing scaled by pieces worn
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, count * 60, 0, false, false, true));
        }
    }
}
