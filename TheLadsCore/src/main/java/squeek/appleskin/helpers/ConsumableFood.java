/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.food.FoodProperties
 *  net.minecraft.world.item.component.Consumable
 */
package squeek.appleskin.helpers;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumable;

public record ConsumableFood(FoodProperties food, Consumable consumable) {
}

