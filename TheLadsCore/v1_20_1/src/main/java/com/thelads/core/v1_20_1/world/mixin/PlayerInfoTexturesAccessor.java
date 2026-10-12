package com.thelads.core.v1_20_1.world.mixin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.util.Map;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA only (WorldQa1201): the texture map behind PlayerInfo.getCapeLocation, to give the offline QA player a test cape. */
@Mixin(PlayerInfo.class)
public interface PlayerInfoTexturesAccessor {
    @Accessor("textureLocations")
    Map<MinecraftProfileTexture.Type, ResourceLocation> lads$textures();
}
