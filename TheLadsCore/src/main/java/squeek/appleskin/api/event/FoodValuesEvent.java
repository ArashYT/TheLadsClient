/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.event.Event
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.food.FoodProperties
 *  net.minecraft.world.item.ItemStack
 */
package squeek.appleskin.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import squeek.appleskin.api.handler.EventHandler;

public class FoodValuesEvent {
    public FoodProperties defaultFoodComponent;
    public FoodProperties modifiedFoodComponent;
    public final ItemStack itemStack;
    public final Player player;
    public static Event<EventHandler<FoodValuesEvent>> EVENT = EventHandler.createArrayBacked();

    public FoodValuesEvent(Player player, ItemStack itemStack, FoodProperties defaultFoodValues, FoodProperties modifiedFoodComponent) {
        this.player = player;
        this.itemStack = itemStack;
        this.defaultFoodComponent = defaultFoodValues;
        this.modifiedFoodComponent = modifiedFoodComponent;
    }
}

