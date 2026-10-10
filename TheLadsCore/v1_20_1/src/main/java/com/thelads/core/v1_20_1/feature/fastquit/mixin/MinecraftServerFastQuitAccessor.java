package com.thelads.core.v1_20_1.feature.fastquit.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Stops a server without IntegratedServer.halt, which blocks the calling (render) thread until the server thread takes a task. */
@Mixin(MinecraftServer.class)
public interface MinecraftServerFastQuitAccessor {
    @Accessor("running") void lads$setRunning(boolean running);
}
