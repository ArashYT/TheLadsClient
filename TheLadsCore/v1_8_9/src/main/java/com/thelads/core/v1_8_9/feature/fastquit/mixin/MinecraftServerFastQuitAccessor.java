package com.thelads.core.v1_8_9.feature.fastquit.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The server thread: MinecraftServer.getServerThread is server-side only (Forge strips it from the client). */
@Mixin(MinecraftServer.class)
public interface MinecraftServerFastQuitAccessor {
    @Accessor("serverThread") Thread lads$serverThread();
}
