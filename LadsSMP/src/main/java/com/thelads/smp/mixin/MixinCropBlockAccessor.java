package com.thelads.smp.mixin;

import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Consolidates 'rightclickharvest': Invoker for CropBlock.getAgeProperty() so custom CropBlock subclasses can be reset to age 0.
 */
@Mixin(CropBlock.class)
public interface MixinCropBlockAccessor {
    @Invoker("getAgeProperty")
    IntegerProperty ladssmp$invokeGetAgeProperty();
}
