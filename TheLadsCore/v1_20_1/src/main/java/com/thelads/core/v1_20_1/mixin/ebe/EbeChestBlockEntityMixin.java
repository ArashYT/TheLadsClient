package com.thelads.core.v1_20_1.mixin.ebe;

import com.thelads.core.v1_20_1.feature.ebe.BakedBlockEntities1201;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChestBlockEntity.class)
public abstract class EbeChestBlockEntityMixin implements BakedBlockEntities1201.LidHolder {
    @Shadow @Final private ChestLidController chestLidController;

    @Override
    public ChestLidController lads$lid() { return chestLidController; }
}
