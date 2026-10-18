package com.thelads.smp.features.arise.nb4;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;

import java.util.List;

/**
 * Clean-room food properties and consumable component definitions for Native Batch NB4 culinary additions.
 */
public final class AriseNB4Foods {
    private AriseNB4Foods() {}

    private static FoodProperties prop(int nutrition, float saturation) {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
    }

    private static FoodProperties propAlways(int nutrition, float saturation) {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).alwaysEdible().build();
    }

    // --- Sandwiches ---
    public static final FoodProperties HAM_SANDWICH = prop(8, 0.8f);
    public static final FoodProperties MUTTON_SANDWICH = prop(8, 0.8f);
    public static final FoodProperties COOKED_COD_SANDWICH = prop(7, 0.7f);
    public static final FoodProperties COOKED_SALMON_SANDWICH = prop(8, 0.8f);
    public static final FoodProperties BACON_AND_EGG_SANDWICH = prop(9, 0.85f);
    public static final FoodProperties TOMATO_AND_LETTUCE_SANDWICH = prop(6, 0.6f);

    // --- Naturalist & Wildlife Dishes ---
    public static final FoodProperties VENISON_STEW = prop(11, 0.9f);
    public static final FoodProperties DUCK_NOODLE_SOUP = prop(10, 0.85f);
    public static final FoodProperties ROASTED_WATERFOWL = prop(8, 0.8f);
    public static final FoodProperties BRAISED_BUSHMEAT = prop(8, 0.8f);
    public static final FoodProperties CLAM_CHOWDER = prop(10, 0.85f);
    public static final FoodProperties CRAB_CAKES = prop(7, 0.75f);

    // --- Ocean's Culinary ---
    public static final FoodProperties CALAMARI_RINGS = prop(6, 0.65f);
    public static final FoodProperties GRILLED_TENTACLE = prop(6, 0.65f);
    public static final FoodProperties BRAISED_SEA_PICKLE = prop(5, 0.5f);
    public static final FoodProperties FUGU_ROLL = prop(7, 0.75f);
    public static Consumable fuguRollConsumable() {
        return Consumables.defaultFood()
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 600, 0)))
                .build();
    }
    public static final FoodProperties GUARDIAN_SOUP = prop(11, 0.9f);
    public static Consumable guardianSoupConsumable() {
        return Consumables.defaultFood()
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.HASTE, 900, 0)))
                .build();
    }

    // --- Nether's Culinary ---
    public static final FoodProperties HOGLIN_LOIN = prop(3, 0.3f);
    public static final FoodProperties HOGLIN_SIRLOIN = prop(9, 0.85f);
    public static final FoodProperties STRIDER_SLICE = prop(2, 0.2f);
    public static final FoodProperties GRILLED_STRIDER = prop(7, 0.75f);
    public static final FoodProperties NETHER_SKEWER = prop(8, 0.8f);
    public static Consumable netherSkewerConsumable() {
        return Consumables.defaultFood()
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0)))
                .build();
    }
    public static final FoodProperties STRIDER_MOSS_STEW = prop(11, 0.9f);

    // --- Cocktails & Beverages ---
    public static final FoodProperties CITRUS_MOJITO = propAlways(3, 0.5f);
    public static Consumable citrusMojitoConsumable() {
        return Consumables.defaultDrink()
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SPEED, 1200, 0)))
                .build();
    }

    public static final FoodProperties BERRY_MARGARITA = propAlways(3, 0.5f);
    public static Consumable berryMargaritaConsumable() {
        return Consumables.defaultDrink()
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 0)))
                .build();
    }

    public static final FoodProperties GLOW_BERRY_FIZZ = propAlways(3, 0.5f);
    public static Consumable glowBerryFizzConsumable() {
        return Consumables.defaultDrink()
                .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                        new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0),
                        new MobEffectInstance(MobEffects.GLOWING, 600, 0)
                )))
                .build();
    }

    public static final FoodProperties NETHER_BLAZE_COCKTAIL = propAlways(4, 0.6f);
    public static Consumable netherBlazeCocktailConsumable() {
        return Consumables.defaultDrink()
                .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                        new MobEffectInstance(MobEffects.STRENGTH, 1200, 0),
                        new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0)
                )))
                .build();
    }

    public static final FoodProperties GREEN_TEA = propAlways(2, 0.4f);
    public static Consumable greenTeaConsumable() {
        return Consumables.defaultDrink()
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 0)))
                .build();
    }

    public static final FoodProperties AZALEA_TEA = propAlways(2, 0.4f);
    public static Consumable azaleaTeaConsumable() {
        return Consumables.defaultDrink()
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 0)))
                .build();
    }

    public static final FoodProperties BERRY_MATCHA_LATTE = propAlways(4, 0.6f);
    public static Consumable berryMatchaLatteConsumable() {
        return Consumables.defaultDrink()
                .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                        new MobEffectInstance(MobEffects.ABSORPTION, 900, 0),
                        new MobEffectInstance(MobEffects.SPEED, 900, 0)
                )))
                .build();
    }

    // --- Cultural Delights ---
    public static final FoodProperties BEEF_BURRITO = prop(10, 0.9f);
    public static final FoodProperties CHICKEN_TACO = prop(8, 0.8f);
    public static final FoodProperties FISH_TACO = prop(8, 0.75f);
    public static final FoodProperties HEARTY_SALAD = prop(7, 0.75f);

    // --- Butchery & Creature Addons ---
    public static final FoodProperties ANIMAL_TALLOW = prop(1, 0.1f);
    public static final FoodProperties BISON_PATTY = prop(7, 0.75f);
    public static final FoodProperties BISON_BURGER = prop(11, 0.95f);
    public static final FoodProperties KANGAROO_PASTA = prop(10, 0.85f);
    public static Consumable kangarooPastaConsumable() {
        return Consumables.defaultFood()
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 1200, 1)))
                .build();
    }

    // --- Refreshing Beverages ---
    public static final FoodProperties MELON_JUICE = propAlways(3, 0.6f);
    public static Consumable melonJuiceConsumable() {
        return Consumables.defaultDrink().build();
    }
}
