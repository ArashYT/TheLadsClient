package com.thelads.core.v1_20_1.mixin.social;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IntegratedServer.class)
public interface IntegratedServerAccessor1201 {
    @Accessor("publishedPort")
    int ladsGetPublishedPort();

    @Accessor("publishedPort")
    void ladsSetPublishedPort(int port);

    @Accessor("lanPinger")
    LanServerPinger ladsGetLanPinger();

    @Accessor("lanPinger")
    void ladsSetLanPinger(LanServerPinger pinger);
}
