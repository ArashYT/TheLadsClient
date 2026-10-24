package com.thelads.core.v1_20_1.hudextras.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Block Info's mining progress: how far your digging has got and on which block. */
@Mixin(MultiPlayerGameMode.class)
public interface GameModeAccessor1201 {
    @Accessor("destroyProgress")
    float lads$destroyProgress();

    @Accessor("destroyBlockPos")
    BlockPos lads$destroyPos();
}
