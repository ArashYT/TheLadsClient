package com.thelads.smp.mixin;

import com.thelads.smp.compat.MapSearchBudget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The explorer-map biome preview samples 128x128 biomes and can generate chunks for them; it stops when the map's budget is spent. */
@Mixin(MapItem.class)
public abstract class MixinMapItemPreviewBudget {
    @Inject(method = "renderBiomePreviewMap", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private static void ladssmp$checkPreviewBudget(ServerLevel level, ItemStack map, CallbackInfo ci) {
        MapSearchBudget.check();
    }
}
