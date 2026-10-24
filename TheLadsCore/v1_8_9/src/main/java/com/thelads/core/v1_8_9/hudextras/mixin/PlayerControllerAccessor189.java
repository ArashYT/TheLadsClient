package com.thelads.core.v1_8_9.hudextras.mixin;

import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Block Info's mining progress: how far your digging has got, on which block, and whether you are digging. */
@Mixin(PlayerControllerMP.class)
public interface PlayerControllerAccessor189 {
    @Accessor("curBlockDamageMP")
    float lads$damage();

    @Accessor("currentBlock")
    BlockPos lads$block();

    @Accessor("isHittingBlock")
    boolean lads$hitting();
}
