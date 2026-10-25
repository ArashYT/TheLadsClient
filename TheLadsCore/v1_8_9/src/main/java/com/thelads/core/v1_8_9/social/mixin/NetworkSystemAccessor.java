package com.thelads.core.v1_8_9.social.mixin;

import io.netty.channel.ChannelFuture;
import java.util.List;
import net.minecraft.network.NetworkSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NetworkSystem.class)
public interface NetworkSystemAccessor {
    @Accessor("endpoints")
    List<ChannelFuture> getEndpoints();
}
