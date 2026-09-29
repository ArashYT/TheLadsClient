package com.thelads.core.v26_2.mixin;

import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The code-of-conduct answer that ServerData.copyFrom leaves out; ServerListSharedMixin copies it when it takes another game's entry. */
@Mixin(ServerData.class)
public interface ServerDataAccessor {
    @Accessor("acceptedCodeOfConduct") int ladsAcceptedCodeOfConduct();
    @Accessor("acceptedCodeOfConduct") void ladsSetAcceptedCodeOfConduct(int value);
}
