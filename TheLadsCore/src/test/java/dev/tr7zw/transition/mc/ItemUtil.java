package dev.tr7zw.transition.mc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class ItemUtil {
    public static Item getItem(Identifier id) {
        return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
    }
}
