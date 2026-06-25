package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface PlayerAccessor {
    @Accessor("inventory")
    void setInventory(net.minecraft.world.item.ItemStack[] inventory);
}
