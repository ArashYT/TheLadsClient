package com.thelads.core.v1_8_9.utility.mixin;

import com.thelads.core.client.BreedingTimer;
import com.thelads.core.v1_8_9.utility.Utility189;
import net.minecraft.entity.passive.EntityAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Breeding Timer: 1.8.9 servers send no event when animals breed, but they sync the sign of an animal's age (-1 baby, 0 adult, 1 breeding
 * cooldown), which the client reads back from getGrowingAge. A 0 that turns into 1 is a breed seen now.
 */
@Mixin(EntityAnimal.class)
abstract class AnimalMixin189 implements BreedingTimer.Tracked {
    @Unique private BreedingTimer.State lads$breeding;
    /** The age sign last tick; 2 until the first look, so an animal already cooling when it loads is not taken for a breed. */
    @Unique private int lads$age = 2;

    @Override
    public BreedingTimer.State lads$breeding() {
        if (lads$breeding == null) lads$breeding = new BreedingTimer.State();
        return lads$breeding;
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void lads$watchAge(CallbackInfo ci) {
        EntityAnimal animal = (EntityAnimal) (Object) this;
        if (!animal.worldObj.isRemote) return;
        int age = animal.getGrowingAge();
        if (lads$age == 0 && age > 0) Utility189.bred(animal);
        lads$age = age;
    }
}
