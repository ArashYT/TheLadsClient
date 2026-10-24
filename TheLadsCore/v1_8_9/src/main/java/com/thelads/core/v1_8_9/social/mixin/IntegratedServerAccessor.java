package com.thelads.core.v1_8_9.social.mixin;

import net.minecraft.client.multiplayer.ThreadLanServerPing;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IntegratedServer.class)
public interface IntegratedServerAccessor {
    @Accessor("isPublic")
    void setIsPublic(boolean isPublic);

    @Accessor("lanServerPing")
    ThreadLanServerPing getLanServerPing();

    @Accessor("lanServerPing")
    void setLanServerPing(ThreadLanServerPing ping);
}
