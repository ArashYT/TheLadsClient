/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.world.effect.MobEffect
 *  net.minecraft.world.effect.MobEffectCategory
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.effect.MobEffects
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.food.FoodData
 *  net.minecraft.world.food.FoodProperties
 *  net.minecraft.world.food.FoodProperties$Builder
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.component.Consumable
 *  net.minecraft.world.item.component.Consumables
 *  net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect
 *  net.minecraft.world.item.consume_effects.ConsumeEffect
 *  org.jetbrains.annotations.Nullable
 */
package squeek.appleskin.helpers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import org.jetbrains.annotations.Nullable;
import squeek.appleskin.api.event.FoodValuesEvent;
import squeek.appleskin.api.handler.EventHandler;
import squeek.appleskin.helpers.ConsumableFood;
import squeek.appleskin.helpers.ExhaustionHelper;
import squeek.appleskin.network.ClientSyncHandler;

public class FoodHelper {
    public static FoodProperties EMPTY_FOOD_COMPONENT = new FoodProperties.Builder().build();
    public static Consumable DEFAULT_CONSUMABLE_COMPONENT = Consumables.DEFAULT_FOOD;
    public static float REGEN_EXHAUSTION_INCREMENT = 6.0f;
    public static float MAX_EXHAUSTION = 4.0f;

    public static boolean isFood(ItemStack itemStack) {
        return itemStack.has(DataComponents.FOOD) && itemStack.has(DataComponents.CONSUMABLE);
    }

    public static boolean canConsume(Player player, FoodProperties foodComponent) {
        return player.canEat(foodComponent.canAlwaysEat());
    }

    public static ConsumableFood getDefaultFoodValues(ItemStack itemStack) {
        return new ConsumableFood((FoodProperties)itemStack.getOrDefault(DataComponents.FOOD, (Object)EMPTY_FOOD_COMPONENT), (Consumable)itemStack.getOrDefault(DataComponents.CONSUMABLE, (Object)DEFAULT_CONSUMABLE_COMPONENT));
    }

    @Nullable
    public static QueriedFoodResult query(ItemStack itemStack, Player player) {
        if (!FoodHelper.isFood(itemStack)) {
            return null;
        }
        ConsumableFood defaultFood = FoodHelper.getDefaultFoodValues(itemStack);
        FoodValuesEvent foodValuesEvent = new FoodValuesEvent(player, itemStack, defaultFood.food(), defaultFood.food());
        ((EventHandler)FoodValuesEvent.EVENT.invoker()).interact(foodValuesEvent);
        return new QueriedFoodResult(foodValuesEvent.defaultFoodComponent, foodValuesEvent.modifiedFoodComponent, defaultFood.consumable(), itemStack);
    }

    public static boolean isRotten(Consumable consumableComponent) {
        for (ConsumeEffect effect : consumableComponent.onConsumeEffects()) {
            if (!(effect instanceof ApplyStatusEffectsConsumeEffect)) continue;
            for (MobEffectInstance statusEffect : ((ApplyStatusEffectsConsumeEffect)effect).effects()) {
                if (((MobEffect)statusEffect.getEffect().value()).getCategory() != MobEffectCategory.HARMFUL) continue;
                return true;
            }
        }
        return false;
    }

    public static float getEstimatedHealthIncrement(Player player, ConsumableFood consumableFood) {
        if (!player.isHurt()) {
            return 0.0f;
        }
        FoodData stats = player.getFoodData();
        int foodLevel = Math.min(stats.getFoodLevel() + consumableFood.food().nutrition(), 20);
        float healthIncrement = 0.0f;
        if ((float)foodLevel >= 18.0f && ClientSyncHandler.naturalRegeneration) {
            float saturationLevel = Math.min(stats.getSaturationLevel() + consumableFood.food().saturation(), (float)foodLevel);
            float exhaustionLevel = ExhaustionHelper.getExhaustion(player);
            healthIncrement = FoodHelper.getEstimatedHealthIncrement(foodLevel, saturationLevel, exhaustionLevel);
        }
        block0: for (ConsumeEffect effect : consumableFood.consumable().onConsumeEffects()) {
            if (!(effect instanceof ApplyStatusEffectsConsumeEffect)) continue;
            for (MobEffectInstance statusEffect : ((ApplyStatusEffectsConsumeEffect)effect).effects()) {
                if (statusEffect.getEffect() != MobEffects.REGENERATION) continue;
                int amplifier = statusEffect.getAmplifier();
                int duration = statusEffect.getDuration();
                healthIncrement += (float)Math.floor(duration / Math.max(50 >> amplifier, 1));
                continue block0;
            }
        }
        return healthIncrement;
    }

    public static float getEstimatedHealthIncrement(int foodLevel, float saturationLevel, float exhaustionLevel) {
        float health = 0.0f;
        if (!Float.isFinite(exhaustionLevel) || !Float.isFinite(saturationLevel)) {
            return 0.0f;
        }
        while (foodLevel >= 18) {
            while (exhaustionLevel > MAX_EXHAUSTION) {
                exhaustionLevel -= MAX_EXHAUSTION;
                if (saturationLevel > 0.0f) {
                    saturationLevel = Math.max(saturationLevel - 1.0f, 0.0f);
                    continue;
                }
                --foodLevel;
            }
            if (foodLevel >= 20 && Float.compare(saturationLevel, Float.MIN_NORMAL) > 0) {
                float limitedSaturationLevel = Math.min(saturationLevel, REGEN_EXHAUSTION_INCREMENT);
                float exhaustionUntilAboveMax = Math.nextUp(MAX_EXHAUSTION) - exhaustionLevel;
                int numIterationsUntilAboveMax = Math.max(1, (int)Math.ceil(exhaustionUntilAboveMax / limitedSaturationLevel));
                health += limitedSaturationLevel / REGEN_EXHAUSTION_INCREMENT * (float)numIterationsUntilAboveMax;
                exhaustionLevel += limitedSaturationLevel * (float)numIterationsUntilAboveMax;
                continue;
            }
            if (foodLevel < 18) continue;
            health += 1.0f;
            exhaustionLevel += REGEN_EXHAUSTION_INCREMENT;
        }
        return health;
    }

    public static class QueriedFoodResult {
        public FoodProperties defaultFoodComponent;
        public FoodProperties modifiedFoodComponent;
        public Consumable consumableComponent;
        public final ItemStack itemStack;

        public QueriedFoodResult(FoodProperties defaultFoodComponent, FoodProperties modifiedFoodComponent, Consumable consumableComponent, ItemStack itemStack) {
            this.defaultFoodComponent = defaultFoodComponent;
            this.modifiedFoodComponent = modifiedFoodComponent;
            this.consumableComponent = consumableComponent;
            this.itemStack = itemStack;
        }
    }
}

