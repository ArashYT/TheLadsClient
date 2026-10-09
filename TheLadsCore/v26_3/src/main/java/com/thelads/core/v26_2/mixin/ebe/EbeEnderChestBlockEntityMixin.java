package com.thelads.core.v26_2.mixin.ebe;

import com.thelads.core.v26_2.feature.ebe.BakedBlockEntities263;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EnderChestBlockEntity.class)
public abstract class EbeEnderChestBlockEntityMixin implements BakedBlockEntities263.LidHolder {
    @Shadow @Final private ChestLidController chestLidController;

    @Override
    public ChestLidController lads$lid() { return chestLidController; }
}
