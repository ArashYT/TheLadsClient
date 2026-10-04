package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA only (Probe170): the local player wears QaSkin, a custom skin 3D Skin Layers draws, and gets their own back afterwards. */
@Mixin(NetworkPlayerInfo.class)
public interface NetworkPlayerInfoAccessor {
    @Accessor("locationSkin") ResourceLocation ladsGetSkin();
    @Accessor("locationSkin") void ladsSetSkin(ResourceLocation skin);
    @Accessor("playerTexturesLoaded") boolean ladsGetTexturesLoaded();
    @Accessor("playerTexturesLoaded") void ladsSetTexturesLoaded(boolean loaded);
    @Accessor("skinType") String ladsGetSkinType();
    @Accessor("skinType") void ladsSetSkinType(String type);
}
