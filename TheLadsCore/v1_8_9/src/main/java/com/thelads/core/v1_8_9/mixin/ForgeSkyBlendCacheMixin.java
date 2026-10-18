package com.thelads.core.v1_8_9.mixin;

import net.minecraft.util.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Forge 1.8.9 bug: getSkyBlendColour checks its cache against the camera block's X and Z but stores X and Y, so it never
 * hits and re-blends (2r+1)^2 biomes (r = biomeSkyBlendRange[render distance], 26 at RD 12: 2,809) for the fog colour and
 * again for the sky, every frame. That was half the render thread at 1,900 FPS. Storing Z, as the check reads, fixes it.
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class ForgeSkyBlendCacheMixin {
    @Redirect(method = "getSkyBlendColour", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/BlockPos;getY()I", remap = true), require = 1)
    private static int lads$cacheZ(BlockPos pos) {
        return pos.getZ();
    }
}
