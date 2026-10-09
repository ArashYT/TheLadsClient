package com.thelads.core.v26_2.mixin.ebe;

import com.thelads.core.v26_2.feature.ebe.BakedBlockEntities263;
import net.minecraft.world.level.block.entity.ChestLidController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChestLidController.class)
public abstract class EbeChestLidControllerMixin implements BakedBlockEntities263.LidTarget {
    @Shadow private boolean shouldBeOpen;
    @Shadow private float openness;
    @Shadow private float oOpenness;

    @Override
    public boolean lads$shouldBeOpen() { return shouldBeOpen; }

    @Override
    public void lads$force(float value) {
        shouldBeOpen = value > 0f;
        openness = value;
        oOpenness = value;
    }
}
