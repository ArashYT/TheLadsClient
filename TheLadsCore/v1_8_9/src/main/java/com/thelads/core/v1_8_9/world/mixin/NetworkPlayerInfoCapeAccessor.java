package com.thelads.core.v1_8_9.world.mixin;

import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA only (Probe191World): the offline QA player wears a test cape through its real player info. */
@Mixin(NetworkPlayerInfo.class)
public interface NetworkPlayerInfoCapeAccessor {
    @Accessor("locationCape") ResourceLocation ladsGetCape();
    @Accessor("locationCape") void ladsSetCape(ResourceLocation cape);
}
