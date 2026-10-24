package com.thelads.core.v1_20_1.utility.mixin;

import com.thelads.core.client.BreedingTimer;
import com.thelads.core.v1_20_1.utility.Utility1201;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Breeding Timer: each animal's love and cooldown, and the hearts event (18) the server sends when it is fed and when it breeds. */
@Mixin(Animal.class)
abstract class AnimalMixin1201 implements BreedingTimer.Tracked {
    @Unique private BreedingTimer.State lads$breeding;

    @Override
    public BreedingTimer.State lads$breeding() {
        if (lads$breeding == null) lads$breeding = new BreedingTimer.State();
        return lads$breeding;
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void lads$hearts(byte id, CallbackInfo ci) {
        Animal animal = (Animal) (Object) this;
        if (id == 18 && animal.level().isClientSide) Utility1201.hearts(animal);
    }
}
