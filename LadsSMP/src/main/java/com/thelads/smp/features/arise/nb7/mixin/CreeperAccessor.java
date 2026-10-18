package com.thelads.smp.features.arise.nb7.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface CreeperAccessor {
    @Accessor("maxSwell")
    int ladssmp$getMaxSwell();

    @Accessor("maxSwell")
    void ladssmp$setMaxSwell(int maxSwell);

    @Accessor("explosionRadius")
    int ladssmp$getExplosionRadius();

    @Accessor("explosionRadius")
    void ladssmp$setExplosionRadius(int explosionRadius);

    @Accessor("DATA_IS_POWERED")
    static EntityDataAccessor<Boolean> ladssmp$getDataIsPowered() {
        throw new AssertionError();
    }
}
