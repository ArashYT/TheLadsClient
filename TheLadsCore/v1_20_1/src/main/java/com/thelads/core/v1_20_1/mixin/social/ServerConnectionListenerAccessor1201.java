package com.thelads.core.v1_20_1.mixin.social;

import io.netty.channel.ChannelFuture;
import java.util.List;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerConnectionListener.class)
public interface ServerConnectionListenerAccessor1201 {
    @Accessor("channels")
    List<ChannelFuture> ladsGetChannels();
}
