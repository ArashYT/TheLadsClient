package com.thelads.core.v1_20_1.hud.items.mixin;

import com.thelads.core.v1_20_1.hud.items.ShulkerInventory1201;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every GUI item model (inventories, hotbar, tooltips) ends in this renderItem: shulker box badges and free-slot bars go on top. */
@Mixin(GuiGraphics.class)
abstract class ShulkerInventoryMixin {
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
        at = @At("TAIL"))
    private void lads$inventory(LivingEntity owner, Level level, ItemStack item, int x, int y, int seed, int guiOffset, CallbackInfo callback) {
        ShulkerInventory1201.decorate((GuiGraphics) (Object) this, item, x, y);
    }
}
