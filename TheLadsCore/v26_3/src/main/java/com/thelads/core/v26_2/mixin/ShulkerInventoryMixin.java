package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.ShulkerInventory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public class ShulkerInventoryMixin {
    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
        at = @At("TAIL"), require = 1)
    private void lads$inventory(LivingEntity owner, Level level, ItemStack item, int x, int y, int seed, CallbackInfo callback) {
        ShulkerInventory.decorate((GuiGraphicsExtractor) (Object) this, item, x, y);
    }
}
