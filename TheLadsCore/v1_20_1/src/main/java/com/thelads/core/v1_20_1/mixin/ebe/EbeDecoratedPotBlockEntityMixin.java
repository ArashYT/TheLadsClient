package com.thelads.core.v1_20_1.mixin.ebe;

import com.thelads.core.v1_20_1.feature.ebe.BakedBlockEntities1201;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A pot with sherds is left out of the mesh as soon as its data arrives, before its section is first built. */
@Mixin(DecoratedPotBlockEntity.class)
public abstract class EbeDecoratedPotBlockEntityMixin {
    @Inject(method = "load", at = @At("TAIL"))
    private void lads$ebeSherds(CompoundTag tag, CallbackInfo ci) {
        DecoratedPotBlockEntity pot = (DecoratedPotBlockEntity) (Object) this;
        if (pot.getLevel() == null || pot.getLevel().isClientSide()) BakedBlockEntities1201.potLoaded(pot);
    }
}
