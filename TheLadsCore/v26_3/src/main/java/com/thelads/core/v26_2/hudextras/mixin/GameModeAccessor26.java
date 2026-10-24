package com.thelads.core.v26_2.hudextras.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Block Info's mining progress: the block your digging is on (its progress is accessible through the access widener). */
@Mixin(MultiPlayerGameMode.class)
public interface GameModeAccessor26 {
    @Accessor("destroyBlockPos")
    BlockPos lads$destroyPos();
}
