package com.thelads.core.v26_2.mixin.ebe;

import com.thelads.core.v26_2.feature.ebe.BakedBlockEntities263;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A pot with sherds is left out of the mesh as soon as its data arrives, before its section is first built. */
@Mixin(DecoratedPotBlockEntity.class)
public abstract class EbeDecoratedPotBlockEntityMixin {
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void lads$ebeSherds(ValueInput input, CallbackInfo ci) {
        DecoratedPotBlockEntity pot = (DecoratedPotBlockEntity) (Object) this;
        if (pot.getLevel() == null || pot.getLevel().isClientSide()) BakedBlockEntities263.potLoaded(pot);
    }
}
